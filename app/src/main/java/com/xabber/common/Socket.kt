package com.xabber.common

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToString
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlElement
import java.nio.charset.StandardCharsets

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
    private val TAG = "Socket_ng"

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = aSocket(selectorManager).tcp().connect(host, port)
            reader = socket?.openReadChannel()
            writer = socket?.openWriteChannel(autoFlush = true)
            Log.d(TAG, "Ktor TCP socket connected to $host:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host:$port: $e")
            false
        }
    }

    suspend fun write(message: String, domain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            writer?.let {
                val bytes = message.toByteArray(StandardCharsets.UTF_8)
                Log.d(TAG, "Raw bytes to send: ${bytes.joinToString(", ") { it.toString() }}")
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
            socket.write(xmlContent, domain)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending stream header: $e")
        }
    }

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            reader?.let { channel ->
                val buffer = StringBuilder()
                val tempBuffer = ByteArray(1024)
                while (true) {
                    val bytesRead = channel.readAvailable(tempBuffer)
                    if (bytesRead == -1) {
                        Log.d(TAG, "Socket closed by remote peer")
                        return@withContext if (buffer.isEmpty()) null else buffer.toString()
                    }
                    if (bytesRead == 0) {
                        Log.d(TAG, "No data available, continuing to read")
                        continue
                    }
                    val chunk = tempBuffer.decodeToString(0, bytesRead)
                    buffer.append(chunk)
                    Log.d(TAG, "Read chunk: $chunk")

                    // Check for complete stanza
                    if (buffer.contains("</stream:stream>") ||
                        buffer.contains("</stream:features>") ||
                        buffer.contains("</stream:error>")) {
                        break
                    }
                }
                val message = buffer.toString()
                Log.d(TAG, "Received message: $message")
                return@withContext message
            }
            Log.e(TAG, "Cannot read: Socket reader is null")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from socket: $e")
            null
        }
    }

    suspend fun readServerResponse(socket: Socket): StreamResponse? {
        val response = socket.read() ?: return null
        try {
            if (response.contains("<stream:error")) {
                Log.e(TAG, "Server responded with stream error: $response")
                return null
            }

            val xmlContent = response.replace(Regex("""<?xml[^>]+?>"""), "").trim()
            Log.d(TAG, "Processing XML content: $xmlContent")
            val xml = XML {
                indent = 2
                autoPolymorphic = true
                defaultPolicy {
                    ignoreUnknownChildren()
                    pedantic = false
                }
            }
            // Extract attributes with regex
            Log.d(TAG, "Extracting stream:stream attributes")
            val headerMatch = Regex("""<stream:stream\s+([^>]+)>""").find(xmlContent)
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

            // Parse features
            val featuresMatch = Regex("""<stream:features([^>]*)>(.*?)</stream:features>""", RegexOption.DOT_MATCHES_ALL).find(xmlContent)
            val features = if (featuresMatch != null) {
                Log.d(TAG, "Features match found: ${featuresMatch.value}")
                try {
                    // Add xmlns:stream to the features tag
                    val featuresAttrs = featuresMatch.groupValues[1]
                    val featuresContent = featuresMatch.groupValues[2]
                    val featuresXml = if (featuresAttrs.contains("xmlns:stream")) {
                        "<stream:features$featuresAttrs>$featuresContent</stream:features>"
                    } else {
                        "<stream:features$featuresAttrs xmlns:stream=\"http://etherx.jabber.org/streams\">$featuresContent</stream:features>"
                    }
                    Log.d(TAG, "Parsing features XML: $featuresXml")
                    xml.decodeFromString(StreamFeatures.serializer(), featuresXml)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse features: $e")
                    null
                }
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
        return@withContext write(ping, host)
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket?.let {
                write("</stream:stream>", host)
                it.close()
                Log.d(TAG, "Ktor TCP socket closed for $host:$port")
            }
            socket = null
            reader = null
            writer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: $e")
        }
    }

    suspend fun initiateXmppStream(socket: Socket, domain: String, jid: String): StreamResponse? {
        sendStreamHeader(socket, domain, jid)
        val response = readServerResponse(socket)
        if (response == null) {
            Log.e(TAG, "Stream initiation failed for $jid")
        } else {
            Log.d(TAG, "Stream initiated successfully for $jid")
        }
        return response
    }

    fun getSocket(): io.ktor.network.sockets.Socket? {
        return socket
    }
}