package com.example.data.remote

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Custom DNS implementation that sorts resolved addresses so that IPv4 addresses
 * are attempted first. This prevents connection failures when servers have AAAA records
 * that are unreachable, blocked, or connection-refused in certain environments.
 */
object IPv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.size <= 1) return addresses

        // Sort addresses placing IPv4 (Inet4Address) first, followed by any IPv6
        return addresses.sortedWith(Comparator { a, b ->
            when {
                a is Inet4Address && b !is Inet4Address -> -1
                a !is Inet4Address && b is Inet4Address -> 1
                else -> 0
            }
        })
    }
}
