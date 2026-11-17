// file: app/src/main/java/com/plum/vpn/PlumVpnService.kt
package com.plum.vpn

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class PlumVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var currentStrategy: Strategy? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val strategyId = intent.getStringExtra(EXTRA_STRATEGY) ?: ""
                startVpn(strategyId)
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(strategyId: String) {
        if (isRunning.get()) return
        
        currentStrategy = StrategyRegistry.get(strategyId) ?: StrategyRegistry.getDefault()
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, buildNotification("VPN подключен"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(1, buildNotification("VPN подключен"))
        }

        val builder = Builder()
            .setSession("Plum VPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)

        // Установка DNS
        val dns = Prefs.getDns(this)
        if (dns.isNotEmpty()) {
            builder.addDnsServer(dns)
        }

        // Белый список
        val whitelist = Prefs.getWhitelist(this)
        if (whitelist.isNotEmpty()) {
            for (pkg in whitelist) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (e: PackageManager.NameNotFoundException) {}
            }
        }

        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            stopVpn()
            return
        }

        if (vpnInterface == null) {
            stopVpn()
            return
        }

        isRunning.set(true)

        Thread {
            try {
                val input = FileInputStream(vpnInterface!!.fileDescriptor)
                val output = FileOutputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteBuffer.allocate(32767)

                while (isRunning.get()) {
                    buffer.clear()
                    val length = input.channel.read(buffer)
                    if (length > 0) {
                        val packet = buffer.array().copyOf(length)
                        val processed = currentStrategy?.process(packet) ?: packet
                        output.write(processed)
                    }
                }
            } catch (e: Exception) {
                stopVpn()
            }
        }.start()
    }

    private fun stopVpn() {
        isRunning.set(false)
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("vpn_channel", "VPN Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("Plum VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.plum.vpn.START"
        const val ACTION_STOP = "com.plum.vpn.STOP"
        const val EXTRA_STRATEGY = "strategy_id"
    }
}
