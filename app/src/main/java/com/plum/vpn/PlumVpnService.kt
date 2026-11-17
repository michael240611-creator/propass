// file: app/src/main/java/com/plum/vpn/PlumVpnService.kt
package com.plum.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class PlumVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var byedpiProcess: Process? = null
    private var tunnelProcess: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        createNotificationChannel()
        startForeground(1, buildNotification("Запуск движка..."))

        // 1. Распаковываем .so из APK во внутреннюю папку
        val binDir = File(filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val byedpiFile = File(binDir, "libbyedpi.so")
        val tunnelFile = File(binDir, "libhev-socks5-tunnel.so")

        try {
            copyAsset("jniLibs/arm64-v8a/libbyedpi.so", byedpiFile)
            copyAsset("jniLibs/arm64-v8a/libhev-socks5-tunnel.so", tunnelFile)
            byedpiFile.setExecutable(true)
            tunnelFile.setExecutable(true)
        } catch (e: Exception) {
            stopVpn()
            return
        }

        // 2. Запускаем ByeDPI как процесс
        val cmd = Prefs.getCustomCommand(this).split(" ").filter { it.isNotBlank() }.toMutableList()
        if (cmd.isEmpty()) cmd.addAll(listOf("-d1", "-s1+s"))
        
        val byedpiCmd = mutableListOf(byedpiFile.absolutePath, "-p", "1080")
        byedpiCmd.addAll(cmd)

        val pbByeDpi = ProcessBuilder(byedpiCmd)
        pbByeDpi.redirectErrorStream(true)
        byedpiProcess = pbByeDpi.start()

        // 3. Создаем VPN интерфейс
        val builder = Builder()
            .setSession("Plum VPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(Prefs.getDns(this))
            .setBlocking(true)

        Prefs.getWhitelist(this).forEach { pkg ->
            try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) { stopVpn(); return }

        // 4. Запускаем hev-socks5-tunnel
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
        
        val pbTunnel = ProcessBuilder(tunnelCmd)
        pbTunnel.redirectErrorStream(true)
        tunnelProcess = pbTunnel.start()

        updateNotification("Соединение защищено")
    }

    private fun copyAsset(assetPath: String, dest: File) {
        assets.open(assetPath).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun stopVpn() {
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
            .setContentTitle("Plum VPN")
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
