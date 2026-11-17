// file: app/src/main/java/com/plum/vpn/NativeBridge.kt
package com.plum.vpn

object NativeBridge {
    init {
        System.loadLibrary("nativebridge")
        System.loadLibrary("byedpi")
        System.loadLibrary("hev-socks5-tunnel")
    }

    external fun runByeDpi(args: Array<String>): Int

    fun startByeDpi(args: Array<String>) {
        Thread {
            try {
                runByeDpi(args)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }.start()
    }
}
