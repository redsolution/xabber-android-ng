package com.xabber.common

import android.util.Log
import com.xabber.xmpp.dns.DNSResolver
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import io.ktor.network.sockets.isClosed

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
    private val connectionLock = Any()
    private var isConnecting = false
    private val messageCallbackChannel = Channel<String>(Channel.UNLIMITED)
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
        synchronized(connectionLock) {
            if (isConnecting) {
                Log.w(TAG, "Connect already in progress for $jid, ignoring")
                return@withContext false
            }
            isConnecting = true
        }
        try {
            state = StreamState.NOT_CONNECTING
            val resolver = DNSResolver()
            val result = resolver.resolveSRV(host)
            if (result == null) {
                Log.e(TAG, "DNS resolution failed for host $host")
                return@withContext false
            }
            this@Stream.remoteAddress = result.first
            this@Stream.port = result.second
            Log.d(TAG, "Resolved IP: $remoteAddress, Port: $port")
            socket?.close()
            socket = Socket(remoteAddress, port)
            socket?.setMessageCallback { message ->
                CoroutineScope(Dispatchers.IO).launch {
                    Log.d(TAG, "Received message via callback: $message")
                    messageCallbackChannel.send(message)
                    handleIncomingMessage(message)
                }
            }
            if (socket?.connect(host, port) != true) {
                Log.e(TAG, "Socket connection failed for $remoteAddress:$port")
                socket?.close()
                socket = null
                return@withContext false
            }
            Log.d(TAG, "Socket connected successfully for $remoteAddress:$port")
            socket?.initiateXmppStream(socket!!, host, jid)
            Log.d(TAG, "XMPP stream initiation started, waiting for server response")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host: $e")
            socket?.close()
            socket = null
            state = StreamState.NOT_CONNECTING
            false
        } finally {
            synchronized(connectionLock) {
                isConnecting = false
            }
        }
    }

    private suspend fun handleIncomingMessage(message: String) {
        try {
            Log.d(TAG, "Handling incoming message: $message")
            when {
                message.contains("<stream:features>") -> {
                    Log.d(TAG, "Received stream features")
                    val response = socket?.parseStreamResponse(message)
                    if (response == null) {
                        Log.e(TAG, "Failed to parse stream features")
                        state = StreamState.NOT_CONNECTING
                        return
                    }
                    if (state == StreamState.NOT_CONNECTING) {
                        Log.d(TAG, "Initial stream response received")
                        state = StreamState.STREAM_OPEN
                    }
                    response.features?.let { features ->
                        Log.d(TAG, "Stream features: $features")
                        if (features.starttls?.present == true) {
                            Log.d(TAG, "STARTTLS is supported")
                            state = StreamState.START_TLS
                        } else if (features.mechanisms?.mechanism?.contains("PLAIN") == true) {
                            Log.d(TAG, "PLAIN authentication is supported")
                            state = StreamState.START_AUTH
                        } else {
                            Log.w(TAG, "No supported features found")
                            state = StreamState.NOT_CONNECTING
                        }
                    }
                }
                message.contains("<proceed") -> {
                    Log.d(TAG, "Received proceed for STARTTLS")
                    proceedChannel.send(message)
                }
                message.contains("<iq") -> {
                    Log.d(TAG, "Received IQ stanza")
                }
                message.contains("<message") -> {
                    Log.d(TAG, "Received message stanza")
                }
                message.contains("<presence") -> {
                    Log.d(TAG, "Received presence stanza")
                }
                message.contains("<stream:error") -> {
                    Log.e(TAG, "Received stream error: $message")
                    state = StreamState.NOT_CONNECTING
                }
                message.contains("</stream:stream>") -> {
                    Log.w(TAG, "Received stream termination")
                    state = StreamState.NOT_CONNECTING
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: $e")
            state = StreamState.NOT_CONNECTING
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        state = StreamState.NOT_CONNECTING
        proceedChannel.close()
        messageCallbackChannel.close()
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
    open fun onNotConnecting() {}
    open suspend fun onStreamOpen() {}
    open suspend fun onStartTls() {
        if (socket == null || socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot send starttls: Socket is null or closed")
            state = StreamState.NOT_CONNECTING
            return
        }
        try {
            Log.d(TAG, "Sending starttls command")
            if (!socket!!.write("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")) {
                Log.e(TAG, "Failed to send starttls due to write error")
                state = StreamState.NOT_CONNECTING
                return
            }
            Log.d(TAG, "Sent starttls, waiting for proceed")
            val proceed = withTimeoutOrNull(30000) {
                proceedChannel.receive()
            }
            Log.w(TAG, "Proceed: $proceed")
            if (proceed != null && proceed.contains("<proceed")) {
                Log.d(TAG, "Received proceed, waiting for server to prepare TLS")
                delay(1000) // Увеличить задержку до 1 секунды
                Log.d(TAG, "Preparing for TLS upgrade")
//                socket?.prepareForTlsUpgrade()
                Log.d(TAG, "Initiating TLS upgrade")
                if (socket?.upgradeToTls() == true) {
                    Log.d(TAG, "TLS upgrade successful, initiating new stream")
                    state = StreamState.PROCEED
                    socket?.initiateXmppStream(socket!!, host, jid)
                    Log.d(TAG, "New stream initiated over TLS, awaiting response")
                } else {
                    Log.e(TAG, "Failed to upgrade to TLS")
                    state = StreamState.NOT_CONNECTING
                }
            } else {
                Log.e(TAG, "Failed to receive proceed within 30 seconds")
                state = StreamState.NOT_CONNECTING
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during TLS upgrade: $e")
            state = StreamState.NOT_CONNECTING
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
    open suspend fun onConnected() {
        socket?.scope?.launch {
            while (state == StreamState.CONNECTED && socket?.getSocket()?.isClosed == false) {
                try {
                    if (socket?.sendPing(jid) == true) {
                        Log.d(TAG, "Sent ping to $jid")
                    } else {
                        Log.w(TAG, "Failed to send ping")
                        state = StreamState.NOT_CONNECTING
                        break
                    }
                    delay(30000)
                } catch (e: Exception) {
                    Log.e(TAG, "Ping error: $e")
                    state = StreamState.NOT_CONNECTING
                    break
                }
            }
            Log.d(TAG, "Ping loop terminated for $jid")
        }
    }
}