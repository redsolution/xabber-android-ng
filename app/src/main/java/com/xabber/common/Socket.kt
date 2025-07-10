package com.xabber.common

import android.os.Build
import android.util.Log
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
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlElement
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

    fun setMessageCallback(callback: (String) -> Unit) {
        messageCallback = callback
    }

    fun setDomain(domain: String) {
        this.domain = domain
    }

    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            attempts++
            Log.d(TAG, "Connection attempt $attempts of $maxAttempts")
            try {
                val tcp = aSocket(selectorManager).tcp()
                socket = tcp.connect(host, port) {
                    noDelay = true
                    keepAlive = true
                }
                writer = socket?.openWriteChannel(autoFlush = true)
                reader = socket?.openReadChannel()

                if (reader == null || writer == null) {
                    Log.e(TAG, "Failed to initialize reader or writer channels")
                    closeInternal()
                    return@withContext false
                }

                startReadingLoop()
                Log.d(TAG, "TCP connection established and read loop started")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error during connection attempt $attempts: ${e.message}", e)
                closeInternal()
                if (attempts < maxAttempts) {
                    delay(1000)
                    continue
                }
                return@withContext false
            }
        }
        Log.e(TAG, "All connection attempts failed")
        return@withContext false
    }

    private fun startReadingLoop() {
        if (isReadingLoopActive) {
            Log.d(TAG, "Reading loop already active, skipping start")
            return
        }
        isReadingLoopActive = true
        scope.launch {
            try {
                startReadLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Reading loop failed: ${e.message}", e)
            } finally {
                isReadingLoopActive = false
                Log.d(TAG, "Reading loop coroutine terminated")
                if (socket?.isClosed == false && reader?.isClosedForRead == false && !tlsHandshaking) {
                    Log.d(TAG, "Restarting reading loop due to unexpected termination")
                    delay(100)
                    startReadingLoop()
                } else {
                    Log.w(TAG, "Cannot restart reading loop: socket closed=${socket?.isClosed}, reader closed=${reader?.isClosedForRead}, tlsHandshaking=$tlsHandshaking")
                }
            }
        }
    }

    suspend fun initiateStartTls(): Boolean = withContext(Dispatchers.IO) {
        try {
            writer?.writeStringUtf8("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")
            writer?.flush()
            Log.d(TAG, "Sent STARTTLS command")

            val proceed = withTimeoutOrNull(30000) {
                proceedChannel.receive()
            }
            if (proceed == null || !proceed.contains("<proceed")) {
                Log.e(TAG, "Failed to receive <proceed> within 30 seconds or invalid response: $proceed")
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

            val expectedFingerprint = "dab1d6bc9c8c825aa9fd266aee12ea562de40817a75fb81b79b50ad939a21f28"
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
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"
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

            val handshakeTimeoutMs = 10000L
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
                            val maxUnwrapAttempts = 200
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
                                val bytes = withTimeoutOrNull(2000) {
                                    tlsDataChannel.receive()
                                }
                                Log.d(TAG, "Read attempt: bytesRead=${bytes?.size ?: -1}, retry=$readRetries, reader closed=${reader?.isClosedForRead}")
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

            delay(200)
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
            val bytes = withTimeoutOrNull(15000) {
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
                Log.e(TAG, "No TLS response received within 15 seconds")
                closeInternal()
                return@withContext false
            }

            if (!isReadingLoopActive) {
                Log.d(TAG, "Reading loop not active, restarting")
                startReadingLoop()
            }
            Log.d(TAG, "TLS upgrade completed successfully")
            tlsHandshaking = false
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
        while (scope.isActive && socket?.isClosed == false && reader?.isClosedForRead == false) {
            try {
                val tempBuffer = ByteArray(65536)
                val bytesRead = reader?.readAvailable(tempBuffer) ?: -1
                Log.d(TAG, "Read attempt: bytesRead=$bytesRead, reader closed=${reader?.isClosedForRead}")
                if (bytesRead == -1) {
                    Log.w(TAG, "Socket closed by remote peer")
                    break
                } else if (bytesRead > 0) {
                    val bytes = tempBuffer.copyOfRange(0, bytesRead)
                    if (tlsHandshaking && !tlsDataChannel.isClosedForSend) {
                        tlsDataChannel.send(bytes)
                        Log.d(TAG, "Sent ${bytesRead} bytes to TLS channel")
                    } else {
                        val message = String(bytes, StandardCharsets.UTF_8)
                        Log.d(TAG, "Read chunk: $message")
//                        Log.d(TAG, "Read chunk (bytes): ${bytes.joinToString(", ")}")
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
                Log.e(TAG, "Concurrent read attempt in read loop: ${e.message}", e)
                delay(100)
                continue
            } catch (e: ClosedByteChannelException) {
                Log.w(TAG, "Reader channel closed in read loop: ${e.message}", e)
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in read loop: ${e.message}", e)
                if (socket?.isClosed == true || reader?.isClosedForRead == true) {
                    Log.w(TAG, "Socket or reader closed, terminating read loop")
                    break
                }
                delay(1000)
            }
        }
        Log.w(TAG, "Read loop terminated: scope active=${scope.isActive}, socket closed=${socket?.isClosed}, reader closed=${reader?.isClosedForRead}")
        if (!tlsHandshaking) {
            proceedChannel.close()
            tlsDataChannel.close()
        }
    }

    suspend fun prepareForTlsUpgrade() {
        try {
            Log.d(TAG, "Preparing to clean up channels. Reader: $reader, Writer: $writer")
            reader?.let {
                if (!it.isClosedForRead) {
                    val available = it.availableForRead
                    Log.d(TAG, "Reader has $available bytes available")
                    try {
                        it.discard(available.toLong())
                        KTOR_LOGGER.debug("Discarded $available bytes from reader buffer")
                    } catch (e: Exception) {
                        KTOR_LOGGER.warn("Error discarding reader buffer: $e")
                    }
                } else {
                    Log.d(TAG, "Reader already closed for reading")
                }
            }
            writer?.let {
                if (!it.isClosedForWrite) {
                    try {
                        it.flush()
                        KTOR_LOGGER.debug("Writer channel flushed")
                    } catch (e: Exception) {
                        KTOR_LOGGER.warn("Error flushing writer: $e")
                    }
                } else {
                    Log.d(TAG, "Writer already closed for writing")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error preparing channels for TLS upgrade: ${e.message}", e)
            KTOR_LOGGER.error("Error preparing channels: $e")
        }
    }

    suspend fun write(message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            writer?.let {
                if (it.isClosedForWrite) {
                    Log.e(TAG, "Writer channel is closed")
                    return@withContext false
                }
                val bytes = message.toByteArray(StandardCharsets.UTF_8)
                Log.d(TAG, "Raw bytes to send: ${bytes.joinToString(", ")}")
                writeMutex.withLock { // Serialize the write operation
                    it.writeFully(bytes, 0, bytes.size)
                }
                Log.d(TAG, "Sent message: $message")
                true
            } ?: run {
                Log.e(TAG, "Cannot send: Socket writer is null")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}", e)
            false
        }
    }

    suspend fun read(timeoutMs: Long = 30000): String? = withContext(Dispatchers.IO) {
        try {
            reader?.let { channel ->
                if (channel.isClosedForRead) {
                    Log.w(TAG, "Reader channel is closed before read attempt")
                    return@withContext null
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
                                return@withContext null
                            }
                            bytesRead > 0 -> {
                                val chunk = tempBuffer.decodeToString(0, bytesRead)
                                buffer.append(chunk)
                               /**/ Log.d(TAG, "Read chunk: $chunk")
                            /**/    Log.d(TAG, "Read chunk (bytes): ${tempBuffer.copyOfRange(0, bytesRead).joinToString(", ")}")
                                if (buffer.contains("</stream:stream>") ||
                                    buffer.contains("</stream:features>") ||
                                    buffer.contains("</stream:error>") ||
                                    buffer.contains("<proceed") ||
                                    buffer.contains("</iq>") ||
                                    buffer.contains("</message>") ||
                                    buffer.contains("</presence>") ||
                                    buffer.contains("<success") ||
                                    buffer.contains("<failure>")) {
                                    return@withContext buffer.toString()
                                }
                            }
                            else -> {
                                Log.d(TAG, "No data available after ${System.currentTimeMillis() - startTime}ms, continuing")
                            }
                        }
                        delay(10)
                    } catch (e: IOException) {
                        Log.w(TAG, "Read error after ${System.currentTimeMillis() - startTime}ms: ${e.message}", e)
                        return@withContext null
                    } catch (e: ClosedReceiveChannelException) {
                        Log.w(TAG, "Reader channel closed during read: ${e.message}", e)
                        return@withContext null
                    }
                }
                val message = buffer.toString()
                if (message.isNotEmpty()) {
                    Log.d(TAG, "Accumulated partial message: $message")
                    message
                } else {
                    Log.d(TAG, "No data received within $timeoutMs ms")
                    null
                }
            } ?: run {
                Log.e(TAG, "Socket reader is null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from socket: ${e.message}", e)
            null
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
        return parseStreamResponse(response)
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

    suspend fun sendPing(jid: String): Boolean = withContext(Dispatchers.IO) {
        val ping = """
            <iq type='get' id='ping1' to='$jid'>
                <ping xmlns='urn:xmpp:ping'/>
            </iq>"""
        return@withContext write(ping)
    }

    private suspend fun closeInternal() = withContext(Dispatchers.IO) {
        try {
            socket?.let {
                if (!it.isClosed) {
                    writer?.let { writer ->
                        if (!writer.isClosedForWrite) {
                            writer.writeFully("</stream:stream>".toByteArray(StandardCharsets.UTF_8), 0, 16)
                            Log.d(TAG, "Sent message: </stream:stream>")
                            writer.close()
                        }
                    }
                    reader?.let { reader ->
                        if (!reader.isClosedForRead) {
                            reader.cancel()
                        }
                    }
                    it.close()
                    Log.d(TAG, "Ktor TCP socket closed for $host:$port")
                }
            }
            socket = null
            reader = null
            writer = null
            if (scope.isActive) {
                scope.cancel("Socket closed")
            }
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            proceedChannel.close()
            tlsDataChannel.close()
            isReadingLoopActive = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
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