package com.xabber.xmpp.dns

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.minidns.hla.ResolverApi
import org.minidns.hla.SrvResolverResult
import org.minidns.record.SRV
import org.minidns.dnslabel.DnsLabel
import org.minidns.dnsname.DnsName
import java.net.Inet4Address
import java.net.InetAddress
import java.io.IOException


/**
 * A typical DNS resolver for resolving SRV records using MiniDNS.
 */
class SRVDNSResolver {
    /**
     * Resolves SRV records for the given service, protocol, and domain.
     *
     * @param service The service name (e.g., "xmpp-client").
     * @param protocol The protocol (e.g., "tcp").
     * @param domain The domain name (e.g., "xmpp.org").
     * @return An SrvResolverResult containing the resolved SRV records or an error.
     */
    fun resolveSRV(service: String, protocol: String, domain: String): SrvResolverResult {
        val serviceLabel = DnsLabel.from("_$service")
        val protocolLabel = DnsLabel.from("_$protocol")
        val domainName = DnsName.from(domain)
        return ResolverApi.INSTANCE.resolveSrv(serviceLabel, protocolLabel, domainName)
    }
}

/**
 * Custom DNS resolver for OkHttp, mapping specific hostnames to IPs.
 */
class DNSResolver(private val map: Map<String, String>) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val ip = map[hostname]
        return if (ip != null) {
            println("Resolved $hostname to $ip")
            listOf(Inet4Address.getByName(ip))
        } else {
            println("Using system DNS for $hostname")
            Dns.SYSTEM.lookup(hostname)
        }
    }
}

/**
 * Fetches data from an SRV-resolved target using HTTP.
 *
 * @param scheme The URL scheme (e.g., "https").
 * @param service The service name (e.g., "xmpp-client").
 * @param protocol The protocol (e.g., "tcp").
 * @param domain The domain name (e.g., "xmpp.org").
 * @return The HTTP response body or an error message.
 */
fun fetchFromSrv(scheme: String, service: String, protocol: String, domain: String): String {
    val resolver = SRVDNSResolver()
    val dnsMap = mapOf("example.com" to "23.55.44.79") // Configurable map
    val customDns = DNSResolver(dnsMap)
    val okHttpClient = OkHttpClient.Builder()
        .dns(customDns)
        .build()

    try {
        val result: SrvResolverResult = resolver.resolveSRV(service, protocol, domain)
        if (result.wasSuccessful() && result.sortedSrvResolvedAddresses.isNotEmpty()) {
            val firstSrv: SRV = result.sortedSrvResolvedAddresses.first().srv
            val target = firstSrv.target.toString().trimEnd('.')
            val url = "$scheme://$target:${firstSrv.port}" // Include SRV port

            val request = Request.Builder()
                .url(url)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                return if (response.isSuccessful) {
                    response.body?.string() ?: "No response body"
                } else {
                    "Error: HTTP ${response.code} from $url"
                }
            }
        } else {
            return "No SRV records found for _${service}._${protocol}.${domain}"
        }
    } catch (e: IOException) {
        return "IO Error: ${e.message}"
    } catch (e: Exception) {
        return "Error: ${e.message}"
    }
}


// Custom DNS resolver, unchanged
//class DNSResolver(private val map: Map<String, String>) : Dns {
//    override fun lookup(hostname: String): List<InetAddress> {
//        val ip = map[hostname]
//        return if (ip != null) {
//            println("Resolved $hostname to $ip")
//            listOf(Inet4Address.getByName(ip))
//        } else {
//            println("Using system DNS for $hostname")
//            Dns.SYSTEM.lookup(hostname)
//        }
//    }
//}
//
//// Function to resolve SRV and perform HTTP request
//fun fetchFromSrv(scheme: String, service: String, protocol: String, domain: String): String {
//    // Initialize OkHttp client with custom DNS
//    val dnsMap = mapOf("example.com" to "23.55.44.79") // Configurable map
//    val customDns = DNSResolver(dnsMap)
//    val okHttpClient = OkHttpClient.Builder()
//        .dns(customDns)
//        .build()
//
//    // Convert inputs to MiniDNS types
//    val serviceLabel = DnsLabel.from("_$service") // e.g., "_mysrv"
//    val protocolLabel = DnsLabel.from("_$protocol") // e.g., "_tcp"
//    val domainName = DnsName.from(domain) // e.g., "example.com"
//
//    // Resolve SRV record using MiniDNS
//    try {
//        val result: SrvResolverResult = ResolverApi.INSTANCE.resolveSrv(serviceLabel, protocolLabel, domainName)
//        if (result.wasSuccessful() && result.sortedSrvResolvedAddresses.isNotEmpty()) {
//            val firstSrv: SRV = result.sortedSrvResolvedAddresses.first().srv
//            val target = firstSrv.target.toString().trimEnd('.')
//            val url = "$scheme://$target" // Construct URL, e.g., https://target
//
//            // Perform HTTP request
//            val request = Request.Builder()
//                .url(url)
//                .build()
//            okHttpClient.newCall(request).execute().use { response ->
//                return if (response.isSuccessful) {
//                    val body = response.body?.string() ?: "No response body"
//                    println("Response from $url: $body")
//                    body
//                } else {
//                    "Error: HTTP ${response.code} from $url"
//                }
//            }
//        } else {
//            return "No SRV records found for _${service}._${protocol}.${domain}"
//        }
//    } catch (e: IOException) {
//        println("IO Error: ${e.message}")
//        return "Error: ${e.message}"
//    } catch (e: Exception) {
//        println("Error: ${e.message}")
//        return "Error: ${e.message}"
//    }
//}
//
