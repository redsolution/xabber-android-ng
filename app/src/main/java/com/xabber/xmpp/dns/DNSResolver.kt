package com.xabber.xmpp.dns

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress

// Reusable function to perform the HTTP request with custom DNS
suspend fun performDnsResolvedRequest(): String {
    val okHttpClient = OkHttpClient.Builder()
        .dns(DNSResolver(mapOf("example.com" to "23.55.44.79")))
        .build()

    val client = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
    }

    return try {
        val response = client.get("http://example.com")
        val responseBody = response.bodyAsText()
        Log.d("DNSResolver", "Response from example.com: $responseBody")
        responseBody // Return the response for further use
    } catch (e: Exception) {
        Log.e("DNSResolver", "Error fetching example.com: ${e.message}", e)
        "Error: ${e.message}" // Return error message
    } finally {
        client.close() // Ensure the client is closed to avoid resource leaks
    }
}

class DNSResolver(private val map: Map<String, String>) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val ip = map[hostname]
        return if (ip != null) {
            Log.d("DNSResolver", "Resolved $hostname to $ip")
            listOf(Inet4Address.getByName(ip))
        } else {
            Log.d("DNSResolver", "Using system DNS for $hostname")
            Dns.Companion.SYSTEM.lookup(hostname)
        }
    }
}