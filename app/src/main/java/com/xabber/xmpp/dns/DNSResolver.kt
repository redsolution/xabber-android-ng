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

class DNSResolver {
    private val TAG = "DNSResolver"

    companion object {
        private val responseHistory = mutableListOf<String>()

        fun getResponseHistory(): List<String> {
            return responseHistory.toList()
        }

        fun clearResponseHistory() {
            responseHistory.clear()
        }
    }

    fun resolveSRV(host: String): String {
        try {
            val result: SrvResolverResult =
                ResolverApi.INSTANCE.resolveSrv(SrvService.xmpp_client, SrvProto.tcp, host)
            if (!result.wasSuccessful()) {
                val responseCode: DnsMessage.RESPONSE_CODE = result.responseCode
                Log.e(TAG, "SRV resolution failed with response code: $responseCode")
                val response = "Failed: Response code $responseCode"
                responseHistory.add("Host: $host, Result: $response")
                return response
            }
            // Log raw SRV records and extract hostname
            val rawAnswers = result.answers
            Log.d(TAG, "Raw SRV answers: $rawAnswers")
            rawAnswers.filterIsInstance<SRV>().forEach { srv ->
                Log.d(TAG, "SRV: target=${srv.target}, port=${srv.port}, priority=${srv.priority}, weight=${srv.weight}")
            }

            // Extract hostname from the first SRV record, if available
            val hostName: String = rawAnswers.filterIsInstance<SRV>()
                .firstOrNull()?.target?.toString() ?: "No SRV target found"
            Log.d(TAG, "Extracted hostName: $hostName")

            val srvRecords: List<SrvResolverResult.ResolvedSrvRecord> = result.sortedSrvResolvedAddresses
            Log.d(TAG, "srvRecords size: ${srvRecords.size}")
            if (srvRecords.isEmpty()) {
                Log.w(TAG, "No resolved SRV records in sortedSrvResolvedAddresses")
                // Fallback to resolving A records for SRV target
                val srvRecordsRaw = rawAnswers.filterIsInstance<SRV>()
                if (srvRecordsRaw.isEmpty()) {
                    Log.w(TAG, "No SRV records in answers")
                    val response = "Failed: No SRV records found"
                    responseHistory.add("Host: $host, Result: $response")
                    return response
                }
                // Use the first SRV record's target and port
                val srvRecordRaw = srvRecordsRaw.first()
                val target = srvRecordRaw.target.toString()
                val port = srvRecordRaw.port
                Log.d(TAG, "Falling back to A record resolution for target: $target")
                val aResult = resolveA(target)
                if (aResult != fin) {
                    Log.e(TAG, "A record resolution failed for $target: $aResult")
                    val response = "Failed: No A records resolved for SRV target $target"
                    responseHistory.add("Host: $host, Result: $response")
                    return response
                } else Log.d(TAG, "A record resolution for $target: $aResult")
            }

            // Sort srvRecords by priority (ascending) and weight (descending)
            val sortedSrvRecords = srvRecords.sortedWith(compareBy(
                { it.srv.priority }, // Sort by priority first (lower is better)
                { -it.srv.weight }   // Then by weight (higher is better, hence negative)
            ))

            // Log sorted SRV records in human-readable format
            Log.d(TAG, "Sorted SRV Records:")
            sortedSrvRecords.forEachIndexed { index, srvRecord ->
                Log.d(TAG, "  Record ${index + 1}: target=${srvRecord.srv.target}, port=${srvRecord.port}, priority=${srvRecord.srv.priority}, weight=${srvRecord.srv.weight}")
            }

            // Iterate over sorted records
            for (srvRecord in sortedSrvRecords) {
                for (inetAddressRR in srvRecord.addresses) {
                    val inetAddress: InetAddress = inetAddressRR.inetAddress
                    val port: Int = srvRecord.port
                    val name: String = srvRecord.name.toString()
                    val aResult: String = srvRecord.srv.toString()
                    Log.d(TAG, "Resolved inetAddress: $inetAddress, port: $port, name: $name, aResult: $aResult")
                    Log.d(TAG, "priority: ${srvRecord.srv.priority}, weight: ${srvRecord.srv.weight}")
                }
            }
            val response = "Success"
            responseHistory.add("Host: $host, Result: $response")
            return response
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving SRV: ${e.message}", e)
            val response = "Error: ${e.message}"
            responseHistory.add("Host: $host, Result: $response")
            return response
        }
    }

    var fin: String = ""

     fun resolveA(host: String): String {
        try {
            val result: ResolverResult<A> =
                ResolverApi.INSTANCE.resolve(host, A::class.java)
            if (!result.wasSuccessful()) {
                val responseCode: DnsMessage.RESPONSE_CODE = result.responseCode
                Log.e(TAG, "A record resolution failed with response code: $responseCode")
                return "Failed: Response code $responseCode"
            }
            val answers: Set<A> = result.answers
            if (answers.isEmpty()) {
                Log.w(TAG, "No A records found")
                return "Failed: No A records found"
            }

            for (a in answers) {
                val inetAddress: InetAddress = a.inetAddress
                Log.d(TAG, "Resolved inetAddress: $inetAddress")
                // Do something with the InetAddress, e.g., connect to.
            }
            fin = answers.toString()
            return answers.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving A record: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }
}

fun fetchFromSrv(host: String): String {
    return DNSResolver().resolveSRV(host)
}