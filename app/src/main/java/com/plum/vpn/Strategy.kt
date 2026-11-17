// file: app/src/main/java/com/plum/vpn/Strategy.kt
package com.plum.vpn

interface Strategy {
    val id: String
    val name: String
    val description: String
    fun process(packet: ByteArray): ByteArray
}

class TlsFragmentStrategy(
    override val id: String,
    override val name: String,
    override val description: String,
    private val splitPos: Int = 2,
    private val reverse: Boolean = false,
    private val splitTwice: Boolean = false
) : Strategy {
    override fun process(packet: ByteArray): ByteArray {
        if (packet.size < splitPos * 3) return packet
        
        // Простая эвристика: проверяем, похоже ли это на TCP с данными
        // (в реальном проекте нужно парсить IP/TCP заголовки, здесь упрощено)
        
        val head = packet.copyOfRange(0, splitPos)
        val mid = packet.copyOfRange(splitPos, splitPos * 2)
        val tail = packet.copyOfRange(splitPos * 2, packet.size)

        return if (splitTwice) {
            if (reverse) mid + tail + head else head + tail + mid
        } else {
            if (reverse) tail + head else head + tail
        }
    }
}

object StrategyRegistry {
    private val strategies = listOf(
        TlsFragmentStrategy(
            "s1", "-f-200 -Qr -s3:5+sm", "Фрагмент SNI 1", 2, false
        ),
        TlsFragmentStrategy(
            "s2", "-d1 -d3+s -s6+s -d9+s -s12+s", "Каскадный сдвиг", 3, false
        ),
        TlsFragmentStrategy(
            "s3", "-q2 -s2 -s3+s -r3 -s4 -r4", "Реверс кусков", 4, true
        ),
        TlsFragmentStrategy(
            "s4", "-o1 -d1 -a1 -At,r,s -s1 -d1 -s5+s", "Осколки + TTL", 2, false, true
        ),
        TlsFragmentStrategy(
            "s5", "-n \"google.com\" -Qr -f-204 -s1:5+sm", "Подмена SNI Google", 5, false
        ),
        TlsFragmentStrategy(
            "s6", "-f-200 -Qr -s3:5+sm -a1 -As -d1 -s4+sm", "Дабл сплит", 3, false, true
        ),
        TlsFragmentStrategy(
            "s7", "-f-300 -d6+sh -a1 -At,r,s -o2 -f-30", "Тяжёлый обход", 6, false
        ),
        TlsFragmentStrategy(
            "s8", "-As -r5 -Mh -r6+sh -f-250 -s2:7+s", "Микс сдвигов", 2, true
        ),
        TlsFragmentStrategy(
            "s9", "-q30+sm -a1 -s7:9+s", "Агрессивный сплит", 7, false, true
        ),
        TlsFragmentStrategy(
            "s10", "-d1 -d3+s -s6+s -d9+s -s12+s -d15+s", "Глубокий каскад", 8, false
        ),
        TlsFragmentStrategy(
            "s11", "-n \"google.com\" -Qr -f-205 -a1 -As -s1:3+sm", "Фейковый хост", 3, false
        ),
        TlsFragmentStrategy(
            "s12", "-s1 -d1 -s5+s -s10+s -s15+s -s20+s", "Рваный шаг", 4, true, true
        )
    )

    fun get(id: String): Strategy? = strategies.find { it.id == id }
    fun getDefault(): Strategy = strategies[0]
    fun all(): List<Strategy> = strategies
}
