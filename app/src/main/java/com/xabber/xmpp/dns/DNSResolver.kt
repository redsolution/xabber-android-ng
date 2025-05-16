package com.xabber.xmpp.dns

import android.util.Log
import org.minidns.dnsmessage.DnsMessage
import org.minidns.hla.DnssecResolverApi
import org.minidns.hla.ResolverApi
import org.minidns.hla.ResolverResult
import org.minidns.hla.SrvResolverResult
import org.minidns.hla.srv.SrvProto
import org.minidns.hla.srv.SrvService
import org.minidns.hla.srv.SrvType
import org.minidns.record.A
import org.minidns.record.SRV
import java.net.InetAddress

class DNSResolver {
    private val TAG = "DNSResolver"

    fun resolveSRV(host : String): String {
        try {
            val result: SrvResolverResult =
                ResolverApi.INSTANCE.resolveSrv(SrvService.xmpp_client, SrvProto.tcp, host)
            if (!result.wasSuccessful()) {
                val responseCode: DnsMessage.RESPONSE_CODE = result.responseCode
                Log.e(TAG, "SRV resolution failed with response code: $responseCode")
                return "Failed: Response code $responseCode"
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
                Log.w(TAG, "No resolved SRV records in sortedSrvResolvedAddresses")
                // Fallback to resolving A records for SRV target
                val srvRecordsRaw = rawAnswers.filterIsInstance<SRV>()
                if (srvRecordsRaw.isEmpty()) {
                    Log.w(TAG, "No SRV records in answers")
                    return "Failed: No SRV records found"
                }
                // Use the first SRV record's target and port
                val srvRecordRaw = srvRecordsRaw.first()
                val target = srvRecordRaw.target.toString()
                val port = srvRecordRaw.port
                Log.d(TAG, "Falling back to A record resolution for target: $target")
                val aResult = resolveA(target)
                if (aResult != "Success") {
                    Log.e(TAG, "A record resolution failed for $target: $aResult")
                    return "Failed: No A records resolved for SRV target $target"
                } else Log.d(TAG, "A record resolution for $target: $aResult")

            // Note: IP addresses are logged in resolveA

            for (srvRecord in srvRecords) {
                for (inetAddressRR in srvRecord.addresses) {
                    val inetAddress: InetAddress = inetAddressRR.inetAddress
                    val port: Int = srvRecord.port
                    val name: String = srvRecord.name.toString()
                    val aResult: String = srvRecord.srv.toString()
                    Log.d(TAG, "Resolved inetAddress: $inetAddress, port: $port, name: $name, aResult: $aResult")

                }

            }
            return "Success"
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving SRV: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }
    private fun resolveA(host: String): String {
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

            return answers.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving A record: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }
}

fun fetchFromSrv(): String {
     return DNSResolver().resolveSRV("redsolution.com")

}


