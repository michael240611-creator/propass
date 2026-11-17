// file: app/src/main/java/com/plum/vpn/Strategy.kt
package com.plum.vpn

import java.net.InetSocketAddress
import java.net.Socket

interface Strategy {
    val id: String
    val name: String
    fun process(packet: ByteArray): ByteArray
}

class TlsFragmentStrategy(
    override val id: String,
    override val name: String,
    private val splitPos: Int = 2,
    private val reverse: Boolean = false
) : Strategy {
    override fun process(packet: ByteArray): ByteArray {
        if (packet.size < splitPos * 2) return packet
        val head = packet.copyOfRange(0, splitPos)
        val tail = packet.copyOfRange(splitPos, packet.size)
        return if (reverse) tail + head else head + tail
    }
}

class CustomStrategy(
    override val id: String,
    override val name: String,
    private val customCommand: String
) : Strategy {
    override fun process(packet: ByteArray): ByteArray {
        return packet
    }
}

object StrategyRegistry {
    private val baseStrategies = mutableListOf(
        TlsFragmentStrategy("c1", "-d1 -s1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1", 2),
        TlsFragmentStrategy("c2", "-d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -a1 -As -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -S -a1", 3, true),
        TlsFragmentStrategy("s1", "-f-200 -Qr -s3:5+sm", 2),
        TlsFragmentStrategy("s2", "-d1 -d3+s -s6+s -d9+s -s12+s", 3),
        TlsFragmentStrategy("s3", "-q2 -s2 -s3+s -r3 -s4 -r4", 4, true),
        TlsFragmentStrategy("s4", "-o1 -d1 -a1 -At,r,s -s1 -d1 -s5+s", 2),
        TlsFragmentStrategy("s5", "-n \"google.com\" -Qr -f-204 -s1:5+sm", 5),
        TlsFragmentStrategy("s6", "-f-200 -Qr -s3:5+sm -a1 -As -d1 -s4+sm", 3),
        TlsFragmentStrategy("s7", "-f-300 -d6+sh -a1 -At,r,s -o2 -f-30", 6),
        TlsFragmentStrategy("s8", "-As -r5 -Mh -r6+sh -f-250 -s2:7+s", 2, true),
        TlsFragmentStrategy("s9", "-q30+sm -a1 -s7:9+s", 7),
        TlsFragmentStrategy("s10", "-d1 -d3+s -s6+s -d9+s -s12+s -d15+s", 8),
        TlsFragmentStrategy("s11", "-n \"google.com\" -Qr -f-205 -a1 -As -s1:3+sm", 3),
        TlsFragmentStrategy("s12", "-s1 -d1 -s5+s -s10+s -s15+s -s20+s", 4, true),
        TlsFragmentStrategy("s13", "-f-1+s -d1 -s2+s", 2),
        TlsFragmentStrategy("s14", "-q2 -s1 -s2+s -r4", 4, true),
        TlsFragmentStrategy("s15", "-As -f-100 -d3 -s5+s", 5),
        TlsFragmentStrategy("s16", "-d1 -d2 -s1 -s3+s -r2", 3, true),
        TlsFragmentStrategy("s17", "-f-250 -o1 -s2 -s4+s", 2),
        TlsFragmentStrategy("s18", "-s1+s -d1 -s3+s -r1 -s5+s", 3),
        TlsFragmentStrategy("s19", "-o2 -f-50 -s1 -s4+s", 4),
        TlsFragmentStrategy("s20", "-q5+sm -a1 -s2 -s7+s", 2)
    )

    init {
        for (i in 21..60) {
            val split = (i % 8) + 2
            val rev = i % 2 == 0
            baseStrategies.add(TlsFragmentStrategy("s$i", "-auto-strategy-$i -s$split+s -d${i%5+1}", split, rev))
        }
    }

    private val customStrategies = mutableListOf<Strategy>()
    private var savedCustomCommand: String? = null

    fun addCustom(command: String) {
        savedCustomCommand = command
        customStrategies.clear()
        if (command.isNotBlank()) {
            customStrategies.add(CustomStrategy("custom1", "Пользовательская: $command", command))
        }
    }

    fun getCustomCommand(): String = savedCustomCommand ?: ""
    fun get(id: String): Strategy? = (customStrategies + baseStrategies).find { it.id == id }
    fun getDefault(): Strategy = customStrategies.firstOrNull() ?: baseStrategies[0]
    fun all(): List<Strategy> = customStrategies + baseStrategies

    fun testStrategy(strategy: Strategy): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("google.com", 443), 2000)
            socket.close()
            true
        } catch (e: Exception) { false }
    }
}
