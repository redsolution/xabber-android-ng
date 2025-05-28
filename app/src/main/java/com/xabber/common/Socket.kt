package com.xabber.common

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToString
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.nio.charset.StandardCharsets

@Serializable
@XmlSerialName("stream", "http://etherx.jabber.org/streams", "stream")
data class ClientStreamHeader(
    val from: String,
    val to: String,
    val version: String = "1.0",
    @XmlSerialName("xmlns", "", "")
    val xmlns: String = "jabber:client"
)

@Serializable
@XmlSerialName("stream", "http://etherx.jabber.org/streams", "stream")
data class ServerStreamHeader(
    val id: String,
    val version: String,
    @XmlSerialName("xmlns:stream", "", "")
    val xmlnsStream: String,
    @XmlSerialName("xmlns", "", "")
    val xmlns: String,
    val from: String? = null,
    val to: String? = null,
    @XmlSerialName("xml:lang", "http://www.w3.org/XML/1998/namespace", "xml")
    val xmlLang: String? = null
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
            Log.e(TAG, "Error connecting to $host:$port: ${e.message}", e)
            false
        }
    }

    suspend fun write(message: String, domain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            writer?.let {
                // Log raw bytes for debugging
//                val bytes = message.toByteArray(StandardCharsets.UTF_8)
//                Log.d(TAG, "Raw bytes to send: ${bytes.joinToString(", ") { it.toString() }}")
                it.writeString(message)
                Log.d(TAG, "Sent message: $message")
                return@withContext true
            }
            Log.e(TAG, "Cannot send: Socket writer is null")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
            false
        }
    }

    suspend fun sendStreamHeader(socket: Socket, domain: String, jid: String) {
        val streamHeader = ClientStreamHeader(
            from = jid,
            to = domain,
            version = "1.0",
            xmlns = "jabber:client"
        )

        val xml = XML {
            indent = 0 // Avoid extra whitespace
            xmlDeclMode = XmlDeclMode.None // No XML declaration
            autoPolymorphic = true
        }
        val xmlContent = xml.encodeToString(ClientStreamHeader.serializer(), streamHeader)
        Log.d(TAG, "Serialized stream header: $xmlContent")
        socket.write(xmlContent, domain)
    }

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            reader?.let { channel ->
                val buffer = StringBuilder()
                val tempBuffer = ByteArray(1024)
                var openTags = 0
                var inTag = false
                while (true) {
                    val bytesRead = channel.readAvailable(tempBuffer)
                    if (bytesRead == -1) {
                        Log.e(TAG, "Socket closed by remote peer before receiving response")
                        return@withContext null
                    }
                    val chunk = tempBuffer.decodeToString(0, bytesRead)
                    buffer.append(chunk)

                    // Count tags to detect complete stream header or error
                    for (char in chunk) {
                        if (char == '<' && !inTag) {
                            inTag = true
                        } else if (char == '>' && inTag) {
                            inTag = false
                            if (chunk.contains("<stream:stream")) {
                                openTags++
                            } else if (chunk.contains("</stream:stream")) {
                                openTags--
                            }
                        }
                    }

                    // Break if we have the stream header (open tag only)
                    if (openTags == 1 && buffer.contains("<stream:stream") && !inTag &&
                        (buffer.contains("/>") || buffer.contains(">"))) {
                        // Extract only the stream header
                        val endIndex = buffer.indexOf(">", buffer.indexOf("<stream:stream")) + 1
                        val header = buffer.substring(0, endIndex)
                        Log.d(TAG, "Extracted stream header: $header")
                        return@withContext header
                    }
                    // Break if an error is detected
                    if (buffer.contains("<stream:error")) {
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
            Log.e(TAG, "Error reading from socket: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun readServerResponse(socket: Socket): ServerStreamHeader? {
        val response = socket.read() ?: return null
        try {
            // Check for stream error
            if (response.contains("<stream:error")) {
                Log.e(TAG, "Server responded with stream error: $response")
                return null
            }

            // Remove XML declaration and normalize response
            val xmlContent = response.replace(Regex("""<\?xml[^?]+\?>"""), "").trim()
            val xml = XML {
                indent = 2
                autoPolymorphic = true
            }
            val parsed = xml.decodeFromString(ServerStreamHeader.serializer(), xmlContent)
            Log.d(TAG, "Parsed ServerStreamHeader: $parsed")
            return parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse server response: ${e.message}\nRaw response: $response", e)
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
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }

    suspend fun initiateXmppStream(socket: Socket, domain: String, jid: String): ServerStreamHeader? {
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