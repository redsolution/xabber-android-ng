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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DNSResolver {
    private val TAG = "DNSResolver"
    private val resolutionMutex = Mutex()
    private val cache = mutableMapOf<String, Pair<String, Int>>()

    // Uncomment if response history is needed for debugging
    /*
    companion object {
        private val responseHistory = mutableListOf<String>()

        fun getResponseHistory(): List<String> = responseHistory.toList()

        fun clearResponseHistory() {
            responseHistory.clear()
        }
    }
    */

    suspend fun resolveSRV(host: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        resolutionMutex.withLock {
            // Check cache first
            cache[host]?.let {
                Log.d(TAG, "Returning cached result for $host: ${it.first}:${it.second}")
                return@withLock it
            }

            try {
                val result: SrvResolverResult =
                    ResolverApi.INSTANCE.resolveSrv(SrvService.xmpp_client, SrvProto.tcp, host)
                if (!result.wasSuccessful()) {
                    val responseCode: DnsMessage.RESPONSE_CODE = result.responseCode
                    Log.e(TAG, "SRV resolution failed with response code: $responseCode")
                    // responseHistory.add("Host: $host, Result: Failed: Response code $responseCode")
                    return@withLock null
                }

                // Log raw SRV records
                val rawAnswers = result.answers
                Log.d(TAG, "Raw SRV answers: $rawAnswers")
                rawAnswers.filterIsInstance<SRV>().forEach { srv ->
                    Log.d(TAG, "SRV: target=${srv.target}, port=${srv.port}, priority=${srv.priority}, weight=${srv.weight}")
                }

                // Extract hostname from the first SRV record
                val hostName: String = rawAnswers.filterIsInstance<SRV>()
                    .firstOrNull()?.target?.toString() ?: run {
                    Log.w(TAG, "No SRV target found")
                    return@withLock null
                }
                Log.d(TAG, "Extracted hostName: $hostName")

                val srvRecords: List<SrvResolverResult.ResolvedSrvRecord> = result.sortedSrvResolvedAddresses
                Log.d(TAG, "srvRecords size: ${srvRecords.size}")
                if (srvRecords.isEmpty()) {
                    Log.w(TAG, "No resolved SRV records in sortedSrvResolvedAddresses")
                    // Fallback to resolving A records for SRV target
                    val srvRecordsRaw = rawAnswers.filterIsInstance<SRV>()
                    if (srvRecordsRaw.isEmpty()) {
                        Log.w(TAG, "No SRV records in answers")
                        // responseHistory.add("Host: $host, Result: Failed: No SRV records found")
                        return@withLock null
                    }
                    val srvRecordRaw = srvRecordsRaw.first()
                    val target = srvRecordRaw.target.toString().trimEnd('.')
                    val port = srvRecordRaw.port
                    Log.d(TAG, "Falling back to A record resolution for target: $target")
                    val ip = resolveA(target) ?: run {
                        Log.e(TAG, "A record resolution failed for $target")
                        // responseHistory.add("Host: $host, Result: Failed: No A records resolved for SRV target $target")
                        return@withLock null
                    }
                    val resultPair = Pair(ip, port)
                    cache[host] = resultPair
                    return@withLock resultPair
                }

                // Sort srvRecords by priority and weight
                val sortedSrvRecords = srvRecords.sortedWith(compareBy(
                    { it.srv.priority },
                    { -it.srv.weight }
                ))

                Log.d(TAG, "Sorted SRV Records:")
                sortedSrvRecords.forEachIndexed { index, srvRecord ->
                    Log.d(TAG, "  Record ${index + 1}: target=${srvRecord.srv.target}, port=${srvRecord.port}, priority=${srvRecord.srv.priority}, weight=${srvRecord.srv.weight}")
                }

                // Use the first valid SRV record with an IP
                for (srvRecord in sortedSrvRecords) {
                    for (inetAddressRR in srvRecord.addresses) {
                        val ip = inetAddressRR.inetAddress.hostAddress
                        val port = srvRecord.port
                        Log.d(TAG, "Resolved inetAddress: $ip, port: $port, name: ${srvRecord.name}")
                        val resultPair = Pair(ip, port)
                        cache[host] = resultPair
                        return@withLock resultPair
                    }
                }

                Log.w(TAG, "No valid IP addresses found in SRV records")
                return@withLock null
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving SRV: ${e.message}", e)
                // responseHistory.add("Host: $host, Result: Error: ${e.message}")
                return@withLock null
            }
        }
    }

    suspend fun resolveA(host: String): String? = withContext(Dispatchers.IO) {
        resolutionMutex.withLock {
            try {
                // Try minidns first
                val result: ResolverResult<A> = ResolverApi.INSTANCE.resolve(host, A::class.java)
                if (!result.wasSuccessful()) {
                    Log.e(TAG, "A record resolution failed with response code: ${result.responseCode}")
                    return@withLock null
                }
                val answers: Set<A> = result.answers
                if (answers.isEmpty()) {
                    Log.w(TAG, "No A records found for $host")
                    return@withLock null
                }
                val ip = answers.first().inetAddress.hostAddress
                Log.d(TAG, "Resolved inetAddress: $ip for $host")
                return@withLock ip
            } catch (e: Exception) {
                Log.w(TAG, "minidns A record resolution failed: ${e.message}, falling back to InetAddress")
                // Fallback to Android's InetAddress
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
}

suspend fun fetchFromSrv(host: String): Pair<String, Int>? {
    return DNSResolver().resolveSRV(host)
}