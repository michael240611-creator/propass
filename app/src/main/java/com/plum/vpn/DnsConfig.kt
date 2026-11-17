// file: app/src/main/java/com/plum/vpn/DnsConfig.kt
package com.plum.vpn

data class DnsServer(
    val name: String,
    val ip: String
)

object DnsRegistry {
    val servers = listOf(
        DnsServer("Системный", ""),
        DnsServer("Google", "8.8.8.8"),
        DnsServer("Cloudflare", "1.1.1.1"),
        DnsServer("Quad9", "9.9.9.9"),
        DnsServer("Яндекс", "77.88.8.8"),
        DnsServer("Свой", "custom")
    )
}
