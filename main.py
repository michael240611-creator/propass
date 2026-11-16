import socket
import struct
import threading
import time
from collections import deque

from kivy.app import App
from kivy.clock import Clock
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.textinput import TextInput
from kivy.uix.checkbox import CheckBox
from kivy.core.window import Window
from kivy.utils import get_color_from_hex

ORANGE_BG = get_color_from_hex('#FF7A00')
ORANGE_LIGHT = get_color_from_hex('#FFE0BF')
ORANGE_BUTTON = get_color_from_hex('#FFB35C')
TEXT_DARK = get_color_from_hex('#1A1A1A')

Window.clearcolor = ORANGE_BG


class Socks5Server:
    def __init__(self, host, port, split_tls, log_callback):
        self.host = host
        self.port = port
        self.split_tls = split_tls
        self.log = log_callback
        self._stop = threading.Event()
        self._sock = None
        self._thread = None

    def start(self):
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._sock.bind((self.host, self.port))
        self._sock.listen(50)
        self._sock.settimeout(0.5)
        self._stop.clear()
        self._thread = threading.Thread(target=self._accept_loop, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._sock is not None:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def _accept_loop(self):
        while not self._stop.is_set():
            try:
                client, addr = self._sock.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            threading.Thread(
                target=self._handle_client,
                args=(client, addr),
                daemon=True
            ).start()

    def _recv_exact(self, sock, size):
        data = b""
        while len(data) < size:
            chunk = sock.recv(size - len(data))
            if not chunk:
                return b""
            data += chunk
        return data

    def _send_reply(self, sock, reply_code):
        sock.sendall(bytes([5, reply_code, 0, 1, 0, 0, 0, 0, 0, 0]))

    def _socks_handshake(self, client):
        header = self._recv_exact(client, 2)
        if len(header) != 2 or header[0] != 5:
            return False
        methods = self._recv_exact(client, header[1])
        if not methods:
            return False
        client.sendall(b"\x05\x00")
        return True

    def _socks_request(self, client):
        header = self._recv_exact(client, 4)
        if len(header) != 4 or header[0] != 5:
            self._send_reply(client, 7)
            return None
        if header[1] != 1:
            self._send_reply(client, 7)
            return None

        atyp = header[3]
        if atyp == 1:
            raw = self._recv_exact(client, 4)
            if len(raw) != 4:
                return None
            host = socket.inet_ntoa(raw)
        elif atyp == 3:
            size_raw = self._recv_exact(client, 1)
            if len(size_raw) != 1:
                return None
            raw = self._recv_exact(client, size_raw[0])
            if len(raw) != size_raw[0]:
                return None
            host = raw.decode("ascii", "ignore")
        elif atyp == 4:
            raw = self._recv_exact(client, 16)
            if len(raw) != 16:
                return None
            host = socket.inet_ntop(socket.AF_INET6, raw)
        else:
            self._send_reply(client, 8)
            return None

        port_raw = self._recv_exact(client, 2)
        if len(port_raw) != 2:
            return None
        port = struct.unpack("!H", port_raw)[0]
        return host, port

    def _fragment_tls_client_hello(self, client, first_data):
        if len(first_data) < 5:
            return first_data
        if first_data[0] != 0x16 or first_data[1] != 0x03:
            return first_data

        record_len = struct.unpack("!H", first_data[3:5])[0]
        needed = 5 + record_len
        buffer = first_data

        while len(buffer) < needed:
            chunk = client.recv(needed - len(buffer))
            if not chunk:
                break
            buffer += chunk

        if len(buffer) < needed or record_len < 2:
            return buffer

        header = buffer[:5]
        payload = buffer[5:needed]
        rest = buffer[needed:]

        cut = 1
        part1 = payload[:cut]
        part2 = payload[cut:]

        output = header[:3] + struct.pack("!H", len(part1)) + part1
        if part2:
            output += header[:3] + struct.pack("!H", len(part2)) + part2
        output += rest
        return output

    def _relay_client_to_upstream(self, client, upstream):
        try:
            first = True
            while True:
                data = client.recv(65536)
                if not data:
                    break
                if first and self.split_tls:
                    first = False
                    data = self._fragment_tls_client_hello(client, data)
                upstream.sendall(data)
        except OSError:
            pass
        finally:
            try:
                upstream.shutdown(socket.SHUT_WR)
            except OSError:
                pass

    def _relay(self, source, target):
        try:
            while True:
                data = source.recv(65536)
                if not data:
                    break
                target.sendall(data)
        except OSError:
            pass
        finally:
            try:
                target.shutdown(socket.SHUT_WR)
            except OSError:
                pass

    def _handle_client(self, client, addr):
        upstream = None
        try:
            client.settimeout(10)
            if not self._socks_handshake(client):
                return
            target = self._socks_request(client)
            if target is None:
                return
            host, port = target
            try:
                upstream = socket.create_connection((host, port), timeout=10)
            except Exception as exc:
                self.log("connect fail {}:{} {}".format(host, port, exc))
                self._send_reply(client, 5)
                return
            self._send_reply(client, 0)
            self.log("connect {}:{}".format(host, port))
            client.settimeout(None)
            upstream.settimeout(None)
            done = threading.Event()

            def close_all():
                for sock in (client, upstream):
                    try:
                        sock.close()
                    except OSError:
                        pass

            def run(source, dest, first):
                try:
                    if first:
                        self._relay_client_to_upstream(source, dest)
                    else:
                        self._relay(source, dest)
                finally:
                    done.set()
                    close_all()

            threading.Thread(target=run, args=(client, upstream, True), daemon=True).start()
            threading.Thread(target=run, args=(upstream, client, False), daemon=True).start()
            done.wait()
        except Exception as exc:
            self.log("client error: {}".format(exc))
        finally:
            try:
                client.close()
            except OSError:
                pass
            if upstream is not None:
                try:
                    upstream.close()
                except OSError:
                    pass


class ProPassApp(App):
    def build(self):
        self.title = 'ProPass'
        self.server = None
        self.log_buffer = deque(maxlen=100)

        root = BoxLayout(orientation='vertical', padding=20, spacing=12)

        root.add_widget(Label(
            text='[b]ProPass[/b]', markup=True, font_size='32sp',
            color=TEXT_DARK, size_hint_y=None, height=60
        ))
        root.add_widget(Label(
            text='Локальный SOCKS5 с фрагментацией TLS',
            font_size='14sp', color=TEXT_DARK, size_hint_y=None, height=30
        ))

        input_layout = BoxLayout(orientation='horizontal', size_hint_y=None, height=50, spacing=10)
        self.host_input = TextInput(
            text='127.0.0.1', multiline=False,
            background_color=ORANGE_LIGHT, foreground_color=TEXT_DARK,
            font_size='16sp', size_hint_x=0.6
        )
        self.port_input = TextInput(
            text='1080', multiline=False, input_filter='int',
            background_color=ORANGE_LIGHT, foreground_color=TEXT_DARK,
            font_size='16sp', size_hint_x=0.4
        )
        input_layout.add_widget(self.host_input)
        input_layout.add_widget(self.port_input)
        root.add_widget(input_layout)

        check_layout = BoxLayout(orientation='horizontal', size_hint_y=None, height=40, spacing=10)
        self.split_check = CheckBox(active=True, size_hint_x=None, width=40)
        check_label = Label(
            text='Фрагментировать TLS ClientHello',
            font_size='16sp', color=TEXT_DARK, halign='left'
        )
        check_label.bind(size=lambda i, v: setattr(i, 'text_size', (v[0], None)))
        check_layout.add_widget(self.split_check)
        check_layout.add_widget(check_label)
        root.add_widget(check_layout)

        button_layout = BoxLayout(orientation='horizontal', size_hint_y=None, height=60, spacing=15)
        self.start_btn = Button(
            text='Старт', background_color=ORANGE_BUTTON,
            color=TEXT_DARK, font_size='18sp', bold=True
        )
        self.start_btn.bind(on_press=self.start_server)
        self.stop_btn = Button(
            text='Стоп', background_color=ORANGE_BUTTON,
            color=TEXT_DARK, font_size='18sp', bold=True, disabled=True
        )
        self.stop_btn.bind(on_press=self.stop_server)
        button_layout.add_widget(self.start_btn)
        button_layout.add_widget(self.stop_btn)
        root.add_widget(button_layout)

        root.add_widget(Label(
            text='Лог:', font_size='16sp', color=TEXT_DARK,
            size_hint_y=None, height=30, halign='left'
        ))

        scroll = ScrollView(size_hint=(1, 1))
        self.log_label = Label(
            text='', font_size='12sp', color=TEXT_DARK,
            halign='left', valign='top', size_hint_y=None, markup=True
        )
        self.log_label.bind(
            texture_size=lambda i, v: setattr(i, 'height', v[1])
        )
        scroll.add_widget(self.log_label)
        root.add_widget(scroll)

        Clock.schedule_interval(self.update_log_display, 0.5)
        return root

    def log(self, message):
        timestamp = time.strftime("%H:%M:%S")
        self.log_buffer.append("{} {}".format(timestamp, message))

    def update_log_display(self, dt):
        if not self.log_buffer:
            return
        text = "\n".join(self.log_buffer)
        if self.log_label.text != text:
            self.log_label.text = text

    def start_server(self, instance):
        if self.server is not None:
            return
        host = self.host_input.text.strip() or '127.0.0.1'
        try:
            port = int(self.port_input.text.strip())
        except ValueError:
            self.log('Ошибка: порт должен быть числом')
            return
        server = Socks5Server(
            host=host, port=port,
            split_tls=self.split_check.active,
            log_callback=self.log
        )
        try:
            server.start()
        except Exception as exc:
            self.log('Ошибка запуска: {}'.format(exc))
            return
        self.server = server
        self.start_btn.disabled = True
        self.stop_btn.disabled = False
        self.log('Слушаю {}:{}'.format(host, port))

    def stop_server(self, instance):
        if self.server is None:
            return
        self.server.stop()
        self.server = None
        self.start_btn.disabled = False
        self.stop_btn.disabled = True
        self.log('Остановлено')


if __name__ == '__main__':
    ProPassApp().run()