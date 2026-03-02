package com.xabber.xmpp.dns

import android.util.Log
import org.minidns.hla.ResolverApi
import org.minidns.hla.srv.SrvProto
import org.minidns.hla.srv.SrvService
import org.minidns.record.SRV
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object DNSResolver {
    private val TAG = "DNSResolver"
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    private data class CacheEntry(
        val value: Pair<String, Int>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }

    private val dohProviders = listOf(
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/dns-query",
        "https://dns.quad9.net/dns-query"
    )

    /**
     * Race all DoH providers in parallel and return the first non-null result.
     * Cancels remaining providers once a result is found.
     */
    private suspend fun <T> tryDohProvidersParallel(
        providers: List<String> = dohProviders,
        timeoutMs: Long = 5000,
        block: suspend (DohResolver) -> T?
    ): T? = withTimeoutOrNull(timeoutMs) {
        coroutineScope {
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

            // Race: keep selecting completed deferreds until we get a non-null result or all are done
            val remaining = deferreds.toMutableList()
            while (remaining.isNotEmpty()) {
                val result = select<T?> {
                    for (deferred in remaining) {
                        deferred.onAwait { it }
                    }
                }
                if (result != null) {
                    // Cancel all remaining deferreds
                    deferreds.forEach { it.cancel() }
                    return@coroutineScope result
                }
                // Remove completed (null-returning) deferreds
                remaining.removeAll { it.isCompleted }
            }
            null
        }
    }

    suspend fun resolveSRV(host: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        // 1. Check cache
        cacheMutex.withLock {
            cache[host]?.let { entry ->
                if (!entry.isExpired()) {
                    Log.d(TAG, "Returning cached result for $host: ${entry.value.first}:${entry.value.second}")
                    return@withContext entry.value
                } else {
                    cache.remove(host)
                }
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
            cacheMutex.withLock { cache[host] = CacheEntry(minidnsResult) }
            return@withContext minidnsResult
        }

        // 3. Try DoH SRV with all providers (parallel)
        val dohSrvResult = try {
            tryDohProvidersParallel(timeoutMs = 5000) { resolver ->
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
            cacheMutex.withLock { cache[host] = CacheEntry(dohSrvResult) }
            return@withContext dohSrvResult
        }

        // 4. FALLBACK: No SRV records found – try A record for the original host and use default port 5222
        Log.w(TAG, "All SRV attempts failed, falling back to A record for $host with port 5222")
        val ip = resolveA(host)
        if (ip != null) {
            val fallbackResult = Pair(ip, 5222)
            cacheMutex.withLock { cache[host] = CacheEntry(fallbackResult) }
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

        // Fallback: try DoH for A records in parallel (racing)
        val rawAnswers = result.answers
        val srvRecords = rawAnswers.filterIsInstance<SRV>()
            .sortedWith(compareBy({ it.priority }, { -it.weight }))

        for (srv in srvRecords) {
            val target = srv.target.toString().trimEnd('.')
            val port = srv.port
            Log.d(TAG, "Attempting DoH A resolution for target: $target")

            val ip = tryDohProvidersParallel(timeoutMs = 5000) { resolver ->
                resolver.resolveARecords(target).firstOrNull()
            }

            if (ip != null) {
                Log.d(TAG, "DoH resolved $target to $ip")
                return Pair(ip, port)
            }
        }

        Log.w(TAG, "No IP addresses found for any SRV target")
        return null
    }

    suspend fun resolveA(host: String): String? = withContext(Dispatchers.IO) {
        // Try DoH with parallel racing (no mutex during network I/O)
        val dohIp = try {
            tryDohProvidersParallel(timeoutMs = 5000) { resolver ->
                resolver.resolveARecords(host).firstOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "DoH A record resolution error: ${e.message}", e)
            null
        }
        if (dohIp != null) {
            Log.d(TAG, "DoH resolved $host to $dohIp")
            return@withContext dohIp
        }

        // Fallback to InetAddress (system DNS)
        try {
            val inetAddress = InetAddress.getByName(host).hostAddress
            Log.d(TAG, "InetAddress resolved: $inetAddress for $host")
            return@withContext inetAddress
        } catch (e: Exception) {
            Log.e(TAG, "InetAddress A record resolution failed: ${e.message}")
            return@withContext null
        }
    }
}

suspend fun fetchFromSrv(host: String): Pair<String, Int>? {
    return DNSResolver.resolveSRV(host)
}
