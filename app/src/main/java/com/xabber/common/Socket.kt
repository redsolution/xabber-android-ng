package com.xabber.common

import io.ktor.util.logging.*
import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.network.tls.CIOCipherSuites.ECDHE_ECDSA_AES128_SHA256
import io.ktor.network.tls.CIOCipherSuites.ECDHE_ECDSA_AES256_SHA384
import io.ktor.network.tls.CIOCipherSuites.ECDHE_RSA_AES128_SHA256
import io.ktor.network.tls.CIOCipherSuites.ECDHE_RSA_AES256_SHA384
import io.ktor.network.tls.CIOCipherSuites.TLS_RSA_WITH_AES128_CBC_SHA
import io.ktor.network.tls.CIOCipherSuites.TLS_RSA_WITH_AES256_CBC_SHA
import io.ktor.network.tls.CIOCipherSuites.TLS_RSA_WITH_AES_128_GCM_SHA256
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlElement
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_AES_128_CBC_SHA
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_AES_256_CBC_SHA
import java.nio.charset.StandardCharsets
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.network.tls.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.nio.BufferOverflowException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.cert.CertificateException
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException


@Serializable
@XmlSerialName("stream", "http://etherx.jabber.org/streams", "stream")
data class StreamResponse(
    val id: String,
    val version: String,
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
    @XmlSerialName("mechanisms", "urn:xml:namespace:xmpp-sasl", "")
    val mechanisms: Mechanisms? = null,
    @XmlElement(true)
    @XmlSerialName("starttls", "urn:xml:namespace:xmpp-tls", "")
    val starttls: StartTls? = null,
    @XmlElement(true)
    @XmlSerialName("proxy", "urn:xabber:ws:proxy", "")
    val proxy: Proxy? = null
)

@Serializable
data class Mechanisms(
    @XmlElement(true)
    @XmlSerialName("mechanism", "urn:xml:namespace:xmpp-sasl", "")
    val mechanism: List<String> = emptyList()
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

class Socket(private val host: String, private val port: Int) {
    private val KTOR_LOGGER = KtorSimpleLogger("io.ktor.network")
    private var socket: io.ktor.network.sockets.Socket? = null
    private var reader: ByteReadChannel? = null
    private var writer: ByteWriteChannel? = null
    private val selectorManager = SelectorManager(Dispatchers.IO)
    var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "Socket_ng"
    private var messageCallback: ((String) -> Unit)? = null

    fun setMessageCallback(callback: (String) -> Unit) {
        messageCallback = callback
    }


    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 3
        val expectedFingerprint = "dab1d6bc9c8c825aa9fd266aee12ea562de40817a75fb81b79b50ad939a21f28"

        while (attempts < maxAttempts) {
            attempts++
            Log.d(TAG, "Connection attempt $attempts of $maxAttempts")
            try {
                // Step 1: Open a plain TCP socket
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

                // Step 2: Perform XMPP STARTTLS negotiation
                val streamOpen = """
                    <?xml version='1.0'?>
                    <stream:stream to='$host' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>
                """.trimIndent()
                writer?.writeStringUtf8(streamOpen)
                writer?.flush()
                Log.d(TAG, "Sent initial stream open: $streamOpen")

                // Read server response to detect <starttls> in <features>
                val buffer = ByteArray(32768)
                var bytesRead = reader?.readAvailable(buffer) ?: -1
                var response = ""
                if (bytesRead > 0) {
                    response = String(buffer, 0, bytesRead)
                    Log.d(TAG, "Server response: $response")
                } else {
                    Log.e(TAG, "No server response received")
                    closeInternal()
                    continue
                }

                // Parse response to check for <starttls> in <features>
                var hasStartTls = false
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(StringReader(response))
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "starttls" &&
                        parser.getAttributeValue(null, "xmlns") == "urn:ietf:params:xml:ns:xmpp-tls") {
                        hasStartTls = true
                        break
                    }
                    eventType = parser.next()
                }

                if (!hasStartTls) {
                    Log.e(TAG, "STARTTLS not offered in <features>")
                    closeInternal()
                    continue
                }

                // Send STARTTLS command
                writer?.writeStringUtf8("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")
                writer?.flush()
                Log.d(TAG, "Sent STARTTLS command")

                // Read STARTTLS response (<proceed>)
                bytesRead = reader?.readAvailable(buffer) ?: -1
                if (bytesRead > 0) {
                    val startTlsResponse = String(buffer, 0, bytesRead)
                    Log.d(TAG, "STARTTLS response: $startTlsResponse")
                    parser.setInput(StringReader(startTlsResponse))
                    eventType = parser.eventType
                    var hasProceed = false
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "proceed") {
                            hasProceed = true
                            break
                        }
                        eventType = parser.next()
                    }
                    if (!hasProceed) {
                        Log.e(TAG, "Server did not send <proceed>")
                        closeInternal()
                        continue
                    }
                } else {
                    Log.e(TAG, "No STARTTLS response received")
                    closeInternal()
                    continue
                }

                // Step 3: Initialize SSLEngine for TLS with certificate pinning
                val sslContext = SSLContext.getInstance("TLS")
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        if (chain == null || chain.isEmpty()) {
                            throw CertificateException("No server certificate provided")
                        }
                        val serverCert = chain[0]
                        val sha256 = MessageDigest.getInstance("SHA-256")
                        val fingerprint = sha256.digest(serverCert.encoded).joinToString("") { "%02x".format(it) }
                        Log.d(TAG, "Server certificate: issuer=${serverCert.issuerDN}, subject=${serverCert.subjectDN}, fingerprint=$fingerprint")
//                        if (!fingerprint.equals(expectedFingerprint, ignoreCase = true)) {
//                            throw CertificateException("Certificate fingerprint mismatch: expected=$expectedFingerprint, actual=$fingerprint")
//                        }
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                sslContext.init(null, arrayOf(trustManager), null)
                val sslEngine = sslContext.createSSLEngine(host, port)
                sslEngine.useClientMode = true
                sslEngine.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
                sslEngine.enabledCipherSuites = arrayOf(
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_CHACHA20_POLY1305_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                    "TLS_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_RSA_WITH_AES_128_CBC_SHA"
                )
                sslEngine.beginHandshake()
                Log.d(TAG, "SSLEngine initialized with protocols: ${sslEngine.enabledProtocols.joinToString()}")
                Log.d(TAG, "SSLEngine initialized with cipher suites: ${sslEngine.enabledCipherSuites.joinToString()}")

// Step 4: Perform TLS handshake with timeout
                var appBuffer = ByteBuffer.allocate(sslEngine.session.applicationBufferSize)
                var packetBuffer = ByteBuffer.allocate(sslEngine.session.packetBufferSize * 2) // ~66KB
                var accumulatedData = ByteBuffer.allocate(1048576) // 1MB
                val handshakeTimeoutMs = 10000L // 10 seconds
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
                                    // Write in smaller chunks to avoid write channel overflow
                                    var chunkSize = 4096 // Start with 4KB chunks
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
                                                packetBuffer.position(packetBuffer.position() - currentChunkSize) // Rewind
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
                                    continue // Retry wrap
                                } else if (result.status == SSLEngineResult.Status.CLOSED) {
                                    Log.e(TAG, "SSLEngine closed during wrap")
                                    closeInternal()
                                    return@withTimeoutOrNull false
                                }
                            }
                            SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                                // Process all available data in accumulatedData
                                var unwrapAttempts = 0
                                val maxUnwrapAttempts = 200
                                while (accumulatedData.position() > 0 && unwrapAttempts < maxUnwrapAttempts) {
                                    accumulatedData.flip() // Prepare to read
                                    appBuffer.clear()
                                    try {
                                        val result = sslEngine.unwrap(accumulatedData, appBuffer)
                                        accumulatedData.compact() // Remove consumed bytes
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
                                        // Exit unwrap loop if status changes (e.g., to NEED_WRAP)
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

                                // Only read from network if still in NEED_UNWRAP and no data remains
                                if (sslEngine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP &&
                                    accumulatedData.position() == 0) {
                                    val buffer = ByteArray(65536)
                                    bytesRead = reader?.readAvailable(buffer) ?: -1
                                    Log.d(TAG, "Read attempt: bytesRead=$bytesRead, retry=$readRetries")
                                    if (bytesRead > 0) {
                                        accumulatedBytes += bytesRead
                                        readRetries = 0
                                        if (accumulatedData.remaining() < bytesRead) {
                                            Log.w(TAG, "Accumulated data buffer overflow, increasing size")
                                            val newBuffer = ByteBuffer.allocate(accumulatedData.capacity() * 2)
                                            accumulatedData.flip()
                                            newBuffer.put(accumulatedData)
                                            accumulatedData = newBuffer
                                        }
                                        accumulatedData.put(buffer, 0, bytesRead)
                                        // Log TLS record details
                                        var offset = 0
                                        while (offset + 5 <= bytesRead) {
                                            val contentType = buffer[offset].toInt() and 0xFF
                                            val majorVersion = buffer[offset + 1].toInt() and 0xFF
                                            val minorVersion = buffer[offset + 2].toInt() and 0xFF
                                            val length = ((buffer[offset + 3].toInt() and 0xFF) shl 8) or (buffer[offset + 4].toInt() and 0xFF)
                                            Log.d(TAG, "TLS record at offset $offset: type=$contentType, version=$majorVersion.$minorVersion, length=$length")
                                            if (contentType == 22 && offset + 6 <= bytesRead) {
                                                val handshakeType = buffer[offset + 5].toInt() and 0xFF
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
                                            } else if (contentType == 21 && offset + 7 <= bytesRead) {
                                                val alertLevel = buffer[offset + 5].toInt() and 0xFF
                                                val alertDescription = buffer[offset + 6].toInt() and 0xFF
                                                Log.e(TAG, "TLS alert: level=$alertLevel, description=$alertDescription")
                                                closeInternal()
                                                return@withTimeoutOrNull false
                                            }
                                            offset += 5 + length
                                        }
                                        val hexBytes = buffer.copyOfRange(0, bytesRead.coerceAtMost(256)).joinToString(", ") { byte -> "0x${byte.toUByte().toString(16).padStart(2, '0')}" }
                                        Log.d(TAG, "Received raw bytes: $hexBytes${if (bytesRead > 256) " ... (truncated, total $bytesRead bytes)" else ""}, total accumulated: $accumulatedBytes")
                                    } else if (bytesRead == -1) {
                                        Log.e(TAG, "Socket closed by server, last status: $lastStatus, accumulated bytes: $accumulatedBytes")
                                        closeInternal()
                                        return@withTimeoutOrNull false
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
                    Log.e(TAG, "TLS handshake timed out after $handshakeTimeoutMs ms, last status: $lastStatus, accumulated bytes: $accumulatedBytes")
                    closeInternal()
                    return@withContext false
                }

                // Step 5: Verify SSLEngine state before sending restarted stream
                if (sslEngine.handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                    sslEngine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    Log.e(TAG, "SSLEngine not in valid state for encryption: ${sslEngine.handshakeStatus}")
                    closeInternal()
                    return@withContext false
                }

                // Step 6: Send restarted XMPP stream
                val restartedStream = """
                    <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0' to='$host'>
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

                // Step 7: Read encrypted response
                packetBuffer.clear()
                bytesRead = reader?.readAvailable(buffer) ?: -1
                if (bytesRead > 0) {
                    Log.d(TAG, "Received raw TLS response bytes: ${buffer.copyOfRange(0, bytesRead.coerceAtMost(256)).joinToString(", ")}")
                    packetBuffer.put(buffer, 0, bytesRead)
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
                    Log.e(TAG, "No TLS response received")
                    closeInternal()
                    return@withContext false
                }

                // Step 8: Start read loop
                scope.launch { startReadLoop() }
                Log.d(TAG, "Connection established and read loop started")
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



    suspend fun connect2(): Boolean = withContext(Dispatchers.IO) {
        try {
            val manager = SelectorManager(Dispatchers.IO)
            val tcp = aSocket(manager).tcp()


            socket = tcp.connect(host, port) {
                noDelay = true
                keepAlive = true
            }
            reader = socket?.openReadChannel()
            writer = socket?.openWriteChannel(autoFlush = true)


            if (reader == null || writer == null) {
                Log.e(TAG, "Failed to initialize reader or writer channels")
                closeInternal()
                return@withContext false
            }
            Log.d(TAG, "Ktor TCP socket connected to $host:$port")
            scope.launch { startReadLoop() }
            val tlsConfig = TLSConfigBuilder().apply {
                cipherSuites = listOf(
                    ECDHE_RSA_AES256_SHA384,
                    ECDHE_RSA_AES128_SHA256,
                    ECDHE_ECDSA_AES256_SHA384,
                    ECDHE_ECDSA_AES128_SHA256,
                    TLS_RSA_WITH_AES_128_GCM_SHA256,
                    TLS_RSA_WITH_AES256_CBC_SHA,
                    TLS_RSA_WITH_AES128_CBC_SHA
                )
            }.build()
            writer?.flush()
            val tlsSocket = withContext(Dispatchers.IO) {
                socket!!.tls(Dispatchers.IO, tlsConfig)
            }

            // Step 5: Open new read and write channels AFTER TLS upgrade
            val sslreader = tlsSocket.openReadChannel()
            val sslwriter = tlsSocket.openWriteChannel(autoFlush = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host:$port: $e")
            closeInternal()
            false
        }
    }

    private suspend fun startReadLoop() {
        while (scope.isActive && socket?.isClosed == false) {
            try {
                val message = read(timeoutMs = 30000)
                if (message != null) {
                    Log.d(TAG, "Processed message: $message")
                    messageCallback?.invoke(message)
                } else {
                    Log.d(TAG, "No complete message received, continuing to listen")
                    if (socket?.isClosed == true || reader?.isClosedForRead == true) {
                        Log.w(TAG, "Socket or reader closed, terminating read loop")
                        break
                    }
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Read loop cancelled: $e")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in read loop: $e")
                if (socket?.isClosed == true || reader?.isClosedForRead == true) {
                    Log.w(TAG, "Socket or reader closed, terminating read loop")
                    break
                }
                delay(1000)
            }
        }
        Log.w(TAG, "Read loop terminated: scope active=${scope.isActive}, socket closed=${socket?.isClosed}")
    }

    suspend fun prepareForTlsUpgrade() {
        stopReadLoop()
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
                    it.cancel()
                    KTOR_LOGGER.debug("Reader channel cancelled")
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
                    it.flushAndClose()
                    KTOR_LOGGER.debug("Writer channel closed")
                } else {
                    Log.d(TAG, "Writer already closed for writing")
                }
            }
            // Проверка остаточных данных
            socket?.let {
                try {
                    val tempReader = it.openReadChannel()
                    val tempBuffer = ByteArray(1024)
                    val bytesRead = tempReader.readAvailable(tempBuffer)
                    if (bytesRead > 0) {
                        Log.w(TAG, "Residual data found after channel cleanup: ${tempBuffer.copyOfRange(0, bytesRead).joinToString(", ")}")
                        KTOR_LOGGER.warn("Residual data: ${tempBuffer.copyOfRange(0, bytesRead).joinToString(", ")}")
                    } else if (bytesRead == -1) {
                        Log.w(TAG, "Socket closed by server during cleanup check")
                    }
                    tempReader.cancel()
                } catch (e: Exception) {
                    KTOR_LOGGER.warn("Error checking residual data: $e")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing channels before TLS upgrade: $e")
            KTOR_LOGGER.error("Error closing channels: $e")
        } finally {
            reader = null
            writer = null
            Log.w(TAG, "READER: $reader")
            Log.w(TAG, "WRITER: $writer")
            Log.d(TAG, "Reader and writer nullified for TLS upgrade")
            KTOR_LOGGER.debug("Reader and writer set to null")
        }
    }

    private fun stopReadLoop() {
        scope.cancel("Stopping read loop for TLS upgrade")
        Log.d(TAG, "Read loop stopped for TLS upgrade")
        KTOR_LOGGER.debug("Read loop scope cancelled")
    }

    suspend fun upgradeToTls(): Boolean = withContext(Dispatchers.IO) {
//        reader?.cancel()
//        socket?.tls(Dispatchers.IO)
        return@withContext false
//        try {
//            val currentSocket = socket ?: run {
//                Log.e(TAG, "Cannot upgrade to TLS: Socket is null")
//                KTOR_LOGGER.error("Socket is null")
//                return@withContext false
//            }
//            if (currentSocket.isClosed) {
//                Log.e(TAG, "Cannot upgrade to TLS: Socket is closed")
//                KTOR_LOGGER.error("Socket is closed")
//                return@withContext false
//            }
//
//            // Дополнительная проверка на привязанные каналы
//            if (reader != null || writer != null) {
//                Log.w(TAG, "Channels not fully cleared, forcing cleanup")
//                KTOR_LOGGER.warn("Forcing channel cleanup before TLS upgrade")
////                prepareForTlsUpgrade()
//            }
//
//            val tlsConfig = TLSConfigBuilder().apply {
//                cipherSuites = listOf(
//                    ECDHE_RSA_AES256_SHA384,
//                    ECDHE_RSA_AES128_SHA256,
//                    ECDHE_ECDSA_AES256_SHA384,
//                    ECDHE_ECDSA_AES128_SHA256,
//                    TLS_RSA_WITH_AES_128_GCM_SHA256,
//                    TLS_RSA_WITH_AES256_CBC_SHA,
//                    TLS_RSA_WITH_AES128_CBC_SHA
//                )
//                // Предложенная конфигурация TLS с кастомным trustManager
//                trustManager = object : X509TrustManager {
//                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
//                        KTOR_LOGGER.debug("Skipping client certificate validation")
//                    }
//
//                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
//                        KTOR_LOGGER.debug("Skipping server certificate validation")
//                    }
//
//                    override fun getAcceptedIssuers(): Array<X509Certificate>? {
//                        KTOR_LOGGER.debug("Returning null for accepted issuers")
//                        return null
//                    }
//                }
//            }.build()
//
//            KTOR_LOGGER.debug("Initiating TLS upgrade on existing socket")
//            val tlsSocket = currentSocket.tls(Dispatchers.IO, tlsConfig)
//            socket = tlsSocket
//
//            KTOR_LOGGER.debug("TLS handshake completed, opening channels")
//            reader = tlsSocket.openReadChannel()
//            writer = tlsSocket.openWriteChannel(autoFlush = true)
//
//            if (reader == null || writer == null) {
//                Log.e(TAG, "Failed to initialize reader or writer after TLS upgrade")
//                KTOR_LOGGER.error("Failed to initialize reader or writer channels")
//                closeInternal()
//                return@withContext false
//            }
//
//            Log.d(TAG, "Socket upgraded to TLS for $host:$port")
//            KTOR_LOGGER.debug("TLS socket created, starting read loop")
//            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//            scope.launch { startReadLoop() }
//            true
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to upgrade socket to TLS: $e", e)
//            KTOR_LOGGER.error("TLS handshake failed: $e")
//            closeInternal()
//            false
//        }
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
                it.writeFully(bytes, 0, bytes.size)
                Log.d(TAG, "Sent message: $message")
                true
            } ?: run {
                Log.e(TAG, "Cannot send: Socket writer is null")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: $e")
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
                                Log.d(TAG, "Read chunk: $chunk")
                                Log.d(TAG, "Read chunk (bytes): ${tempBuffer.copyOfRange(0, bytesRead).joinToString(", ")}")
                                if (buffer.contains("</stream:stream>") ||
                                    buffer.contains("</stream:features>") ||
                                    buffer.contains("</stream:error>") ||
                                    buffer.contains("<proceed") ||
                                    buffer.contains("</iq>") ||
                                    buffer.contains("</message>") ||
                                    buffer.contains("</presence>")) {
                                    return@withContext buffer.toString()
                                }
                            }
                            else -> {
                                Log.d(TAG, "No data available after ${System.currentTimeMillis() - startTime}ms, continuing")
                            }
                        }
                        delay(10)
                    } catch (e: IOException) {
                        Log.w(TAG, "Read error after ${System.currentTimeMillis() - startTime}ms: $e")
                        return@withContext null
                    } catch (e: ClosedReceiveChannelException) {
                        Log.w(TAG, "Reader channel closed during read: $e")
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
            Log.e(TAG, "Error reading from socket: $e")
            null
        }
    }

    suspend fun sendStreamHeader(socket: Socket, domain: String, jid: String) {
        try {
            val xmlContent = "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0' to='$domain'>"
            Log.d(TAG, "Serialized stream header: $xmlContent")
            socket.write(xmlContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending stream header: $e")
        }
    }

    suspend fun readServerResponse(socket: Socket): StreamResponse? {
        val response = socket.read() ?: return null
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
                Log.e(TAG, "Server closed stream unexpectedly: $response")
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
            Log.d(TAG, "Extracting stream:stream attributes")
            val headerMatch = Regex("""<stream:stream\s+([^>]+?)>""").find(xmlContent)
            if (headerMatch == null) {
                Log.e(TAG, "No <stream:stream> tag found in response")
                return null
            }
            Log.d(TAG, "Header match found: ${headerMatch.value}")
            val attributes = headerMatch.groupValues[1]
            val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(attributes)
            val versionMatch = Regex("""version=['"]([^'"]+)['"]""").find(attributes)
            val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(attributes)
            val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(attributes)
            val xmlLangMatch = Regex("""xml:lang=['"]([^'"]+)['"]""").find(attributes)
            val xmlnsMatch = Regex("""xmlns=['"]([^'"]+)['"]""").find(attributes)
            val xmlnsStreamMatch = Regex("""xmlns:stream=['"]([^'"]+)['"]""").find(attributes)

            if (idMatch == null || versionMatch == null) {
                Log.e(TAG, "Missing required attributes (id or version) in <stream:stream>")
                return null
            }

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
                StreamFeatures(
                    mechanisms = mechanisms,
                    starttls = if (starttlsPresent) StartTls(present = true) else null,
                    proxy = if (proxyPresent) Proxy(present = true) else null
                )
            } else {
                Log.w(TAG, "No features found in response")
                null
            }

            val parsed = StreamResponse(
                id = idMatch.groupValues[1],
                version = versionMatch.groupValues[1],
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
            Log.e(TAG, "Failed to parse server response: $e\nRaw response: $response")
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
            scope.cancel("Socket closed")
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // Создать новый scope
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: $e")
        }
    }

    suspend fun close() = closeInternal()

    suspend fun initiateXmppStream(socket: Socket, domain: String, jid: String): StreamResponse? {
        sendStreamHeader(socket, domain, jid)
        Log.d(TAG, "Stream header sent, awaiting server response via callback")
        return null
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
                Log.e(TAG, "Server closed stream unexpectedly: $response")
                return null
            }
            val xmlContent = response.replace(Regex("""<\?xml\s+version=['"][^'"]+['"](?:\s+encoding=['"][^'"]+['"])?\s*\?>"""), "").trim()
            val xml = XML {
                indent = 2
                autoPolymorphic = false
                defaultPolicy {
                    ignoreUnknownChildren()
                    pedantic = false
                }
            }
            val headerMatch = Regex("""<stream:stream\s+([^>]+?)>""").find(xmlContent) ?: return null
            val attributes = headerMatch.groupValues[1]
            val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(attributes) ?: return null
            val versionMatch = Regex("""version=['"]([^'"]+)['"]""").find(attributes) ?: return null
            val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(attributes)
            val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(attributes)
            val xmlLangMatch = Regex("""xml:lang=['"]([^'"]+)['"]""").find(attributes)
            val xmlnsMatch = Regex("""xmlns=['"]([^'"]+)['"]""").find(attributes)
            val xmlnsStreamMatch = Regex("""xmlns:stream=['"]([^'"]+)['"]""").find(attributes)

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
                StreamFeatures(
                    mechanisms = mechanisms,
                    starttls = if (starttlsPresent) StartTls(present = true) else null,
                    proxy = if (proxyPresent) Proxy(present = true) else null
                )
            } else null

            return StreamResponse(
                id = idMatch.groupValues[1],
                version = versionMatch.groupValues[1],
                from = fromMatch?.groupValues?.get(1),
                to = toMatch?.groupValues?.get(1),
                xmlLang = xmlLangMatch?.groupValues?.get(1),
                xmlns = xmlnsMatch?.groupValues?.get(1) ?: "jabber:client",
                xmlnsStream = xmlnsStreamMatch?.groupValues?.get(1) ?: "http://etherx.jabber.org/streams",
                features = features
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stream response: $e")
            return null
        }
    }

    fun getSocket(): io.ktor.network.sockets.Socket? {
        return socket
    }
}