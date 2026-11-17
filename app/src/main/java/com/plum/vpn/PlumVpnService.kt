// file: app/src/main/java/com/plum/vpn/PlumVpnService.kt
package com.plum.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File

class PlumVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        createNotificationChannel()
        startForeground(1, buildNotification("Запуск..."))

        val builder = Builder()
            .setSession("Plum Duck")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(Prefs.getDns(this))
            .setBlocking(true)

        Prefs.getWhitelist(this).forEach { pkg ->
            try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            stopVpn()
            return
        }

        // Запускаем ByeDPI через наш новый мост
        val cmd = Prefs.getCustomCommand(this).split(" ").filter { it.isNotBlank() }.toTypedArray()
        val finalArgs = if (cmd.isEmpty()) arrayOf("-p", "1080", "-d1", "-s1+s") else arrayOf("-p", "1080", *cmd)
        
        NativeBridge.startByeDpi(finalArgs)

        isRunning = true
        updateNotification("Соединение защищено")
    }

    private fun stopVpn() {
        isRunning = false
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
            val channel = NotificationChannel("vpn", "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "vpn")
            .setContentTitle("Plum Duck")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.plum.vpn.START"
        const val ACTION_STOP = "com.plum.vpn.STOP"
    }
}
