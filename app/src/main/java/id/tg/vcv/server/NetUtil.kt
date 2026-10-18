package id.tg.vcv.server

import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {

    /**
     * Alamat IPv4 lokal HP ini yang bisa dijangkau HP siswa (WiFi atau hotspot).
     * Urutan: hotspot dulu (ap/swlan), lalu wlan, lalu lainnya.
     */
    fun lanAddresses(): List<String> {
        val result = mutableListOf<Pair<Int, String>>()
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (ni in ifaces.toList()) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name.lowercase()
                // Lewati jaringan seluler & VPN, hanya WiFi/hotspot yang relevan untuk siswa
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") ||
                    name.startsWith("dummy") || name.startsWith("v4-")) continue
                val rank = when {
                    name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 0
                    name.startsWith("wlan") -> 1
                    else -> 2
                }
                for (addr in ni.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        addr.hostAddress?.let { result += rank to it }
                    }
                }
            }
        }
        return result.sortedBy { it.first }.map { it.second }.distinct()
    }
}
