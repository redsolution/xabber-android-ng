package com.xabber.xmpp.dns

import android.util.Log
import org.minidns.dnsmessage.DnsMessage
import org.minidns.hla.ResolverApi
import org.minidns.hla.ResolverResult
import org.minidns.hla.SrvResolverResult
import org.minidns.hla.srv.SrvProto
import org.minidns.hla.srv.SrvService
import org.minidns.record.A
import org.minidns.record.SRV
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DNSResolver {
    private val TAG = "DNSResolver"
    private val resolutionMutex = Mutex()
    private val cache = mutableMapOf<String, Pair<String, Int>>()

    // Smaller, more reliable DoH list for SRV fallback
    private val reliableDohProviders = listOf(
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/dns-query",
        "https://dns.quad9.net/dns-query"
    )

    // Full list for A record resolution (can be kept as is)
    private val dohProviders = listOf(
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/dns-query",
        "https://dns.quad9.net/dns-query",
        "https://doh.libredns.gr/dns-query",
        "https://dns.adguard.com/dns-query",
        "https://doh.opendns.com/dns-query",
        "https://doh.securedns.eu/dns-query",
        "https://doh.dns.sb/dns-query",
        "https://doh.10centuries.org/dns-query",
        "https://doh.42l.fr/dns-query",
        "https://dns.flatuslir.is/dns-query",
        "https://doh.crypto-solutions.net/dns-query"
    )

    // Helper to try each DoH provider until one succeeds
    private suspend fun <T> tryDohProviders(
        providers: List<String> = dohProviders,
        block: suspend (DohResolver) -> T?
    ): T? {
        for (url in providers) {
            Log.d(TAG, "Trying DoH provider: $url")
            val resolver = DohResolver(url)
            try {
                val result = block(resolver)
                if (result != null) {
                    Log.d(TAG, "DoH succeeded with provider: $url")
                    return result
                }
            } catch (e: Exception) {
                Log.e(TAG, "DoH provider $url threw exception: ${e.message}", e)
            }
            delay(100)
        }
        return null
    }

    suspend fun resolveSRV(host: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        resolutionMutex.withLock {
            // 1. Check cache
            cache[host]?.let {
                Log.d(TAG, "Returning cached result for $host: ${it.first}:${it.second}")
                return@withLock it
            }

            // 2. Try minidns SRV first (fast and likely to work)
            val minidnsResult = try {
                resolveSRVviaMinidns(host)
            } catch (e: Exception) {
                Log.e(TAG, "Minidns SRV error: ${e.message}", e)
                null
            }
            if (minidnsResult != null) {
                Log.d(TAG, "Minidns SRV succeeded for $host: ${minidnsResult.first}:${minidnsResult.second}")
                cache[host] = minidnsResult
                return@withLock minidnsResult
            }

            // 3. If minidns fails, try DoH SRV with reliable providers only
            val dohResult = try {
                resolveSRVviaDoH(host, reliableDohProviders)
            } catch (e: Exception) {
                Log.e(TAG, "DoH SRV error: ${e.message}", e)
                null
            }
            if (dohResult != null) {
                Log.d(TAG, "DoH SRV succeeded for $host: ${dohResult.first}:${dohResult.second}")
                cache[host] = dohResult
                return@withLock dohResult
            }

            // 4. If all else fails
            Log.e(TAG, "All SRV resolution attempts failed for $host")
            null
        }
    }

    /**
     * Resolve SRV using minidns, then use DoH for A records.
     */
    private suspend fun resolveSRVviaMinidns(host: String): Pair<String, Int>? {
        val result = ResolverApi.INSTANCE.resolveSrv(SrvService.xmpp_client, SrvProto.tcp, host)
        if (!result.wasSuccessful()) {
            Log.e(TAG, "Minidns SRV failed with response code: ${result.responseCode}")
            return null
        }

        val rawAnswers = result.answers
        val srvRecords = rawAnswers.filterIsInstance<SRV>()
            .sortedWith(compareBy({ it.priority }, { -it.weight }))

        if (srvRecords.isEmpty()) {
            Log.w(TAG, "No SRV records in answers")
            return null
        }

        // Try each SRV target in order of priority/weight
        for (srv in srvRecords) {
            val target = srv.target.toString().trimEnd('.')
            val port = srv.port
            Log.d(TAG, "Attempting DoH A resolution for target: $target (priority=${srv.priority}, weight=${srv.weight})")

            val ip = tryDohProviders(providers = dohProviders) { resolver ->
                resolver.resolveARecords(target).firstOrNull()
            }

            if (ip != null) {
                Log.d(TAG, "DoH resolved $target to $ip")
                return Pair(ip, port)
            }
        }

        // If DoH A resolution failed for all, fall back to minidns built-in addresses (if any)
        val srvRecordsWithAddresses = result.sortedSrvResolvedAddresses
        for (srvRecord in srvRecordsWithAddresses) {
            for (inetAddressRR in srvRecord.addresses) {
                val ip = inetAddressRR.inetAddress.hostAddress
                val port = srvRecord.port
                Log.d(TAG, "Resolved via minidns built-in: $ip, port: $port")
                return Pair(ip, port)
            }
        }

        Log.w(TAG, "No IP addresses found for any SRV target")
        return null
    }

    /**
     * Resolve SRV using DoH (fallback when minidns fails).
     */
    private suspend fun resolveSRVviaDoH(domain: String, providers: List<String>): Pair<String, Int>? {
        return tryDohProviders(providers = providers) { resolver ->
            val service = "xmpp-client"
            val protocol = "tcp"
            val srvRecords = resolver.resolveSrvRecords(service, protocol, domain)
            if (srvRecords.isEmpty()) return@tryDohProviders null

            val sorted = srvRecords.sortedWith(compareBy({ it.priority }, { -it.weight }))
            for (srv in sorted) {
                val ips = resolver.resolveARecords(srv.target)
                if (ips.isNotEmpty()) {
                    return@tryDohProviders Pair(ips.first(), srv.port)
                }
            }
            null
        }
    }

    suspend fun resolveA(host: String): String? = withContext(Dispatchers.IO) {
        resolutionMutex.withLock {
            // Try DoH with full provider list
            val dohIp = try {
                tryDohProviders(providers = dohProviders) { resolver ->
                    resolver.resolveARecords(host).firstOrNull()
                }
            } catch (e: Exception) {
                Log.e(TAG, "DoH A record resolution error: ${e.message}", e)
                null
            }
            if (dohIp != null) {
                Log.d(TAG, "DoH resolved $host to $dohIp")
                return@withLock dohIp
            }

            // Fallback to InetAddress (system DNS)
            try {
                val inetAddress = InetAddress.getByName(host).hostAddress
                Log.d(TAG, "InetAddress resolved: $inetAddress for $host")
                return@withLock inetAddress
            } catch (e: Exception) {
                Log.e(TAG, "InetAddress A record resolution failed: ${e.message}")
                return@withLock null
            }
        }
    }
}

suspend fun fetchFromSrv(host: String): Pair<String, Int>? {
    return DNSResolver().resolveSRV(host)
}