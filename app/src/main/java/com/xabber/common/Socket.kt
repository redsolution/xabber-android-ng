package com.xabber.common

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToString
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlElement
import java.nio.charset.StandardCharsets
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@Serializable
@XmlSerialName("stream", "http://etherx.jabber.org/streams", "stream")
data class ClientStreamHeader(
    val from: String,
    val to: String,
    val version: String = "1.0",
    @XmlSerialName("lang", "http://www.w3.org/XML/1998/namespace", "xml")
    val xmlLang: String = "en",
    @XmlSerialName("xmlns", "", "")
    val xmlns: String = "jabber:client"
)

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
    private var socket: io.ktor.network.sockets.Socket? = null
    private var reader: ByteReadChannel? = null
    private var writer: ByteWriteChannel? = null
    private val selectorManager = SelectorManager(Dispatchers.IO)
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "Socket_ng"
    private var messageCallback: ((String) -> Unit)? = null

    // Set a callback to handle incoming messages from the read loop
    fun setMessageCallback(callback: (String) -> Unit) {
        messageCallback = callback
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = aSocket(selectorManager).tcp().connect(host, port)
            reader = socket?.openReadChannel()
            writer = socket?.openWriteChannel(autoFlush = true)
            if (reader == null || writer == null) {
                Log.e(TAG, "Failed to initialize reader or writer channels")
                socket?.close()
                socket = null
                reader = null
                writer = null
                return@withContext false
            }
            Log.d(TAG, "Ktor TCP socket connected to $host:$port")
            if (scope.coroutineContext.isActive) {
                scope.launch { startReadLoop() }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host:$port: $e")
            socket?.close()
            socket = null
            reader = null
            writer = null
            false
        }
    }

    // Continuous read loop to process incoming messages
    private suspend fun startReadLoop() {
        while (scope.isActive && socket?.isClosed == false) {
            try {
                val message = read(timeoutMs = 30000)
                if (message != null) {
                    Log.d(TAG, "Processed message: $message")
                    messageCallback?.invoke(message)
//                    if (message.contains("</stream:stream>")) {
//                        Log.w(TAG, "Received stream termination, notifying for reconnection")
//                        messageCallback?.invoke("RECONNECT_REQUIRED")
//                    }
                } else {
//                    Log.d(TAG, "No complete message received, continuing to listen")
                    if (socket?.isClosed == true) {
                        Log.w(TAG, "Socket closed, read loop terminating")
                        break
                    }
//                    if (reader?.isClosedForRead == true) {
//                        Log.w(TAG, "Reader channel closed, notifying for reconnection")
//                        messageCallback?.invoke("RECONNECT_REQUIRED")
//                        break
//                    }
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Read loop cancelled: $e")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in read loop: $e")
                if (socket?.isClosed == true) {
                    break
                }
//                if (reader?.isClosedForRead == true) {
//                    Log.w(TAG, "Reader channel closed, notifying for reconnection")
//                    messageCallback?.invoke("RECONNECT_REQUIRED")
//                    break
//                }
                delay(1000)
            }
        }
        Log.w(TAG, "Read loop terminated: scope active=${scope.isActive}, socket closed=${socket?.isClosed}")
    }

    // Reconnect logic
    private suspend fun reconnect() {
        try {
            close()
            if (connect()) {
                Log.d(TAG, "Reconnected successfully to $host:$port")
            } else {
                Log.e(TAG, "Reconnection failed to $host:$port")
                delay(5000) // Wait before retrying
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reconnection error: $e")
            delay(5000)
        }
    }

    suspend fun upgradeToTls(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentSocket = socket ?: run {
                Log.e(TAG, "Cannot upgrade to TLS: Socket is null")
                return@withContext false
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val tlsConfig = TLSConfigBuilder().apply {
                // Optional: Configure cipher suites
            }.build()
            val tlsSocket = currentSocket.tls(Dispatchers.IO, tlsConfig)
            socket = tlsSocket
            reader = tlsSocket.openReadChannel()
            writer = tlsSocket.openWriteChannel(autoFlush = true)
            if (reader == null || writer == null) {
                Log.e(TAG, "Failed to initialize reader or writer after TLS upgrade")
                socket?.close()
                socket = null
                reader = null
                writer = null
                return@withContext false
            }
            Log.d(TAG, "Socket upgraded to TLS for $host:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upgrade socket to TLS: $e")
            false
        }
    }

    suspend fun write(message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            writer?.let {
                if (it.isClosedForWrite) {
                    Log.e(TAG, "Writer channel is closed")
                    return@withContext false
                }
                it.writeStringUtf8(message)
//                val bytes = message.toByteArray(StandardCharsets.UTF_8)
//                Log.d(TAG, "Raw bytes to send: ${bytes.joinToString(", ")}")
//                it.writeFully(bytes, 0, bytes.size)
                Log.d(TAG, "Sent message: $message")
//                it.flush()
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
//                    Log.w(TAG, "Reader channel is closed before read attempt")
                    return@withContext null
                }
                val buffer = StringBuilder()
                val tempBuffer = ByteArray(1024)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
//                        channel.
                        val bytesRead = channel.readAvailable(tempBuffer)
                        when {
                            bytesRead == -1 -> {
                                Log.w(TAG, "Socket closed by remote peer")
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
                                Log.d(TAG, "No data available, continuing")
                            }
                        }
                        delay(10)
                    } catch (e: IOException) {
                        Log.w(TAG, "Read error: $e")
                        return@withContext null
                    } catch (e: ClosedReceiveChannelException) {
                        Log.w(TAG, "Reader channel closed: $e")
                        return@withContext null
                    }
                }
                val message = buffer.toString()
                if (message.isNotEmpty()) {
                    Log.d(TAG, "Accumulated partial message: $message")
                    message
                } else {
                    Log.d(TAG, "No data within $timeoutMs ms")
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
            val streamHeader = ClientStreamHeader(
                from = jid,
                to = domain,
                version = "1.0",
                xmlLang = "en",
                xmlns = "jabber:client"
            )
            val xml = XML {
                indent = 0
                xmlDeclMode = XmlDeclMode.None
                autoPolymorphic = true
            }
            val xmlContent = "<?xml version='1.0'?>\n" + xml.encodeToString(ClientStreamHeader.serializer(), streamHeader)
            Log.d(TAG, "Serialized stream header: $xmlContent")
            socket.write(xmlContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending stream header: $e")
        }
    }

    suspend fun readServerResponse(socket: Socket): StreamResponse? {
        // Deprecated since we’re using messageCallback; keep for reference
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

    suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket?.let {
                if (!it.isClosed) {
                    writer?.let { writer ->
                        if (!writer.isClosedForWrite) {
                            writer.writeFully("</stream:stream>".toByteArray(StandardCharsets.UTF_8), 0, 16)
                            Log.d(TAG, "Sent message: </stream:stream>")
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
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: $e")
        }
    }

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