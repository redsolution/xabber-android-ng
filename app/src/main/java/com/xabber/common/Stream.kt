package com.xabber.common

import android.util.Log
import com.xabber.xmpp.dns.DNSResolver
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xabber.common.Socket


enum class StreamState {
    NOT_CONNECTING,
    STREAM_OPEN,
    START_TLS,
    PROCEED,
    START_AUTH,
    PROCESS_AUTH,
    AUTH_SUCCESS,
    AUTH_FAILED,
    BINDING,
    CONNECTED
}

class Stream {
    @PrimaryKey
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var remoteAddress: String = ""
    private var socket: Socket? = null
    private var state: StreamState = StreamState.NOT_CONNECTING
        set(value) {
            field = value
            Log.d(TAG, "Transitioned to state: $value")
            when (value) {
                StreamState.NOT_CONNECTING -> onNotConnecting()
                StreamState.STREAM_OPEN -> onStreamOpen()
                StreamState.START_TLS -> onStartTls()
                StreamState.PROCEED -> onProceed()
                StreamState.START_AUTH -> onStartAuth()
                StreamState.PROCESS_AUTH -> onProcessAuth()
                StreamState.AUTH_SUCCESS -> onAuthSuccess()
                StreamState.AUTH_FAILED -> onAuthFailed()
                StreamState.BINDING -> onBinding()
                StreamState.CONNECTED -> onConnected()
            }
        }
    private val TAG = "Stream"

    constructor(jid: String, port: Int? = null) {
        this.jid = jid
        this.port = port ?: 5222
        this.host = extractHostFromJid(jid)
        this.state = StreamState.NOT_CONNECTING
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
            Log.e(TAG, "Error extracting host from JID: $e")
            return jid
        }
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            state = StreamState.NOT_CONNECTING
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
            val response = socket?.initiateXmppStream(socket!!, host, jid)
            if (response == null) {
                Log.e(TAG, "Failed to initiate XMPP stream for $jid")
                socket?.close()
                return@withContext false
            }
            Log.d(TAG, "XMPP stream initiated successfully. Server response: $response")
            state = StreamState.STREAM_OPEN

            // Access stream features
            response.features?.let { features ->
                Log.d(TAG, "Stream features: $features")
                if (features.starttls?.present == true) {
                    Log.d(TAG, "STARTTLS is supported")
                    state = StreamState.START_TLS
                    // TODO: Implement STARTTLS
                }
                features.mechanisms?.mechanism?.let { mechanisms ->
                    Log.d(TAG, "Supported SASL mechanisms: $mechanisms")
                    if (mechanisms.contains("PLAIN")) {
                        Log.d(TAG, "PLAIN authentication is supported")
                        state = StreamState.START_AUTH
                        // TODO: Implement SASL PLAIN
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host: $e")
            socket?.close()
            false
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        state = StreamState.NOT_CONNECTING
        Log.d(TAG, "Stream closed for $jid")
    }

    fun getSocket(): Socket? {
        return socket
    }

    // State handler functions
    open fun onNotConnecting() {}
    open fun onStreamOpen() {}
    open fun onStartTls() {}
    open fun onProceed() {}
    open fun onStartAuth() {}
    open fun onProcessAuth() {}
    open fun onAuthSuccess() {}
    open fun onAuthFailed() {}
    open fun onBinding() {}
    open fun onConnected() {}
}
