package com.xabber.common

import android.util.Log
import com.xabber.xmpp.dns.DNSResolver
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

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
                StreamState.STREAM_OPEN -> runBlocking(Dispatchers.IO) { onStreamOpen() }
                StreamState.START_TLS -> runBlocking(Dispatchers.IO) { onStartTls() }
                StreamState.PROCEED -> runBlocking(Dispatchers.IO) { onProceed() }
                StreamState.START_AUTH -> runBlocking(Dispatchers.IO) { onStartAuth() }
                StreamState.PROCESS_AUTH -> runBlocking(Dispatchers.IO) { onProcessAuth() }
                StreamState.AUTH_SUCCESS -> runBlocking(Dispatchers.IO) { onAuthSuccess() }
                StreamState.AUTH_FAILED -> runBlocking(Dispatchers.IO) { onAuthFailed() }
                StreamState.BINDING -> runBlocking(Dispatchers.IO) { onBinding() }
                StreamState.CONNECTED -> runBlocking(Dispatchers.IO) { onConnected() }
            }
        }
    private val TAG = "Stream"
    private val proceedChannel = Channel<String?>(1)

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
            socket?.setMessageCallback { message ->
                CoroutineScope(Dispatchers.IO).launch {
                    if (message.contains("<proceed")) {
                        proceedChannel.send(message)
                    }
                    // Handle other messages as needed
                }
            }
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
                }
                features.mechanisms?.mechanism?.let { mechanisms ->
                    Log.d(TAG, "Supported SASL mechanisms: $mechanisms")
                    if (mechanisms.contains("PLAIN")) {
                        Log.d(TAG, "PLAIN authentication is supported")
                        state = StreamState.START_AUTH
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host: $e")
            socket?.close()
            state = StreamState.NOT_CONNECTING
            false
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        state = StreamState.NOT_CONNECTING
        proceedChannel.close()
        Log.d(TAG, "Stream closed for $jid")
    }

    fun logout(jid: String) {
        if (this.jid == jid) {
            runBlocking(Dispatchers.IO) {
                close()
            }
        }
    }

    fun getSocket(): Socket? {
        return socket
    }

    // State handler functions
    open fun onNotConnecting() {}
    open suspend fun onStreamOpen() {}
    open suspend fun onStartTls() {
        if (socket == null) {
            Log.e(TAG, "Cannot send starttls: Socket is null")
            return
        }
        if (!socket!!.write("<starttls xmlns='urn:xml:namespace:xmpp-tls'/>\n")) {
            Log.e(TAG, "Failed to send starttls due to write error")
            return
        }
        if (socket == null || socket!!.getSocket() == null) {
            Log.e(TAG, "Socket became null or invalid before reading proceed")
            return
        }
        try {
            // Wait for proceed message from the read loop
            val proceed = withTimeoutOrNull(5000) {
                proceedChannel.receive()
            }
            Log.w(TAG, "Proceed: $proceed")
            if (proceed != null && proceed.contains("<proceed")) {
                Log.d(TAG, "Received proceed, upgrading to TLS")
                if (socket?.upgradeToTls() == true) {
                    state = StreamState.PROCEED
                    val response = socket?.initiateXmppStream(socket!!, host, jid)
                    if (response != null) {
                        Log.d(TAG, "New stream initiated over TLS: $response")
                        state = StreamState.STREAM_OPEN
                        response.features?.let { features ->
                            Log.d(TAG, "New stream features: $features")
                            if (features.mechanisms?.mechanism?.contains("PLAIN") == true) {
                                state = StreamState.START_AUTH
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to restart stream over TLS")
                    }
                } else {
                    Log.e(TAG, "Failed to upgrade to TLS")
                }
            } else {
                Log.e(TAG, "Failed to receive proceed: $proceed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for proceed: $e")
        }
    }
    open suspend fun onProceed() {}
    open suspend fun onStartAuth() {
        // TODO: Implement SASL PLAIN authentication
    }
    open suspend fun onProcessAuth() {}
    open suspend fun onAuthSuccess() {}
    open suspend fun onAuthFailed() {}
    open suspend fun onBinding() {}
    open suspend fun onConnected() {}
}