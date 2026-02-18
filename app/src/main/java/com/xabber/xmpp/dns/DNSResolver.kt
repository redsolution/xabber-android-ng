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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object DNSResolver {
    private val TAG = "DNSResolver"
    private val resolutionMutex = Mutex()
    private val cache = mutableMapOf<String, Pair<String, Int>>()
    private var preferredDoHProvider: String? = null
    // Smaller, more reliable DoH list for SRV fallback
    private val reliableDohProviders = listOf(
        "https://dns.google/dns-query",
        "https://cloudflare-dns.com/dns-query",
        "https://dns.quad9.net/dns-query"
    )


    // Full list for A record resolution (can be kept as is)
    private val dohProviders = listOf(
        "https://dns.cloudflare.com",
        "https://adblock.mydns.network",
        "https://commons.host",
        "https://cloudflare-dns.com"
    )

    // Helper to try each DoH provider until one succeeds
    private suspend fun <T> tryDohProviders(
        providers: List<String> = dohProviders,
        block: suspend (DohResolver) -> T?
    ): T? {
        val orderedProviders = if (preferredDoHProvider != null) {
            listOf(preferredDoHProvider!!) + providers.filter { it != preferredDoHProvider }
        } else providers

        for (url in orderedProviders) {
            Log.d(TAG, "Trying DoH provider: $url")
            val resolver = DohResolver(url)
                val result = block(resolver)
            if (result != null) {
                preferredDoHProvider = url
                return result
            }
        }
        return null
    }

    private suspend fun <T> tryDohProvidersParallel(
        providers: List<String> = dohProviders,
        timeoutMs: Long = 5000, // overall timeout for all parallel attempts
        block: suspend (DohResolver) -> T?
    ): T? = withTimeoutOrNull(timeoutMs) {
        // Launch a coroutine for each provider
        val deferreds = providers.map { url ->
            async {
                try {
                    val resolver = DohResolver(url)
                    block(resolver)
                } catch (e: Exception) {
                    Log.e(TAG, "DoH provider $url threw exception", e)
                    null
                }
            }
        }
        // Wait for the first non‑null result
        for (deferred in deferreds) {
            val result = deferred.await()
            if (result != null) {
                Log.d(TAG, "DoH succeeded with provider: ${deferred.getCompleted()}")
                return@withTimeoutOrNull result
            }
        }
        null
    }

    suspend fun resolveSRV(host: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        resolutionMutex.withLock {
            // 1. Check cache
            cache[host]?.let {
                Log.d(TAG, "Returning cached result for $host: ${it.first}:${it.second}")
                return@withLock it
            }
        }

        // 2. Try minidns SRV first (fast, but may fail under VPN)
        val minidnsResult = try {
            resolveSRVviaMinidns(host)
        } catch (e: Exception) {
            Log.e(TAG, "Minidns SRV error: ${e.message}", e)
            null
        }
        if (minidnsResult != null) {
            Log.d(TAG, "Minidns SRV succeeded for $host: ${minidnsResult.first}:${minidnsResult.second}")
            resolutionMutex.withLock { cache[host] = minidnsResult }
            return@withContext minidnsResult
        }

        // 3. Try DoH SRV with ALL providers (parallel) – replace reliableDohProviders with full list
        val dohSrvResult = try {
            // Use the full provider list for SRV as well
            tryDohProvidersParallel(providers = dohProviders, timeoutMs = 5000) { resolver ->
                val srvRecords = resolver.resolveSrvRecords("xmpp-client", "tcp", host)
                if (srvRecords.isEmpty()) return@tryDohProvidersParallel null
                // Try each SRV target in priority order
                for (srv in srvRecords.sortedWith(compareBy({ it.priority }, { -it.weight }))) {
                    val ips = resolver.resolveARecords(srv.target)
                    if (ips.isNotEmpty()) {
                        return@tryDohProvidersParallel Pair(ips.first(), srv.port)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "DoH SRV error: ${e.message}", e)
            null
        }
        if (dohSrvResult != null) {
            Log.d(TAG, "DoH SRV succeeded for $host: ${dohSrvResult.first}:${dohSrvResult.second}")
            resolutionMutex.withLock { cache[host] = dohSrvResult }
            return@withContext dohSrvResult
        }

        // 4. FALLBACK: No SRV records found – try A record for the original host and use default port 5222
        Log.w(TAG, "All SRV attempts failed, falling back to A record for $host with port 5222")
        val ip = resolveA(host)   // resolveA already uses its own caching and DoH/system fallback
        if (ip != null) {
            val fallbackResult = Pair(ip, 5222)
            resolutionMutex.withLock { cache[host] = fallbackResult }
            return@withContext fallbackResult
        }

        // 5. Complete failure
        Log.e(TAG, "All DNS resolution attempts failed for $host")
        null
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

        // Fast path: use already-resolved addresses if available
        val srvRecordsWithAddresses = result.sortedSrvResolvedAddresses
        if (srvRecordsWithAddresses.isNotEmpty()) {
            for (srvRecord in srvRecordsWithAddresses) {
                for (inetAddressRR in srvRecord.addresses) {
                    val ip = inetAddressRR.inetAddress.hostAddress
                    val port = srvRecord.port
                    Log.d(TAG, "Resolved via minidns built-in: $ip, port: $port")
                    return Pair(ip, port)
                }
            }
        }

        // Fallback: try DoH for A records in parallel
        val rawAnswers = result.answers
        val srvRecords = rawAnswers.filterIsInstance<SRV>()
            .sortedWith(compareBy({ it.priority }, { -it.weight }))

        for (srv in srvRecords) {
            val target = srv.target.toString().trimEnd('.')
            val port = srv.port
            Log.d(TAG, "Attempting DoH A resolution for target: $target")

            val ip = withTimeoutOrNull(5000) {
                // Launch parallel requests, each paired with its URL
                val deferreds = dohProviders.map { url ->
                    url to async {
                        try {
                            val resolver = DohResolver(url)
                            resolver.resolveARecords(target).firstOrNull()
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                // Wait for the first successful result
                for ((url, deferred) in deferreds) {
                    val result = deferred.await()
                    if (result != null) {
                        preferredDoHProvider = url  // cache the working provider
                        return@withTimeoutOrNull result
                    }
                }
                null
            }

            if (ip != null) {
                Log.d(TAG, "DoH resolved $target to $ip")
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
    return DNSResolver.resolveSRV(host)
}