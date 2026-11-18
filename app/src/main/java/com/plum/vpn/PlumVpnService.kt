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
    private var byedpiProcess: Process? = null
    private var tunnelProcess: Process? = null
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
        startForeground(1, buildNotification("Запуск движка..."))

        // 1. Создаем TUN интерфейс
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

        // 2. Запускаем ByeDPI
        val byedpiFile = File(applicationInfo.nativeLibraryDir, "libbyedpi.so")
        val cmd = Prefs.getCustomCommand(this).split(" ").filter { it.isNotBlank() }.toMutableList()
        if (cmd.isEmpty()) cmd.addAll(listOf("-d1", "-s1+s"))
        
        val byedpiCmd = mutableListOf(byedpiFile.absolutePath, "-p", "1080")
        byedpiCmd.addAll(cmd)

        try {
            val pb = ProcessBuilder(byedpiCmd)
            pb.redirectErrorStream(true)
            byedpiProcess = pb.start()
        } catch (e: Exception) {
            updateNotification("Ошибка ByeDPI: ${e.message}")
            stopVpn()
            return
        }

        // 3. Запускаем hev-socks5-tunnel
        val tunnelFile = File(applicationInfo.nativeLibraryDir, "libhev-socks5-tunnel.so")
        val configFile = File(filesDir, "hev-config.yaml")
        configFile.writeText("""
            tunnel:
              mtu: 8500
              ipv4: 10.0.0.2
            socks5:
              address: 127.0.0.1
              port: 1080
              udp: 'udp'
        """.trimIndent())

        val tunnelCmd = listOf(
            tunnelFile.absolutePath,
            configFile.absolutePath,
            vpnInterface!!.fd.toString()
        )

        try {
            val pbTunnel = ProcessBuilder(tunnelCmd)
            pbTunnel.redirectErrorStream(true)
            tunnelProcess = pbTunnel.start()
        } catch (e: Exception) {
            updateNotification("Ошибка моста: ${e.message}")
            stopVpn()
            return
        }

        isRunning = true
        updateNotification("Соединение защищено")
    }

    private fun stopVpn() {
        isRunning = false
        byedpiProcess?.destroy()
        tunnelProcess?.destroy()
        byedpiProcess = null
        tunnelProcess = null
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
            .setContentTitle("PlumDuck")
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