package com.xabber.common

import android.util.Log
import com.xabber.xmpp.dns.DNSResolver
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Stream {
    @PrimaryKey
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var remoteAddress: String = ""
    private var socket: Socket? = null
    private val TAG = "Stream"

    constructor(jid: String, port: Int? = null) {
        this.jid = jid
        this.port = port ?: 5222
        this.host = extractHostFromJid(jid)
    }

    private fun extractHostFromJid(jid: String): String {
        try {
            val parts = jid.split("@")
            if (parts.size > 1) {
                return parts[1].split("/").first()
            }
            Log.w(TAG, "Invalid JID format: $jid, using JID as host")
            return jid
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting host from JID: ${e.message}", e)
            return jid
        }
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = DNSResolver()
            val result = resolver.resolveSRV(host)
            if (result != "Success") {
                Log.e(TAG, "DNS resolution failed for host $host: $result")
                return@withContext false
            }
            var resolvedIp: String? = null
            var resolvedPort: Int = port
            if (resolvedIp == null) {
                Log.w(TAG, "No SRV records with IP found, falling back to A record resolution")
                val aResult = resolver.resolveA(host)
                if (aResult.startsWith("Failed") || aResult.startsWith("Error")) {
                    Log.e(TAG, "A record resolution failed: $aResult")
                    return@withContext false
                }
                resolvedIp = aResult.substringAfter("[").substringBefore("]").trim()
            }
            if (resolvedIp.isEmpty()) {
                Log.e(TAG, "No IP address resolved for host $host")
                return@withContext false
            }
            this@Stream.remoteAddress = resolvedIp
            this@Stream.port = resolvedPort
            Log.d(TAG, "Resolved IP: $remoteAddress, Port: $port")
            socket = Socket(remoteAddress, port)
            if (socket?.connect() != true) {
                Log.e(TAG, "Socket connection failed for $remoteAddress:$port")
                return@withContext false
            }
            Log.d(TAG, "Socket connected successfully for $remoteAddress:$port")

            // Initiate XMPP stream
            val serverResponse = socket?.initiateXmppStream(socket!!, host, jid)
            if (serverResponse == null) {
                Log.e(TAG, "Failed to initiate XMPP stream for $jid")
                socket?.close()
                return@withContext false
            }
            Log.d(TAG, "XMPP stream initiated successfully. Server response: $serverResponse")

            // Example: Send a ping stanza after stream initiation
            socket?.sendPing(jid)?.let { pingSent ->
                if (pingSent) {
                    val pingResponse = socket?.read()
                    Log.d(TAG, "Ping response: $pingResponse")
                } else {
                    Log.w(TAG, "Failed to send ping for $jid")
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host: ${e.message}", e)
            socket?.close()
            false
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        Log.d(TAG, "Stream closed for $jid")
    }

    fun getSocket(): Socket? {
        return socket
    }
}