package org.plum.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class PlumVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            startVpn()
        } else if (intent?.action == "STOP") {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        // Настраиваем VPN-интерфейс (перехватываем весь трафик)
        val builder = Builder()
        builder.setSession("Plum VPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0) // Перехват всех IPv4 адресов
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")

        vpnInterface = builder.establish()
        isRunning = true

        // Запускаем обработчик пакетов в фоне
        Thread {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)
            val buffer = ByteArray(32767)

            // Здесь происходит перехват и фрагментация
            while (isRunning) {
                val length = input.read(buffer)
                if (length > 0) {
                    // В настоящем VPN здесь должен быть движок tun2socks (SOCKS5)
                    // Мы применяем фрагментацию к первым байтам TLS (0x16 0x03)
                    if (length > 5 && buffer[0] == 0x16.toByte() && buffer[1] == 0x03.toByte()) {
                        // Логика фрагментации (разрезаем пакет)
                        output.write(buffer, 0, 1) 
                        Thread.sleep(10)
                        output.write(buffer, 1, length - 1)
                    } else {
                        output.write(buffer, 0, length)
                    }
                }
            }
        }.start()
    }

    private fun stopVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}