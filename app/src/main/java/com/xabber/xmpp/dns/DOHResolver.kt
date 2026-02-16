package com.xabber.xmpp.dns

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.Record
import org.minidns.record.SRV
import org.minidns.record.CNAME
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DohResolver(private val dohUrl: String = "https://cloudflare-dns.com/dns-query") {

    private val TAG = "DohResolver"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun resolveSrvRecords(service: String, protocol: String, domain: String): List<SrvRecord> =
        withContext(Dispatchers.IO) {
            val queryDomain = "_$service._$protocol.$domain"
            val responseBytes = doDohQuery(queryDomain, Record.TYPE.SRV) ?: return@withContext emptyList()
            parseSrvRecords(responseBytes)
        }

    /**
     * Resolve A records for a host, following CNAMEs if necessary.
     */
    suspend fun resolveARecords(host: String): List<String> = withContext(Dispatchers.IO) {
        var currentHost = host
        val visited = mutableSetOf<String>() // prevent loops
        val maxSteps = 5

        repeat(maxSteps) {
            if (currentHost in visited) {
                Log.w(TAG, "CNAME loop detected for $host")
                return@withContext emptyList()
            }
            visited.add(currentHost)

            val responseBytes = doDohQuery(currentHost, Record.TYPE.A) ?: return@withContext emptyList()
            val message = try {
                DnsMessage(responseBytes)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to parse DNS message", e)
                return@withContext emptyList()
            }

            // Check for A records first
            val aRecords = message.answerSection
                .filter { it.type == Record.TYPE.A }
                .map { (it.payload as org.minidns.record.A).inetAddress.hostAddress }
            if (aRecords.isNotEmpty()) {
                Log.d(TAG, "Found ${aRecords.size} A records for $currentHost")
                return@withContext aRecords
            }

            // If no A, look for CNAME
            val cnameRecords = message.answerSection
                .filter { it.type == Record.TYPE.CNAME }
                .map { (it.payload as CNAME).target.toString().removeSuffix(".") }
            if (cnameRecords.isNotEmpty()) {
                val next = cnameRecords.first()
                Log.d(TAG, "Following CNAME from $currentHost to $next")
                currentHost = next
                // Continue loop
            } else {
                // No A and no CNAME – stop
                Log.d(TAG, "No A or CNAME records found for $currentHost")
                return@withContext emptyList()
            }
        }

        Log.w(TAG, "Exceeded max CNAME steps for $host")
        emptyList()
    }

    // Internal: perform the DoH POST request
    private suspend fun doDohQuery(domain: String, type: Record.TYPE): ByteArray? =
        suspendCoroutine { continuation ->
            try {
                val query = DnsMessage.builder()
                    .setQrFlag(false)
                    .setCheckingDisabled(true)
                    .setQuestion(Question(domain, type))
                    .build()

                val requestBody = RequestBody.create(
                    "application/dns-message".toMediaTypeOrNull(),
                    query.toArray()
                )

                val request = Request.Builder()
                    .url(dohUrl)
                    .post(requestBody)
                    .addHeader("Accept", "application/dns-message")
                    .build()

                Log.d(TAG, "Sending DoH query for $domain (type=$type) to $dohUrl")

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "DoH HTTP call failed", e)
                        continuation.resumeWith(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                Log.e(TAG, "DoH HTTP error: ${response.code} ${response.message}")
                                continuation.resume(null)
                                return
                            }
                            val body = response.body?.bytes()
                            if (body == null) {
                                Log.e(TAG, "DoH response body is null")
                            } else {
                                Log.d(TAG, "DoH received ${body.size} bytes")
                            }
                            continuation.resume(body)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Exception in doDohQuery", e)
                continuation.resumeWith(Result.failure(e))
            }
        }

    // Parse SRV records from raw DNS response
    private fun parseSrvRecords(responseBytes: ByteArray): List<SrvRecord> {
        val message = try {
            DnsMessage(responseBytes)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to parse DNS message", e)
            return emptyList()
        }
        if (message.responseCode != DnsMessage.RESPONSE_CODE.NO_ERROR) {
            Log.w(TAG, "DNS response code: ${message.responseCode}")
            return emptyList()
        }

        return message.answerSection
            .filter { it.type == Record.TYPE.SRV }
            .map { answer ->
                val srv = answer.payload as SRV
                SrvRecord(
                    target = srv.target.toString().removeSuffix("."),
                    port = srv.port,
                    priority = srv.priority,
                    weight = srv.weight
                )
            }
    }

    data class SrvRecord(
        val target: String,
        val port: Int,
        val priority: Int,
        val weight: Int
    )
}