import socket
import struct
import threading
import time
from collections import deque

from kivy.app import App
from kivy.clock import Clock
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.textinput import TextInput
from kivy.uix.spinner import Spinner
from kivy.core.window import Window
from kivy.utils import get_color_from_hex
from kivy.metrics import dp

# Цветовая схема
ORANGE_BG = get_color_from_hex('#FF7A00')
ORANGE_LIGHT = get_color_from_hex('#FFE0BF')
ORANGE_BUTTON = get_color_from_hex('#FFB35C')
TEXT_COLOR = get_color_from_hex('#000000') # Черный текст

Window.clearcolor = ORANGE_BG

# Локализация
TRANSLATIONS = {
    'ru': {
        'title': 'ProPass', 'subtitle': 'Локальный SOCKS5 с фрагментацией TLS',
        'lang': 'Язык', 'host': 'Хост', 'port': 'Порт', 'strategy': 'Стратегия', 'dns': 'DNS',
        'start': 'Старт', 'stop': 'Стоп', 'log': 'Лог:',
        'stopped': 'Остановлено', 'listening': 'Слушаю {}:{}', 'err_port': 'Ошибка: порт должен быть числом',
        'strat_none': 'Без обхода', 'strat_split': 'Фрагментация', 'strat_fake': 'Фейк-пакет'
    },
    'en': {
        'title': 'ProPass', 'subtitle': 'Local SOCKS5 with TLS fragmentation',
        'lang': 'Language', 'host': 'Host', 'port': 'Port', 'strategy': 'Strategy', 'dns': 'DNS',
        'start': 'Start', 'stop': 'Stop', 'log': 'Log:',
        'stopped': 'Stopped', 'listening': 'Listening {}:{}', 'err_port': 'Error: port must be a number',
        'strat_none': 'None', 'strat_split': 'Split', 'strat_fake': 'Fake packet'
    },
    'de': {
        'title': 'ProPass', 'subtitle': 'Lokaler SOCKS5 mit TLS-Fragmentierung',
        'lang': 'Sprache', 'host': 'Host', 'port': 'Port', 'strategy': 'Strategie', 'dns': 'DNS',
        'start': 'Start', 'stop': 'Stopp', 'log': 'Protokoll:',
        'stopped': 'Gestoppt', 'listening': 'Höre zu {}:{}', 'err_port': 'Fehler: Port muss eine Zahl sein',
        'strat_none': 'Keine', 'strat_split': 'Split', 'strat_fake': 'Fake-Paket'
    },
    'fr': {
        'title': 'ProPass', 'subtitle': 'SOCKS5 local avec fragmentation TLS',
        'lang': 'Langue', 'host': 'Hôte', 'port': 'Port', 'strategy': 'Stratégie', 'dns': 'DNS',
        'start': 'Démarrer', 'stop': 'Arrêter', 'log': 'Journal:',
        'stopped': 'Arrêté', 'listening': 'Écoute {}:{}', 'err_port': 'Erreur : le port doit être un nombre',
        'strat_none': 'Aucune', 'strat_split': 'Fractionnement', 'strat_fake': 'Faux paquet'
    },
    'it': {
        'title': 'ProPass', 'subtitle': 'SOCKS5 locale con frammentazione TLS',
        'lang': 'Lingua', 'host': 'Host', 'port': 'Porta', 'strategy': 'Strategia', 'dns': 'DNS',
        'start': 'Avvia', 'stop': 'Ferma', 'log': 'Registro:',
        'stopped': 'Fermato', 'listening': 'In ascolto {}:{}', 'err_port': 'Errore: la porta deve essere un numero',
        'strat_none': 'Nessuna', 'strat_split': 'Divisione', 'strat_fake': 'Pacchetto finto'
    }
}

def is_ip(host):
    try:
        socket.inet_aton(host)
        return True
    except OSError:
        return False

def resolve_dns(domain, dns_server):
    """Простой UDP DNS-клиент для обхода системного DNS"""
    try:
        header = b'\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00'
        qname = b''.join(struct.pack('B', len(part)) + part.encode('ascii') for part in domain.split('.')) + b'\x00'
        qtype = b'\x00\x01' # A record
        qclass = b'\x00\x01'
        packet = header + qname + qtype + qclass
        
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(2.0)
        sock.sendto(packet, (dns_server, 53))
        data, _ = sock.recvfrom(512)
        
        idx = 12
        while data[idx] != 0:
            idx += 1
        idx += 5
        
        ancount = struct.unpack('!H', data[6:8])[0]
        for _ in range(ancount):
            if (data[idx] & 0xC0) == 0xC0:
                idx += 2
            else:
                while data[idx] != 0:
                    idx += 1
                idx += 1
            atype = struct.unpack('!H', data[idx:idx+2])[0]
            idx += 8
            rdlength = struct.unpack('!H', data[idx:idx+2])[0]
            idx += 2
            if atype == 1 and rdlength == 4:
                return socket.inet_ntoa(data[idx:idx+4])
            idx += rdlength
    except Exception:
        pass
    return None


class Socks5Server:
    def __init__(self, host, port, strategy, dns_server, log_callback):
        self.host = host
        self.port = port
        self.strategy = strategy
        self.dns_server = dns_server
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
            threading.Thread(target=self._handle_client, args=(client, addr), daemon=True).start()

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
            if len(raw) != 4: return None
            host = socket.inet_ntoa(raw)
        elif atyp == 3:
            size_raw = self._recv_exact(client, 1)
            if len(size_raw) != 1: return None
            raw = self._recv_exact(client, size_raw[0])
            if len(raw) != size_raw[0]: return None
            host = raw.decode("ascii", "ignore")
        elif atyp == 4:
            raw = self._recv_exact(client, 16)
            if len(raw) != 16: return None
            host = socket.inet_ntop(socket.AF_INET6, raw)
        else:
            self._send_reply(client, 8)
            return None

        port_raw = self._recv_exact(client, 2)
        if len(port_raw) != 2: return None
        port = struct.unpack("!H", port_raw)[0]
        return host, port

    def _relay_client_to_upstream(self, client, upstream):
        try:
            first = True
            while True:
                data = client.recv(65536)
                if not data: break
                if first:
                    first = False
                    if self.strategy == 'split':
                        self._send_split(upstream, data)
                    elif self.strategy == 'fake':
                        self._send_fake(upstream, data)
                    else:
                        upstream.sendall(data)
                else:
                    upstream.sendall(data)
        except OSError:
            pass
        finally:
            try: upstream.shutdown(socket.SHUT_WR)
            except OSError: pass

    def _send_split(self, upstream, data):
        if len(data) < 5 or data[0] != 0x16:
            upstream.sendall(data)
            return
        header = data[:5]
        payload = data[5:]
        part1 = payload[:1]
        part2 = payload[1:]
        
        upstream.sendall(header[:3] + struct.pack("!H", len(part1)) + part1)
        time.sleep(0.01)
        if part2:
            upstream.sendall(header[:3] + struct.pack("!H", len(part2)) + part2)

    def _send_fake(self, upstream, data):
        # Отправляем фейковый пакет, чтобы сбить с толку DPI
        fake = b'\x16\x03\x01\x00\x05\x01\x00\x00\x01\x00'
        upstream.sendall(fake)
        time.sleep(0.01)
        upstream.sendall(data)

    def _relay(self, source, target):
        try:
            while True:
                data = source.recv(65536)
                if not data: break
                target.sendall(data)
        except OSError:
            pass
        finally:
            try: target.shutdown(socket.SHUT_WR)
            except OSError: pass

    def _handle_client(self, client, addr):
        upstream = None
        try:
            client.settimeout(10)
            if not self._socks_handshake(client): return
            target = self._socks_request(client)
            if target is None: return
            host, port = target

            # Кастомный DNS
            if not is_ip(host) and self.dns_server:
                self.log(f"DNS {host} -> {self.dns_server}")
                ip = resolve_dns(host, self.dns_server)
                if ip:
                    host = ip
                else:
                    self.log(f"DNS fail for {host}")
                    self._send_reply(client, 4)
                    return

            try:
                upstream = socket.create_connection((host, port), timeout=10)
            except Exception as exc:
                self.log(f"connect fail {host}:{port}")
                self._send_reply(client, 5)
                return

            self._send_reply(client, 0)
            self.log(f"connect {host}:{port}")
            client.settimeout(None)
            upstream.settimeout(None)
            done = threading.Event()

            def close_all():
                for sock in (client, upstream):
                    try: sock.close()
                    except OSError: pass

            def run(source, dest, first):
                try:
                    if first: self._relay_client_to_upstream(source, dest)
                    else: self._relay(source, dest)
                finally:
                    done.set()
                    close_all()

            threading.Thread(target=run, args=(client, upstream, True), daemon=True).start()
            threading.Thread(target=run, args=(upstream, client, False), daemon=True).start()
            done.wait()
        except Exception as exc:
            self.log(f"client error: {exc}")
        finally:
            try: client.close()
            except OSError: pass
            if upstream is not None:
                try: upstream.close()
                except OSError: pass


class ProPassApp(App):
    def build(self):
        self.title = 'ProPass'
        self.server = None
        self.log_buffer = deque(maxlen=100)
        self.lang = 'ru'
        self.current_strategy = 'split'

        root = BoxLayout(orientation='vertical', padding=dp(20), spacing=dp(12))

        # Заголовки
        self.title_label = Label(text='[b]ProPass[/b]', markup=True, font_size='32sp', color=TEXT_COLOR, size_hint_y=None, height=dp(50))
        self.subtitle_label = Label(text='', font_size='14sp', color=TEXT_COLOR, size_hint_y=None, height=dp(30))
        root.add_widget(self.title_label)
        root.add_widget(self.subtitle_label)

        # Сетка настроек
        grid = GridLayout(cols=2, spacing=dp(10), size_hint_y=None, height=dp(220))
        
        self.lang_label = Label(text='', color=TEXT_COLOR, bold=True, halign='left')
        self.lang_label.bind(size=lambda i, v: setattr(i, 'text_size', (v[0], None)))
        self.lang_spinner = Spinner(text='Русский', values=('Русский', 'English', 'Deutsch', 'Français', 'Italiano'), background_color=ORANGE_LIGHT, color=TEXT_COLOR)
        self.lang_spinner.bind(text=self.change_lang)
        grid.add_widget(self.lang_label)
        grid.add_widget(self.lang_spinner)

        self.host_label = Label(text='', color=TEXT_COLOR, bold=True, halign='left')
        self.host_input = TextInput(text='127.0.0.1', multiline=False, background_color=ORANGE_LIGHT, foreground_color=TEXT_COLOR, font_size='15sp')
        grid.add_widget(self.host_label)
        grid.add_widget(self.host_input)

        self.port_label = Label(text='', color=TEXT_COLOR, bold=True, halign='left')
        self.port_input = TextInput(text='1080', multiline=False, input_filter='int', background_color=ORANGE_LIGHT, foreground_color=TEXT_COLOR, font_size='15sp')
        grid.add_widget(self.port_label)
        grid.add_widget(self.port_input)

        self.strategy_label = Label(text='', color=TEXT_COLOR, bold=True, halign='left')
        self.strategy_spinner = Spinner(text='', values=('',), background_color=ORANGE_LIGHT, color=TEXT_COLOR)
        self.strategy_spinner.bind(text=self.change_strategy)
        grid.add_widget(self.strategy_label)
        grid.add_widget(self.strategy_spinner)

        self.dns_label = Label(text='', color=TEXT_COLOR, bold=True, halign='left')
        self.dns_spinner = Spinner(text='8.8.8.8', values=('8.8.8.8', '9.9.9.9', '1.1.1.1', '8.8.4.4'), background_color=ORANGE_LIGHT, color=TEXT_COLOR)
        grid.add_widget(self.dns_label)
        grid.add_widget(self.dns_spinner)

        root.add_widget(grid)

        # Кнопки
        button_layout = BoxLayout(orientation='horizontal', size_hint_y=None, height=dp(60), spacing=dp(15))
        self.start_btn = Button(text='', background_color=ORANGE_BUTTON, color=TEXT_COLOR, font_size='18sp', bold=True)
        self.start_btn.bind(on_press=self.start_server)
        self.stop_btn = Button(text='', background_color=ORANGE_BUTTON, color=TEXT_COLOR, font_size='18sp', bold=True, disabled=True)
        self.stop_btn.bind(on_press=self.stop_server)
        button_layout.add_widget(self.start_btn)
        button_layout.add_widget(self.stop_btn)
        root.add_widget(button_layout)

        # Лог
        self.log_label_title = Label(text='', font_size='16sp', color=TEXT_COLOR, bold=True, size_hint_y=None, height=dp(30), halign='left')
        root.add_widget(self.log_label_title)

        scroll = ScrollView(size_hint=(1, 1))
        self.log_label = Label(text='', font_size='13sp', color=TEXT_COLOR, halign='left', valign='top', size_hint_y=None, markup=True)
        self.log_label.bind(texture_size=lambda i, v: setattr(i, 'height', v[1]))
        scroll.add_widget(self.log_label)
        root.add_widget(scroll)

        self.change_lang(None, 'Русский')
        Clock.schedule_interval(self.update_log_display, 0.5)
        return root

    def change_lang(self, spinner, text):
        lang_map = {'Русский': 'ru', 'English': 'en', 'Deutsch': 'de', 'Français': 'fr', 'Italiano': 'it'}
        self.lang = lang_map.get(text, 'ru')
        t = TRANSLATIONS[self.lang]
        
        self.title_label.text = f"[b]{t['title']}[/b]"
        self.subtitle_label.text = t['subtitle']
        self.lang_label.text = t['lang'] + ":"
        self.host_label.text = t['host'] + ":"
        self.port_label.text = t['port'] + ":"
        self.strategy_label.text = t['strategy'] + ":"
        self.dns_label.text = t['dns'] + ":"
        self.start_btn.text = t['start']
        self.stop_btn.text = t['stop']
        self.log_label_title.text = t['log']
        
        # Обновляем стратегии
        curr = self.current_strategy
        self.strategy_spinner.values = (t['strat_none'], t['strat_split'], t['strat_fake'])
        if curr == 'none': self.strategy_spinner.text = t['strat_none']
        elif curr == 'split': self.strategy_spinner.text = t['strat_split']
        elif curr == 'fake': self.strategy_spinner.text = t['strat_fake']

    def change_strategy(self, spinner, text):
        t = TRANSLATIONS[self.lang]
        if text == t['strat_none']: self.current_strategy = 'none'
        elif text == t['strat_split']: self.current_strategy = 'split'
        elif text == t['strat_fake']: self.current_strategy = 'fake'

    def log(self, message):
        self.log_buffer.append(f"[color=000000]{time.strftime('%H:%M:%S')}[/color] {message}")

    def update_log_display(self, dt):
        if not self.log_buffer: return
        text = "\n".join(self.log_buffer)
        if self.log_label.text != text:
            self.log_label.text = text

    def start_server(self, instance):
        if self.server is not None: return
        host = self.host_input.text.strip() or '127.0.0.1'
        try:
            port = int(self.port_input.text.strip())
        except ValueError:
            t = TRANSLATIONS[self.lang]
            self.log(t['err_port'])
            return
        
        server = Socks5Server(
            host=host, 
            port=port, 
            strategy=self.current_strategy, 
            dns_server=self.dns_spinner.text,
            log_callback=self.log
        )
        
        try:
            server.start()
        except Exception as exc:
            t = TRANSLATIONS[self.lang]
            self.log(t['err_start'].format(exc))
            return
            
        self.server = server
        self.start_btn.disabled = True
        self.stop_btn.disabled = False
        t = TRANSLATIONS[self.lang]
        self.log(t['listening'].format(host, port))

    def stop_server(self, instance):
        if self.server is None: return
        self.server.stop()
        self.server = None
        self.start_btn.disabled = False
        self.stop_btn.disabled = True
        t = TRANSLATIONS[self.lang]
        self.log(t['stopped'])

if __name__ == '__main__':
    ProPassApp().run()