package com.xabber.common

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToString
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("stream:stream")
data class ClientStreamHeader(
     val from: String,
     val to: String,
     val version: String = "1.0",
     val xmlns: String = "jabber:client",
     val xmlnsStream: String = "http://etherx.jabber.org/streams"
)

@Serializable
@XmlSerialName("stream:stream")
data class ServerStreamHeader(
     val id: String,
     val version: String,
     val xmlnsStream: String,
     val xmlns: String,
     val from: String? = null
)


class Socket(private val host: String, private val port: Int) {
    private var socket: io.ktor.network.sockets.Socket? = null
    private var reader: ByteReadChannel? = null
    private var writer: ByteWriteChannel? = null
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val TAG = "Socket_ng"

    // Connects to the specified host and port and sends XMPP stream opening
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = aSocket(selectorManager).tcp().connect(host, port)
            reader = socket?.openReadChannel()
            writer = socket?.openWriteChannel(autoFlush = true)
            Log.d(TAG, "Ktor TCP socket connected to $host:$port")

            // Send XMPP stream opening tag
            val streamOpen = """<?xml version='1.0'?>
                <stream:stream to='$host' version='1.0' xml:lang='en' 
                xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>"""
            return@withContext write(streamOpen, host)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host:$port: ${e.message}", e)
            false
        }
    }

    // Writes a string to the socket
    suspend fun write(message: String, domain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            writer?.let {
                it.writeString(message)
                Log.d(TAG, "Sent message: $message")
                return@withContext true
            }
            Log.e(TAG, "Cannot write: Socket writer is null")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to socket: ${e.message}", e)
            false
        }
    }

    suspend fun sendStreamHeader(socket: Socket, domain: String, jid: String) {
        // Create the client stream header
        val streamHeader = ClientStreamHeader(from = jid,to = domain )

        // Serialize the stream header to XML
        val xmlContent = encodeToString(ClientStreamHeader.serializer(), streamHeader)

        // Prepend the XML declaration
        val fullXml = "<?xml version='1.0' encoding='UTF-8'?>" + xmlContent

        // Send the XML over the socket
        socket.write(fullXml, host)
    }

    // Reads a string from the socket until a complete XML tag is received
    suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            reader?.let { channel ->
                val buffer = StringBuilder()
                val tempBuffer = ByteArray(1024)
                while (true) {
                    val bytesRead = channel.readAvailable(tempBuffer)
                    if (bytesRead == -1) {
                        Log.d(TAG, "Socket closed by remote peer")
                        return@withContext null
                    }
                    val chunk = tempBuffer.decodeToString(0, bytesRead)
                    buffer.append(chunk)
                    // Check for complete XML tag (simplified: assumes tag ends with '>')
                    if (chunk.contains(">")) {
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
            null
        }
    }

    suspend fun readServerResponse(socket: Socket): ServerStreamHeader? {
        // Read the server's response as a string
        val response = socket.read() ?: return null

        // Strip the XML declaration if present
        val xmlContent = response.replace(Regex("""<\?xml[^?]+\?>"""), "").trim()

        // Deserialize the XML content into a ServerStreamHeader object
        return decodeFromString(ServerStreamHeader.serializer(), xmlContent)
    }

    // Sends a sample XMPP IQ ping stanza
    suspend fun sendPing(jid: String): Boolean = withContext(Dispatchers.IO) {
        val ping = """
            <iq type='get' id='ping1' to='$jid'>
                <ping xmlns='urn:xmpp:ping'/>
            </iq>"""
        return@withContext write(ping, host)
    }

    // Closes the socket if open
    suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket?.let {
                // Send stream closing tag
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
        return readServerResponse(socket)
    }

    // Getter for the Ktor socket
    fun getSocket(): io.ktor.network.sockets.Socket? {
        return socket
    }
}