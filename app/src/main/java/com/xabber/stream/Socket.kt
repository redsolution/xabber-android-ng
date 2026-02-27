package com.xabber.stream

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.onboarding.util.PasswordStorageHelper
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.io.IOException
import java.nio.BufferOverflowException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.*
import kotlin.coroutines.cancellation.CancellationException

@Serializable
@XmlSerialName("stream", "http://etherx.jabber.org/streams", "stream")
data class StreamResponse(
    val id: String? = null,
    val version: String? = null,
    val from: String? = null,
    val to: String? = null,
    val xmlLang: String? = null,
    val xmlns: String? = null,
    val xmlnsStream: String? = null,
    val features: StreamFeatures? = null
)

@Serializable
@XmlSerialName("features", "http://etherx.jabber.org/streams", "stream")
data class StreamFeatures(
    @XmlElement(true)
    @XmlSerialName("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl", "")
    val mechanisms: Mechanisms? = null,
    @XmlElement(true)
    @XmlSerialName("starttls", "urn:ietf:params:xml:ns:xmpp-tls", "")
    val starttls: StartTls? = null,
    @XmlElement(true)
    @XmlSerialName("proxy", "urn:xabber:ws:proxy", "")
    val proxy: Proxy? = null,
    @XmlElement(true)
    @XmlSerialName("devices", "https://xabber.com/protocol/devices", "")
    val devices: Devices? = null,
    @XmlElement(true)
    @XmlSerialName("bind", "urn:ietf:params:xml:ns:xmpp-bind", "")
    val bind: Bind? = null
)

@Serializable
data class Mechanisms(
    @XmlElement(true)
    @XmlSerialName("mechanism", "urn:ietf:params:xml:ns:xmpp-sasl", "")
    val mechanism: List<String?> = emptyList()
)

@Serializable
data class StartTls(
    @XmlElement(false)
    val present: Boolean = true
)

@Serializable
data class Proxy(
    @XmlElement(false)
    val present: Boolean = true
)

@Serializable
data class Devices(
    @XmlElement(false)
    val present: Boolean = true
)

@Serializable
data class Bind(
    @XmlElement(false)
    val present: Boolean = true
)

class Socket(private val host: String, private val port: Int) {
    private val KTOR_LOGGER = KtorSimpleLogger("io.ktor.network")
    private var socket: io.ktor.network.sockets.Socket? = null
    private var reader: ByteReadChannel? = null
    private var writer: ByteWriteChannel? = null
    private val selectorManager = SelectorManager(Dispatchers.Default)
    var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "Socket_nging"
    private val tagPing = "SOCKET PING"
    private val sslContext = SSLContext.getInstance("TLS")
    private lateinit var sslEngine: SSLEngine
    private val writeMutex = Mutex()
    private lateinit var appBuffer: ByteBuffer
    private lateinit var packetBuffer: ByteBuffer
    private lateinit var accumulatedData: ByteBuffer
    private var messageCallback: ((String) -> Unit)? = null
    private var proceedChannel = Channel<String?>(1)
    private var tlsDataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var tlsHandshaking = false
    private var isReadingLoopActive = false
    private var domain: String = host
    private var onReadLoopError: (() -> Unit)? = null   // Новый callback
    private var isReadLoopErrorFired = false

    private var readingLoopJob: Job? = null
    private var keepAliveJob: Job? = null
    @Volatile
    private var lastDataReceivedTime = System.currentTimeMillis()
    private val KEEPALIVE_INTERVAL_MS = 10_000L    // how often to send a keepalive ping
    private val DEAD_CONNECTION_MS    = 120_000L  // silence this long → declare connection dead

    val isClosed: Boolean
        get() = socket?.isClosed != false || writer?.isClosedForWrite != false

    private var userJid: String = ""

    fun setUserJid(jid: String) {
        this.userJid = jid
    }

    fun setOnReadLoopError(callback: () -> Unit) {
        onReadLoopError = callback
    }


    fun setMessageCallback(callback: (String) -> Unit) {
        messageCallback = callback
    }

    fun setDomain(domain: String) {
        this.domain = domain
    }

    fun startKeepAlive() {
        keepAliveJob?.cancel()
        lastDataReceivedTime = System.currentTimeMillis()
        keepAliveJob = scope.launch {
            while (scope.isActive) {
                delay(KEEPALIVE_INTERVAL_MS)

                if (socket?.isClosed != false || writer?.isClosedForWrite != false) break

                // If the TCP layer has been completely silent for too long the remote
                // side is likely gone (network drop without a FIN/RST).
                val silentMs = System.currentTimeMillis() - lastDataReceivedTime
                if (silentMs > DEAD_CONNECTION_MS) {
                    Log.w(tagPing, "No data received for ${silentMs}ms — declaring connection dead")
                    closeInternal()
                    if (!isReadLoopErrorFired) {
                        isReadLoopErrorFired = true
                        onReadLoopError?.invoke()
                    }
                    break
                }

                // Send a single whitespace character.  XMPP servers silently ignore
                // whitespace between stanzas (RFC 6120 §4.6), so this carries zero
                // protocol overhead.  A write failure means the socket is dead.
                try {
                    if (sendKeepalivePing()) {
                        Log.v(tagPing, "Keepalive ping sent to $domain (silent for ${silentMs}ms)")
                    } else {
                        Log.w(tagPing, "Keepalive ping write failed — triggering reconnect")
                        if (!isReadLoopErrorFired) {
                            isReadLoopErrorFired = true
                            onReadLoopError?.invoke()
                        }
                        break
                    }
                } catch (e: Exception) {
                    Log.w(tagPing, "Keepalive ping exception: ${e.message}")
                    if (!isReadLoopErrorFired) {
                        isReadLoopErrorFired = true
                        onReadLoopError?.invoke()
                    }
                    break
                }
            }
        }
    }

    private suspend fun sendKeepalivePing(): Boolean {
        val stanza = "<iq type='get' to='$domain' id='ka-${System.currentTimeMillis()}'><ping xmlns='urn:xmpp:ping'/></iq>"
        return write(stanza)
    }

    suspend fun connect(host: String, port: Int, alternateEndpoints: List<Pair<String, Int>> = emptyList()): Boolean = withContext(Dispatchers.IO) {
        val endpoints = listOf(Pair(host, port)) + alternateEndpoints
        val maxAttemptsPerEndpoint = 3
        val connectTimeoutMs = 10000L
        val retryDelayMs = 500L

        coroutineScope {
            endpoints.map { (targetHost, targetPort) ->
                async {
                    var attempts = 0
                    while (attempts < maxAttemptsPerEndpoint) {
                        attempts++
                        Log.d(TAG, "Connection attempt $attempts of $maxAttemptsPerEndpoint to $targetHost:$targetPort")
                        try {
                            val tcp = aSocket(selectorManager).tcp()
                            val connection = withTimeout(connectTimeoutMs) {
                                tcp.connect(targetHost, targetPort) {
                                    noDelay = true
                                    keepAlive = true
                                }
                            }
                            socket = connection
                            writer = socket?.openWriteChannel(autoFlush = true)
                            reader = socket?.openReadChannel()
                            if (reader == null || writer == null) {
                                Log.e(TAG, "Failed to initialize reader or writer channels for $targetHost:$targetPort")
                                closeInternal()
                                return@async false
                            }
                            startReadingLoop()
                            Log.d(TAG, "TCP connection established for $targetHost:$targetPort")
                            return@async true
                        } catch (e: TimeoutCancellationException) {
                            Log.e(TAG, "Connection attempt $attempts to $targetHost:$targetPort timed out after ${connectTimeoutMs}ms")
                            closeInternal()
                            if (attempts < maxAttemptsPerEndpoint) {
                                Log.d(TAG, "Retrying after ${retryDelayMs}ms")
                                delay(retryDelayMs)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during connection attempt $attempts to $targetHost:$targetPort: ${e.message}", e)
                            closeInternal()
                            if (attempts < maxAttemptsPerEndpoint) {
                                Log.d(TAG, "Retrying after ${retryDelayMs}ms")
                                delay(retryDelayMs)
                            }
                        }
                    }
                    Log.e(TAG, "All $maxAttemptsPerEndpoint attempts failed for $targetHost:$targetPort")
                    false
                }
            }.firstOrNull { it.await() } != null
        }
    }

    private fun startReadingLoop() {
        if (isReadingLoopActive) {
            Log.d(TAG, "Reading loop already active, skipping")
            return
        }
        isReadingLoopActive = true
        readingLoopJob = scope.launch {
            try {
                startReadLoop()
            } catch (e: Throwable) {
                Log.e(TAG, "Reading loop crashed: ${e.message}", e)
                if (!isReadLoopErrorFired) {
                    isReadLoopErrorFired = true
                    onReadLoopError?.invoke()
                }
                closeInternal()
            } finally {
                isReadingLoopActive = false
                readingLoopJob = null
                Log.d(TAG, "Reading loop coroutine terminated")
            }
        }
        Log.d(TAG, "Reading loop coroutine started")
    }

    suspend fun initiateStartTls(): Boolean = withContext(Dispatchers.IO) {
        try {
            writer?.writeStringUtf8("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")
            writer?.flush()
            Log.d(TAG, "Sent STARTTLS command")

            val proceed = withTimeoutOrNull(10000) {
                proceedChannel.receive()
            }
            if (proceed == null || !proceed.contains("<proceed")) {
                Log.e(TAG, "Failed to receive <proceed> within 10 seconds or invalid response: $proceed")
                closeInternal()
                return@withContext false
            }
            Log.d(TAG, "STARTTLS negotiation successful, received <proceed>")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error during STARTTLS initiation: ${e.message}", e)
            closeInternal()
            return@withContext false
        }
    }

    suspend fun upgradeToTls(): Boolean = withContext(Dispatchers.IO) {
        try {
            tlsHandshaking = true
            if (tlsDataChannel.isClosedForSend || tlsDataChannel.isClosedForReceive) {
                Log.d(TAG, "Reinitializing tlsDataChannel")
                tlsDataChannel = Channel<ByteArray>(Channel.UNLIMITED)
            }
            if (proceedChannel.isClosedForSend || proceedChannel.isClosedForReceive) {
                Log.d(TAG, "Reinitializing proceedChannel")
                proceedChannel = Channel<String?>(1)
            }

            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain == null || chain.isEmpty()) {
                        throw CertificateException("No server certificate provided")
                    }
                    val serverCert = chain[0]
                    val sha256 = MessageDigest.getInstance("SHA-256")
                    val fingerprint = sha256.digest(serverCert.encoded).joinToString("") { "%02x".format(it) }
                    Log.d(TAG, "Server certificate fingerprint: $fingerprint")
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            sslContext.init(null, arrayOf(trustManager), SecureRandom())
            sslEngine = sslContext.createSSLEngine(host, port)
            appBuffer = ByteBuffer.allocate(sslEngine.session.applicationBufferSize)
            packetBuffer = ByteBuffer.allocate(sslEngine.session.packetBufferSize * 2)
            accumulatedData = ByteBuffer.allocate(1048576)

            sslEngine.useClientMode = true
            sslEngine.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
            val preferredCipherSuites = listOf(
                "TLS_AES_128_GCM_SHA256",
                "TLS_AES_256_GCM_SHA384"
            )
            val supportedCipherSuites = sslEngine.supportedCipherSuites.toList()
            val enabledCipherSuites = preferredCipherSuites.filter { it in supportedCipherSuites }.toTypedArray()
            if (enabledCipherSuites.isEmpty()) {
                Log.e(TAG, "No supported cipher suites available")
                closeInternal()
                return@withContext false
            }
            sslEngine.enabledCipherSuites = enabledCipherSuites
            Log.d(TAG, "SSLEngine initialized with protocols: ${sslEngine.enabledProtocols.joinToString()}")
            Log.d(TAG, "SSLEngine initialized with cipher suites: ${enabledCipherSuites.joinToString()}")

            sslEngine.beginHandshake()
            Log.d(TAG, "TLS initialization completed")

            val handshakeTimeoutMs = 8000L
            var lastStatus: SSLEngineResult.Status? = null
            var accumulatedBytes = 0
            var readRetries = 0
            val maxReadRetries = 10

            withTimeoutOrNull(handshakeTimeoutMs) {
                while (sslEngine.handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                    sslEngine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    Log.d(TAG, "Handshake loop iteration, status=${sslEngine.handshakeStatus}")
                    when (sslEngine.handshakeStatus) {
                        SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                            Log.d(TAG, "Entering NEED_WRAP state at ${System.currentTimeMillis()}")
                            packetBuffer.clear()
                            var result: SSLEngineResult
                            try {
                                result = sslEngine.wrap(appBuffer, packetBuffer)
                            } catch (e: SSLException) {
                                if (e.message?.contains("buffer overflow") == true) {
                                    Log.w(TAG, "Buffer overflow during wrap, increasing packetBuffer size")
                                    packetBuffer = ByteBuffer.allocate(packetBuffer.capacity() * 2)
                                    packetBuffer.clear()
                                    result = sslEngine.wrap(appBuffer, packetBuffer)
                                } else {
                                    throw e
                                }
                            }
                            Log.d(TAG, "Wrap result: status=${result.status}, bytesProduced=${result.bytesProduced()}, handshakeStatus=${result.handshakeStatus}")
                            packetBuffer.flip()
                            if (result.bytesProduced() > 0) {
                                var chunkSize = 4096
                                while (packetBuffer.hasRemaining()) {
                                    val currentChunkSize = minOf(packetBuffer.remaining(), chunkSize)
                                    val chunk = ByteArray(currentChunkSize)
                                    packetBuffer.get(chunk)
                                    try {
                                        writer?.write { buffer ->
                                            buffer.put(chunk)
                                            buffer.remaining()
                                        }
                                        writer?.flush()
                                        Log.d(TAG, "Sent TLS handshake data chunk: $currentChunkSize bytes")
                                    } catch (e: Exception) {
                                        if (e is BufferOverflowException && chunkSize > 1024) {
                                            Log.w(TAG, "Write buffer overflow, reducing chunk size to ${chunkSize / 2}")
                                            chunkSize /= 2
                                            packetBuffer.position(packetBuffer.position() - currentChunkSize)
                                            continue
                                        }
                                        throw e
                                    }
                                }
                                Log.d(TAG, "Sent TLS handshake data: ${result.bytesProduced()} bytes total")
                            }
                            lastStatus = result.status
                            if (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                                Log.w(TAG, "Buffer overflow during wrap, increasing packetBuffer size")
                                packetBuffer = ByteBuffer.allocate(packetBuffer.capacity() * 2)
                                continue
                            } else if (result.status == SSLEngineResult.Status.CLOSED) {
                                Log.e(TAG, "SSLEngine closed during wrap")
                                closeInternal()
                                return@withTimeoutOrNull false
                            }
                        }
                        SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                            var unwrapAttempts = 0
                            val maxUnwrapAttempts = 100
                            while (accumulatedData.position() > 0 && unwrapAttempts < maxUnwrapAttempts) {
                                accumulatedData.flip()
                                appBuffer.clear()
                                try {
                                    val result = sslEngine.unwrap(accumulatedData, appBuffer)
                                    accumulatedData.compact()
                                    Log.d(TAG, "Unwrap attempt $unwrapAttempts: status=${result.status}, bytesConsumed=${result.bytesConsumed()}, bytesProduced=${result.bytesProduced()}, handshakeStatus=${result.handshakeStatus}")
                                    lastStatus = result.status
                                    accumulatedBytes -= result.bytesConsumed()
                                    Log.d(TAG, "Consumed ${result.bytesConsumed()} bytes, remaining: $accumulatedBytes")
                                    if (result.status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                                        Log.d(TAG, "Buffer underflow, exiting unwrap loop to read more data")
                                        break
                                    } else if (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                                        Log.w(TAG, "Buffer overflow during unwrap, increasing appBuffer size")
                                        appBuffer = ByteBuffer.allocate(appBuffer.capacity() * 2)
                                    } else if (result.status == SSLEngineResult.Status.CLOSED) {
                                        Log.e(TAG, "SSLEngine closed during unwrap, accumulated bytes: $accumulatedBytes")
                                        closeInternal()
                                        return@withTimeoutOrNull false
                                    }
                                    if (sslEngine.handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                                        Log.d(TAG, "Handshake status changed to ${sslEngine.handshakeStatus}, exiting unwrap loop")
                                        break
                                    }
                                } catch (e: SSLException) {
                                    Log.e(TAG, "SSLException during unwrap: ${e.message}, accumulated bytes: $accumulatedBytes", e)
                                    closeInternal()
                                    return@withTimeoutOrNull false
                                }
                                unwrapAttempts++
                            }

                            if (sslEngine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP &&
                                accumulatedData.position() == 0) {
                                Log.d(TAG, "Waiting for TLS data from channel, retry=$readRetries")
                                val bytes = withTimeoutOrNull(1000) {
                                    tlsDataChannel.receive()
                                }
                                if (bytes != null) {
                                    accumulatedBytes += bytes.size
                                    readRetries = 0
                                    if (accumulatedData.remaining() < bytes.size) {
                                        Log.w(TAG, "Accumulated data buffer overflow, increasing size")
                                        val newBuffer = ByteBuffer.allocate(accumulatedData.capacity() * 2)
                                        accumulatedData.flip()
                                        newBuffer.put(accumulatedData)
                                        accumulatedData = newBuffer
                                    }
                                    accumulatedData.put(bytes)
                                    var offset = 0
                                    while (offset + 5 <= bytes.size) {
                                        val contentType = bytes[offset].toInt() and 0xFF
                                        val majorVersion = bytes[offset + 1].toInt() and 0xFF
                                        val minorVersion = bytes[offset + 2].toInt() and 0xFF
                                        val length = ((bytes[offset + 3].toInt() and 0xFF) shl 8) or (bytes[offset + 4].toInt() and 0xFF)
                                        Log.d(TAG, "TLS record at offset $offset: type=$contentType, version=$majorVersion.$minorVersion, length=$length")
                                        if (contentType == 22 && offset + 6 <= bytes.size) {
                                            val handshakeType = bytes[offset + 5].toInt() and 0xFF
                                            val handshakeTypeName = when (handshakeType) {
                                                2 -> "ServerHello"
                                                11 -> "Certificate"
                                                12 -> "ServerKeyExchange"
                                                13 -> "CertificateRequest"
                                                14 -> "ServerHelloDone"
                                                20 -> "Finished"
                                                else -> "Unknown ($handshakeType)"
                                            }
                                            Log.d(TAG, "Handshake message at offset $offset: $handshakeTypeName")
                                        } else if (contentType == 21 && offset + 7 <= bytes.size) {
                                            val alertLevel = bytes[offset + 5].toInt() and 0xFF
                                            val alertDescription = bytes[offset + 6].toInt() and 0xFF
                                            Log.e(TAG, "TLS alert: level=$alertLevel, description=$alertDescription")
                                            closeInternal()
                                            return@withTimeoutOrNull false
                                        }
                                        offset += 5 + length
                                    }
                                    val hexBytes = bytes.copyOfRange(0, bytes.size.coerceAtMost(256)).joinToString(", ") { byte -> "0x${byte.toUByte().toString(16).padStart(2, '0')}" }
                                    Log.d(TAG, "Received raw bytes: $hexBytes${if (bytes.size > 256) " ... (truncated, total ${bytes.size} bytes)" else ""}, total accumulated: $accumulatedBytes")
                                } else {
                                    readRetries++
                                    if (readRetries >= maxReadRetries) {
                                        Log.e(TAG, "Max read retries reached, accumulated bytes: $accumulatedBytes")
                                        closeInternal()
                                        return@withTimeoutOrNull false
                                    }
                                    Log.d(TAG, "No new data, retrying ($readRetries/$maxReadRetries)...")
                                    delay(50)
                                }
                            }
                        }
                        SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                            var task: Runnable?
                            while (sslEngine.delegatedTask.also { task = it } != null) {
                                task!!.run()
                                Log.d(TAG, "Ran delegated TLS task")
                            }
                        }
                        else -> {
                            Log.d(TAG, "Handshake status: ${sslEngine.handshakeStatus}")
                        }
                    }
                }
                Log.d(TAG, "TLS handshake completed: protocol=${sslEngine.session.protocol}, cipherSuite=${sslEngine.session.cipherSuite}")
                true
            } ?: run {
                Log.e(TAG, "TLS handshake timed out after $handshakeTimeoutMs ms, last status: $lastStatus, accumulatedBytes=$accumulatedBytes")
                closeInternal()
                return@withContext false
            }

            if (sslEngine.handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                sslEngine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                Log.e(TAG, "SSLEngine not in valid state for encryption: ${sslEngine.handshakeStatus}")
                closeInternal()
                return@withContext false
            }

            reader?.let {
                if (!it.isClosedForRead) {
                    try {
                        val discarded = it.discard(it.availableForRead.toLong())
                        Log.d(TAG, "Discarded $discarded bytes from reader channel")
                        val tempBuffer = ByteArray(65536)
                        var totalFlushed = 0L
                        while (it.availableForRead > 0) {
                            val bytesRead = it.readAvailable(tempBuffer)
                            if (bytesRead > 0) {
                                totalFlushed += bytesRead
                                Log.w(TAG, "Flushed residual data: ${tempBuffer.copyOfRange(0, bytesRead).joinToString(", ")}")
                            } else {
                                break
                            }
                        }
                        Log.d(TAG, "Total flushed residual data: $totalFlushed bytes")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error discarding/flushing residual data: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "Reader channel closed before TLS stream, attempting reinitialization")
                    reader = socket?.openReadChannel()
                    if (reader == null) {
                        Log.e(TAG, "Failed to reinitialize reader channel")
                        closeInternal()
                        return@withContext false
                    }
                    Log.d(TAG, "Reader channel reinitialized successfully")
                }
            }

            if (writer?.isClosedForWrite == true || writer == null) {
                Log.e(TAG, "Writer closed or null before sending stream header")
                closeInternal()
                return@withContext false
            }

            val restartedStream = """
                <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0' to='$domain'>
            """.trimIndent()
            appBuffer.clear()
            appBuffer.put(restartedStream.toByteArray())
            appBuffer.flip()
            packetBuffer.clear()
            val wrapResult = sslEngine.wrap(appBuffer, packetBuffer)
            Log.d(TAG, "Wrap stream result: status=${wrapResult.status}, bytesProduced=${wrapResult.bytesProduced()}")
            packetBuffer.flip()
            if (wrapResult.bytesProduced() > 0 && wrapResult.status == SSLEngineResult.Status.OK) {
                writer?.write { buffer ->
                    buffer.put(packetBuffer)
                    buffer.remaining()
                }
                writer?.flush()
                Log.d(TAG, "Sent restarted XMPP stream: $restartedStream")
            } else {
                Log.e(TAG, "Failed to wrap XMPP stream: status=${wrapResult.status}, bytesProduced=${wrapResult.bytesProduced()}")
                closeInternal()
                return@withContext false
            }

            packetBuffer.clear()
            Log.d(TAG, "Waiting for TLS stream response")
            val bytes = withTimeoutOrNull(10000) {
                tlsDataChannel.receive()
            }
            if (bytes != null) {
                Log.d(TAG, "Received raw TLS response bytes: ${bytes.copyOfRange(0, bytes.size.coerceAtMost(256)).joinToString(", ")}")
                packetBuffer.put(bytes)
                packetBuffer.flip()
                appBuffer.clear()
                val unwrapResult = sslEngine.unwrap(packetBuffer, appBuffer)
                Log.d(TAG, "Unwrap response result: status=${unwrapResult.status}, bytesProduced=${unwrapResult.bytesProduced()}")
                if (unwrapResult.bytesProduced() > 0) {
                    appBuffer.flip()
                    val response = ByteArray(appBuffer.remaining())
                    appBuffer.get(response)
                    val tlsResponse = String(response)
                    Log.d(TAG, "TLS response: $tlsResponse")
                    messageCallback?.invoke(tlsResponse)
                } else {
                    Log.e(TAG, "No application data in TLS response: ${unwrapResult.status}")
                    closeInternal()
                    return@withContext false
                }
            } else {
                Log.e(TAG, "No TLS response received within 10 seconds")
                closeInternal()
                return@withContext false
            }

            readingLoopJob?.cancel()
            readingLoopJob = null
            isReadingLoopActive = false
            Log.d(TAG, "TLS upgrade completed successfully")
            reader?.cancel()
            tlsHandshaking = false

            reader = socket?.openReadChannel()
            if (reader == null) {
                Log.e(TAG, "Failed to reopen reader after TLS")
                closeInternal()
                return@withContext false
            }

            if (isReadingLoopActive) {
                Log.w(TAG, "Old reading loop still active — should not happen")
                // Можно принудительно убить старый scope, если нужно
            }
            startReadingLoop()  // ← только один раз, после пересоздания reader
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upgrade to TLS: ${e.message}", e)
            closeInternal()
            return@withContext false
        } finally {
            tlsHandshaking = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saslPlainAuth(jid: String, username: String): String {
        AccountStorageItem().username = username
        val passwordVal = PasswordStorageHelper(XabberApplication.applicationContext()).getData(jid)
        if (passwordVal == null) {
            Log.e(TAG, "Failed to retrieve password for JID: $jid")
            throw IllegalStateException("Password not found for JID: $jid")
        }
        val authzId = ""
        val saslPlain = "$authzId\u0000$username\u0000$passwordVal"
        Log.d(TAG, "SASL PLAIN struct: authzId='$authzId', username='$username', password length=${passwordVal.length}")
        val rawBytes = saslPlain.toByteArray(StandardCharsets.UTF_8)
        Log.d(TAG, "SASL PLAIN raw bytes: ${rawBytes.joinToString(", ") { byte -> "0x${byte.toUByte().toString(16).padStart(2, '0')}" }}")
        val encoded = Base64.getEncoder().encodeToString(rawBytes)
        Log.d(TAG, "SASL PLAIN Base64 encoded: $encoded")
        return encoded
    }

    private suspend fun startReadLoop() {
        while (scope.isActive) {
            if (socket?.isClosed == true || reader?.isClosedForRead == true) {
                Log.w(TAG, "Socket or reader already closed — terminating read loop")
                if (!isReadLoopErrorFired) {
                    isReadLoopErrorFired = true
                    onReadLoopError?.invoke()
                }
                break
            }
            try {
                val tempBuffer = ByteArray(65536)
                val bytesRead = reader?.readAvailable(tempBuffer) ?: -1
                if (bytesRead == -1) {
                    Log.w(TAG, "Remote peer closed connection")
                    closeInternal()
                    onReadLoopError?.invoke()
                    break
                } else if (bytesRead > 0) {
                    lastDataReceivedTime = System.currentTimeMillis()
                    val bytes = tempBuffer.copyOfRange(0, bytesRead)
                    if (tlsHandshaking && !tlsDataChannel.isClosedForSend) {
                        tlsDataChannel.send(bytes)
                        Log.d(TAG, "Sent ${bytesRead} bytes to TLS channel")
                    } else {
                        val message = String(bytes, StandardCharsets.UTF_8)
                        Log.v(TAG, "Received ${bytesRead} bytes: ${message}")
                        if (message.contains("<proceed") && !proceedChannel.isClosedForSend) {
                            proceedChannel.send(message)
                        }
                        messageCallback?.invoke(message)
                    }
                } else {
                    Log.d(TAG, "No data available, continuing")
                }
                delay(if (tlsHandshaking) 5 else 10)
            } catch (e: CancellationException) {
                Log.w(TAG, "Read loop cancelled: ${e.message}", e)
                break
            } catch (e: ConcurrentIOException) {
                Log.e(TAG, "Concurrent read attempt: ${e.message}", e)
                delay(100)
                continue
            } catch (e: ClosedByteChannelException) {
                Log.w(TAG, "Reader channel closed (connection lost): ${e.message}", e)
                closeInternal()
                if (!isReadLoopErrorFired) {
                    isReadLoopErrorFired = true
                    onReadLoopError?.invoke()
                }
                break
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in read loop: ${e.message}", e)
                closeInternal()
                onReadLoopError?.invoke()
                break
            }
        }

        Log.w(TAG, "Read loop fully terminated")
        isReadingLoopActive = false
    }

    suspend fun write(message: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("XMPP STANZA SEND", "SEND: $message")
        try {
            writer?.let { w ->
                if (w.isClosedForWrite || socket?.isClosed == true) {
                    Log.e(TAG, "Writer closed or socket dead — cannot send stanza")
                    closeInternal()
                    onReadLoopError?.invoke()  // используем тот же callback — он триггерит reconnect
                    return@withContext false
                }
                val bytes = message.toByteArray(StandardCharsets.UTF_8)
                writeMutex.withLock {
                    w.writeFully(bytes, 0, bytes.size)
                    w.flush()
                    Log.v(TAG, "Written ${bytes.size} bytes, flushed")
                }
                true
            } ?: run {
                Log.e(TAG, "Writer is null — connection lost")
                closeInternal()
                onReadLoopError?.invoke()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Write failed: ${e.message}", e)
            closeInternal()
            onReadLoopError?.invoke()
            false
        }
    }

    suspend fun read(timeoutMs: Long = 10000): Boolean = withContext(Dispatchers.IO) {
        try {
            reader?.let { channel ->
                if (channel.isClosedForRead) {
                    Log.w(TAG, "Reader channel is closed before read attempt")
                    return@withContext false
                }
                val buffer = StringBuilder()
                val tempBuffer = ByteArray(1024)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val bytesRead = channel.readAvailable(tempBuffer)
                        when {
                            bytesRead == -1 -> {
                                Log.w(TAG, "Socket closed by remote peer after ${System.currentTimeMillis() - startTime}ms")
                                return@withContext false
                            }
                            bytesRead > 0 -> {
                                val chunk = tempBuffer.decodeToString(0, bytesRead)
                                buffer.append(chunk)
                                if (buffer.contains("</stream:stream>") ||
                                    buffer.contains("</stream:features>") ||
                                    buffer.contains("</stream:error>") ||
                                    buffer.contains("<proceed") ||
                                    buffer.contains("</iq>") ||
                                    buffer.contains("</message>") ||
                                    buffer.contains("</presence>") ||
                                    buffer.contains("<success") ||
                                    buffer.contains("<failure>")) {
                                    messageCallback?.invoke(buffer.toString())
                                    return@withContext true
                                }
                            }
                            else -> {
                                Log.d(TAG, "No data available after ${System.currentTimeMillis() - startTime}ms, continuing")
                            }
                        }
                        delay(5)
                    } catch (e: IOException) {
                        Log.w(TAG, "Read error after ${System.currentTimeMillis() - startTime}ms: ${e.message}", e)
                        return@withContext false
                    } catch (e: ClosedReceiveChannelException) {
                        Log.w(TAG, "Reader channel closed during read: ${e.message}", e)
                        return@withContext false
                    }
                }
                val message = buffer.toString()
                if (message.isNotEmpty()) {
                    Log.d(TAG, "Accumulated partial message: $message")
                    messageCallback?.invoke(message)
                    return@withContext true
                } else {
                    Log.d(TAG, "No data received within $timeoutMs ms")
                    return@withContext false
                }
            } ?: run {
                Log.e(TAG, "Socket reader is null")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from socket: ${e.message}", e)
            false
        }
    }

    suspend fun sendStreamHeader(socket: Socket, domain: String, jid: String) {
        try {
            val xmlContent = "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0' to='$domain'>"
            Log.d(TAG, "Serialized stream header: $xmlContent")
            socket.write(xmlContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending stream header: ${e.message}", e)
        }
    }

    suspend fun readServerResponse(socket: Socket): StreamResponse? {
        val response = socket.read() ?: return null
        return parseStreamResponse(response.toString())
    }

    fun parseStreamResponse(response: String): StreamResponse? {
        try {
            if (response.contains("<stream:error")) {
                Log.e(TAG, "Server responded with stream error: $response")
                return null
            }
            if (response.contains("<proceed")) {
                Log.d(TAG, "Received proceed response for STARTTLS")
                return null
            }
            if (response.contains("</stream:stream>") && !response.contains("<stream:features>")) {
                Log.e(TAG, "Server responded with stream termination: $response")
                return null
            }
            val xmlContent = response.replace(Regex("""<\?xml\s+version=['"][^'"]+['"](?:\s+encoding=['"][^'"]+['"])?\s*\?>"""), "").trim()
            Log.d(TAG, "Processing XML content: $xmlContent")

            val xml = XML {
                indent = 2
                autoPolymorphic = false
                defaultPolicy {
                    ignoreUnknownChildren()
                    pedantic = false
                }
            }

            if (xmlContent.startsWith("<stream:features")) {
                Log.d(TAG, "Parsing standalone stream features")
                val featuresMatch = Regex("""<stream:features([^>]*)>(.*?)</stream:features>""", RegexOption.DOT_MATCHES_ALL).find(xmlContent)
                val features = if (featuresMatch != null) {
                    val mechanismsContent = Regex("""<mechanisms[^>]*>(.*?)</mechanisms>""", RegexOption.DOT_MATCHES_ALL).find(featuresMatch.value)?.groupValues?.get(1)
                    val mechanisms = if (mechanismsContent != null) {
                        val mechanismList = Regex("""<mechanism>([^<]+)</mechanism>""").findAll(mechanismsContent)
                            .map { it.groupValues[1] }
                            .toList()
                        Mechanisms(mechanismList)
                    } else null
                    val starttlsPresent = featuresMatch.value.contains("<starttls")
                    val proxyPresent = featuresMatch.value.contains("<proxy")
                    val devicesPresent = featuresMatch.value.contains("https://xabber.com/protocol/devices")
                    val bindPresent = featuresMatch.value.contains("<bind")
                    StreamFeatures(
                        mechanisms = mechanisms,
                        starttls = if (starttlsPresent) StartTls(present = true) else null,
                        proxy = if (proxyPresent) Proxy(present = true) else null,
                        devices = if (devicesPresent) Devices(present = true) else null,
                        bind = if (bindPresent) Bind(present = true) else null
                    )
                } else {
                    Log.w(TAG, "No features found in response")
                    null
                }
                return StreamResponse(features = features)
            }

            Log.d(TAG, "Extracting stream:stream attributes")
            val headerMatch = Regex("""<stream:stream\s+([^>]+?)>""").find(xmlContent) ?: return null
            val attributes = headerMatch.groupValues[1]
            val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(attributes)
            val versionMatch = Regex("""version=['"]([^'"]+)['"]""").find(attributes)
            val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(attributes)
            val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(attributes)
            val xmlLangMatch = Regex("""xml:lang=['"]([^'"]+)['"]""").find(attributes)
            val xmlnsMatch = Regex("""xmlns=['"]([^'"]+)['"]""").find(attributes)
            val xmlnsStreamMatch = Regex("""xmlns:stream=['"]([^'"]+)['"]""").find(attributes)

            val featuresMatch = Regex("""<stream:features([^>]*)>(.*?)</stream:features>""", RegexOption.DOT_MATCHES_ALL).find(xmlContent)
            val features = if (featuresMatch != null) {
                Log.d(TAG, "Features match found: ${featuresMatch.value}")
                val mechanismsContent = Regex("""<mechanisms[^>]*>(.*?)</mechanisms>""", RegexOption.DOT_MATCHES_ALL).find(featuresMatch.value)?.groupValues?.get(1)
                val mechanisms = if (mechanismsContent != null) {
                    val mechanismList = Regex("""<mechanism>([^<]+)</mechanism>""").findAll(mechanismsContent)
                        .map { it.groupValues[1] }
                        .toList()
                    Mechanisms(mechanismList)
                } else null
                val starttlsPresent = featuresMatch.value.contains("<starttls")
                val proxyPresent = featuresMatch.value.contains("<proxy")
                val devicesPresent = featuresMatch.value.contains("https://xabber.com/protocol/devices")
                val bindPresent = featuresMatch.value.contains("<bind")
                StreamFeatures(
                    mechanisms = mechanisms,
                    starttls = if (starttlsPresent) StartTls(present = true) else null,
                    proxy = if (proxyPresent) Proxy(present = true) else null,
                    devices = if (devicesPresent) Devices(present = true) else null,
                    bind = if (bindPresent) Bind(present = true) else null
                )
            } else {
                Log.w(TAG, "No features found in response")
                null
            }

            val parsed = StreamResponse(
                id = idMatch?.groupValues?.get(1),
                version = versionMatch?.groupValues?.get(1),
                from = fromMatch?.groupValues?.get(1),
                to = toMatch?.groupValues?.get(1),
                xmlLang = xmlLangMatch?.groupValues?.get(1),
                xmlns = xmlnsMatch?.groupValues?.get(1) ?: "jabber:client",
                xmlnsStream = xmlnsStreamMatch?.groupValues?.get(1) ?: "http://etherx.jabber.org/streams",
                features = features
            )
            Log.d(TAG, "Parsed StreamResponse: $parsed")
            return parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stream response: ${e.message}\nRaw response: $response", e)
            return null
        }
    }

    private suspend fun closeInternal() = withContext(Dispatchers.IO) {
        try {
            socket?.let {
                if (!it.isClosed) {
                    writer?.let { w ->
                        if (!w.isClosedForWrite) {
                            try {
                                // 1. Отправляем unavailable presence
                                val unavailable = "<presence type='unavailable'/>"
                                w.writeFully(unavailable.toByteArray(StandardCharsets.UTF_8), 0, unavailable.length)
                                w.flush()
                                Log.d(TAG, "Sent unavailable presence")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send unavailable presence: ${e.message}")
                            }
                            try {
                                // 2. Закрываем стрим
                                w.writeFully("</stream:stream>".toByteArray(StandardCharsets.UTF_8), 0, 16)
                                w.flush()
                                Log.d(TAG, "Sent </stream:stream>")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send closing stream tag: ${e.message}")
                            }
                            w.close()
                        }
                    }
                    reader?.cancel()
                    it.close()
                }
            }
            socket = null
            reader = null
            writer = null
            messageCallback = null
            proceedChannel.close()
            tlsDataChannel.close()
            isReadingLoopActive = false
            isReadLoopErrorFired = false
            readingLoopJob?.cancel()
            readingLoopJob = null
            keepAliveJob?.cancel()
            keepAliveJob = null
            isReadingLoopActive = false
            isReadLoopErrorFired = false
            // **Важно:** отменяем все корутины этого сокета
            scope.cancel()
            Log.d(TAG, "Socket fully closed and cleaned")
        } catch (e: Exception) {
            Log.e(TAG, "Error during closeInternal: ${e.message}", e)
        }
    }

    suspend fun close() = closeInternal()

    suspend fun initiateXmppStream(socket: Socket, domain: String, jid: String): StreamResponse? {
        this.domain = domain
        sendStreamHeader(socket, domain, jid)
        Log.d(TAG, "Stream header sent, awaiting server response via callback")
        return null
    }

    fun getSocket(): io.ktor.network.sockets.Socket? {
        return socket
    }
}