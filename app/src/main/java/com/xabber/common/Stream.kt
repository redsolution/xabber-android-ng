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
//            val history = DNSResolver.getResponseHistory()
            var resolvedIp: String? = null
            var resolvedPort: Int = port
//            for (entry in history.reversed()) {
//                if (entry.contains("Host: $host")) {
//                    if (entry.contains("inetAddress")) {
//                        val lines = entry.split(", ")
//                        for (line in lines) {
//                            if (line.contains("inetAddress")) {
//                                resolvedIp = line.substringAfter("inetAddress=").substringBefore(",").trim()
//                            }
//                            if (line.contains("port")) {
//                                resolvedPort = line.substringAfter("port=").substringBefore(",").trim().toInt()
//                            }
//                        }
//                        break
//                    }
//                }
//            }
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
            socket = Socket(host, port)
            if (socket?.connect() == true) {
                Log.d(TAG, "Socket connected successfully for $remoteAddress:$port")
                val serverResponse = socket!!.read()
                println("Server response: $serverResponse")

                // Send a ping stanza
                socket!!.sendPing("aleksey.bolding@redsolution.com")

                // Read the ping response
                val pingResponse = socket!!.read()
                println("Ping response: $pingResponse")
                socket!!.close()
                return@withContext true
            } else {
                Log.e(TAG, "Socket connection failed for $remoteAddress:$port")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host: ${e.message}", e)
            return@withContext false
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
    }

    fun getSocket(): Socket? {
        return socket
    }
}