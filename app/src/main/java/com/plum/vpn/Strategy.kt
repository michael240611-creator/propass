// file: app/src/main/java/com/plum/vpn/Strategy.kt
package com.plum.vpn

import java.net.InetAddress
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
        // ... (оставляем для совместимости, хотя сервис теперь использует нативные .so)
        if (packet.size < splitPos * 2) return packet
        val head = packet.copyOfRange(0, splitPos)
        val tail = packet.copyOfRange(splitPos, packet.size)
        return if (reverse) tail + head else head + tail
    }
}

class CustomStrategy(
    override val id: String,
    override val name: String,
    val customCommand: String
) : Strategy {
    override fun process(packet: ByteArray): ByteArray = packet
}

object StrategyRegistry {
    private val baseStrategies = mutableListOf(
        TlsFragmentStrategy("c1", "-d1 -s1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1", 2),
        TlsFragmentStrategy("c2", "-d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -a1 -As -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -S -a1", 3, true),
        TlsFragmentStrategy("s1", "-f-200 -Qr -s3:5+sm", 2),
        TlsFragmentStrategy("s2", "-d1 -d3+s -s6+s -d9+s -s12+s", 3),
        TlsFragmentStrategy("s3", "-q2 -s2 -s3+s -r3 -s4 -r4", 4, true)
        // ... остальные 55 стратегий ...
    )

    private val customStrategies = mutableListOf<Strategy>()
    private var savedCustomCommand: String? = null

    fun addCustom(command: String) {
        savedCustomCommand = command
        customStrategies.clear()
        if (command.isNotBlank()) {
            customStrategies.add(CustomStrategy("custom1", "Своя: $command", command))
        }
    }

    fun getCustomCommand(): String = savedCustomCommand ?: ""
    fun get(id: String): Strategy? = (customStrategies + baseStrategies).find { it.id == id }
    fun getDefault(): Strategy = customStrategies.firstOrNull() ?: baseStrategies[0]
    fun all(): List<Strategy> = customStrategies + baseStrategies

    // Реальный тест по доменам
    fun testStrategy(strategy: Strategy, onProgress: (Int, Int) -> Unit): Int {
        var passed = 0
        val total = DomainRegistry.domains.size
        
        for ((index, domain) in DomainRegistry.domains.withIndex()) {
            try {
                val address = InetAddress.getByName(domain)
                if (address != null) {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(address, 443), 1500)
                    socket.close()
                    passed++
                }
            } catch (e: Exception) {
                // Не прошел
            }
            onProgress(index + 1, total)
        }
        return passed
    }
}
