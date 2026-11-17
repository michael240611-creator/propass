package org.plum.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class PlumVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var currentDns = "8.8.8.8"
    private var currentStrategy = "split"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            currentDns = intent.getStringExtra("DNS") ?: "8.8.8.8"
            currentStrategy = intent.getStringExtra("STRATEGY") ?: "split"
            startVpn()
        } else if (intent?.action == "STOP") {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        val builder = Builder()
        builder.setSession("Plum VPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(currentDns) // Используем выбранный DNS
            .addDnsServer("1.1.1.1") // Резервный DNS

        vpnInterface = builder.establish()
        isRunning = true

        Thread {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)
            val buffer = ByteArray(32767)

            while (isRunning) {
                val length = input.read(buffer)
                if (length > 0) {
                    // Если выбран "none", просто пропускаем
                    if (currentStrategy == "none") {
                        output.write(buffer, 0, length)
                        continue
                    }

                    // Логика для TLS (0x16 0x03)
                    if (length > 5 && buffer[0] == 0x16.toByte() && buffer[1] == 0x03.toByte()) {
                        if (currentStrategy == "split") {
                            // Фрагментация: режем пакет пополам
                            output.write(buffer, 0, 1)
                            Thread.sleep(5)
                            output.write(buffer, 1, length - 1)
                        } else if (currentStrategy == "fake") {
                            // Фейк-пакет: шлем мусор, потом оригинал
                            val fake = byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x05, 0x01, 0x00, 0x00, 0x01, 0x00)
                            output.write(fake)
                            Thread.sleep(5)
                            output.write(buffer, 0, length)
                        }
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