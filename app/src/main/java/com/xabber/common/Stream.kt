package com.xabber.common

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.xmpp.XEP_0CCC.ClientSynchronizationManager
import com.xabber.xmpp.auth.DevicesOCRA
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.dns.DNSResolver
import com.xabber.xmpp.presence.PresenceManager
import com.xabber.xmpp.roster.RosterManager
import io.ktor.network.sockets.isClosed
import io.reactivex.subjects.BehaviorSubject
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope

enum class StreamState {
    NOT_CONNECTING,
    STREAM_OPEN,
    START_TLS,
    PROCEED,
    START_AUTH,
    PROCESS_AUTH,
    AUTH_SUCCESS,
    AUTH_FAILED,
    DEVICE_REGISTRATION,
    BINDING,
    CONNECTED
}

@RequiresApi(Build.VERSION_CODES.O)
class Stream(var jid: String, var port: Int = 5222) {
    var delegate: XMPPStreamDelegate? = null
    @io.realm.kotlin.types.annotations.PrimaryKey
    var host: String = extractHostFromJid(jid)
    var remoteAddress: String = ""
    var socket: Socket? = null
    private val connectionLock = Any()
    private var isConnecting = false
    private val streamBuffer = StringBuilder()
    val messageCallbackChannel = Channel<String>(Channel.UNLIMITED)
    var state: StreamState = StreamState.NOT_CONNECTING
        set(value) {
            field = value
            Log.d(TAG, "Transitioned to state: $value")
            runBlocking(Dispatchers.IO) {
                when (value) {
                    StreamState.NOT_CONNECTING -> onNotConnecting()
                    StreamState.STREAM_OPEN -> onStreamOpen()
                    StreamState.START_TLS -> delegate?.streamStartTLS(this@Stream)
                    StreamState.PROCEED -> onProceed()
                    StreamState.START_AUTH -> delegate?.streamOCRAAuth(this@Stream)
                    StreamState.PROCESS_AUTH -> onProcessAuth()
                    StreamState.AUTH_SUCCESS -> delegate?.streamAuthSuccess(this@Stream)
                    StreamState.AUTH_FAILED -> delegate?.streamAuthFailed(this@Stream)
                    StreamState.DEVICE_REGISTRATION -> delegate?.streamDeviceRegistration(this@Stream)
                    StreamState.BINDING -> delegate?.streamBinding(this@Stream)
                    StreamState.CONNECTED -> delegate?.streamDidConnect(this@Stream)
                }
            }
        }
    private val TAG = "Stream"
    private var onErrorCallback: ((String) -> Unit)? = null

    fun setOnErrorCallback(callback: (String) -> Unit) {
        onErrorCallback = callback
    }

    fun extractHostFromJid(jid: String): String {
        try {
            val parts = jid.split("@")
            if (parts.size > 1) {
                return parts[1].split("/").first()
            }
            Log.w(TAG, "Invalid JID format: $jid")
            return jid
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting host from JID: ${e.message}", e)
            return jid
        }
    }

    suspend fun connect(): String? = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
            if (isConnecting) {
                Log.w(TAG, "Connect already in progress for $jid, ignoring")
                return@withContext "Connect already in progress"
            }
            isConnecting = true
        }
        try {
            Log.d(TAG, "Connection attempt for JID: $jid")
            state = StreamState.NOT_CONNECTING
            val resolver = DNSResolver()
            val result = resolver.resolveSRV(host)
            if (result == null) {
                Log.e(TAG, "DNS resolution failed for host $host")
                return@withContext "DNS resolution failed"
            }
            remoteAddress = result.first
            port = result.second
            Log.d(TAG, "Resolved IP: $remoteAddress, Port: $port")
            socket?.close()
            socket = Socket(remoteAddress, port)
            socket?.setMessageCallback { message ->
                CoroutineScope(Dispatchers.IO).launch {
                    Log.d(TAG, "Received message via callback: ${message.substring(0, minOf(message.length, 200))}...")
                    messageCallbackChannel.send(message)
                    handleIncomingMessage(message)
                }
            }
            if (socket?.connect(remoteAddress, port) != true) {
                Log.e(TAG, "Socket connection failed for $remoteAddress:$port")
                socket?.close()
                socket = null
                return@withContext "Socket connection failed"
            }
            Log.d(TAG, "Socket connected successfully for $remoteAddress:$port")
            // REMOVE this block: Don't call streamDidConnect here
            // if (delegate?.streamDidConnect(this@Stream) == true) { ... }
            socket?.initiateXmppStream(socket!!, host, jid)  // Just send the stream header
            Log.d(TAG, "XMPP stream initiation started, waiting for server response")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $host: ${e.message}", e)
            socket?.close()
            socket = null
            state = StreamState.NOT_CONNECTING
            return@withContext "Connection failed: ${e.message}"
        } finally {
            synchronized(connectionLock) {
                isConnecting = false
            }
        }
    }

    private suspend fun handleIncomingMessage(chunk: String) {
        try {
            streamBuffer.append(chunk)
            var content = streamBuffer.toString()
            while (content.isNotEmpty()) {
                val start = content.indexOf("<")
                if (start == -1) break
                if (content.startsWith("<?xml") || content.indexOf("<stream:stream", start) == start || content.indexOf("<stream:error", start) == start || content.indexOf("<stream:features", start) == start) {
                    // Handle header/features/error as special (find end if needed)
                    var end = content.indexOf(">", start)
                    if (end == -1) break
                    if (content.indexOf("<stream:features>", start) != -1) {
                        end = content.indexOf("</stream:features>", end)
                        if (end == -1) break
                        end += "</stream:features>".length
                    } else if (content.indexOf("</stream:stream>", start) != -1) {
                        end = content.indexOf("</stream:stream>", end)
                        if (end == -1) break
                        end += "</stream:stream>".length
                    } else if (content.indexOf("</stream:error>", start) != -1) {
                        end = content.indexOf("</stream:error>", end)
                        if (end == -1) break
                        end += "</stream:error>".length
                    }
                    val header = content.substring(start, end)
                    processStanza(header)
                    content = content.substring(end)
                    continue
                }
                val tagEnd = content.indexOf(">", start)
                if (tagEnd == -1) break
                val fullTag = content.substring(start + 1, tagEnd)
                val tagName = fullTag.split(Regex("\\s+"))[0]
                val isSelfClosing = fullTag.endsWith("/")
                val stanzaEnd: Int
                val fullEnd: Int
                if (isSelfClosing) {
                    stanzaEnd = tagEnd
                    fullEnd = stanzaEnd + 1  // After >
                } else {
                    val closeTag = "</$tagName>"
                    stanzaEnd = content.indexOf(closeTag, tagEnd)
                    if (stanzaEnd == -1) break
                    fullEnd = stanzaEnd + closeTag.length
                }
                val stanza = content.substring(start, fullEnd)
                processStanza(stanza)
                content = content.substring(fullEnd)
            }
            streamBuffer.clear()
            streamBuffer.append(content)  // Save partial remainder
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
            onErrorCallback?.invoke("Error processing server response: ${e.message}")
            state = StreamState.NOT_CONNECTING
        }
    }

    private fun parseIQ(stanza: String): XMPPIQ? {
        try {
            val typeMatch =
                Regex("""type=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1) ?: return null
            val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)

            val error = if (typeMatch == "error") {
                val errorStart = stanza.indexOf("<error")
                if (errorStart != -1) {
                    val errorEnd = stanza.indexOf("</error>", errorStart) + 8
                    stanza.substring(errorStart, errorEnd)
                } else null
            } else null

            val iqStart = stanza.indexOf("<iq")
            val headerEnd = stanza.indexOf(">", iqStart)
            val iqEnd = stanza.lastIndexOf("</iq>")
            val content =
                if (headerEnd != -1 && iqEnd > headerEnd + 1) stanza.substring(headerEnd + 1, iqEnd)
                    .trim() else ""

            val queryNamespace = if (content.isNotEmpty()) {
                val childStart = content.indexOf("<")
                if (childStart != -1) {
                    val childHeaderEnd = content.indexOf(">", childStart)
                    Regex("""xmlns=['"]([^'"]+)['"]""").find(
                        content.substring(
                            childStart,
                            childHeaderEnd + 1
                        )
                    )?.groupValues?.get(1)
                } else null
            } else null

            return XMPPIQ(
                stanza,
                typeMatch,
                idMatch,
                fromMatch,
                toMatch,
                error,
                queryNamespace,
                content
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IQ: ${e.message}", e)
            return null
        }
    }

    private suspend fun processStanza(stanza: String) {
        if (stanza.startsWith("<iq")) {
            val iq = parseIQ(stanza)
            if (iq != null) {
                delegate?.didReceiveIQ(iq, this)
            } else {
                Log.w(TAG, "Failed to parse IQ stanza: $stanza")
            }
        } else if (stanza.contains("<stream:stream") && !stanza.contains("<stream:features")) {
            delegate?.didReceiveStreamHeader(stanza, this)
        } else if (stanza.contains("<stream:features>")) {
            delegate?.didReceiveStreamFeatures(stanza, this)
        } else if (stanza.contains("<challenge")) {
            delegate?.didReceiveChallenge(stanza, this)
        } else if (stanza.contains("<success")) {
            delegate?.didReceiveSuccess(stanza, this)
        } else if (stanza.contains("<failure")) {
            delegate?.didReceiveFailure(stanza, this)
        } else if (stanza.contains("<proceed")) {
            delegate?.didReceiveProceed(stanza, this)
        } else if (stanza.contains("<presence")) {
            delegate?.didReceivePresence(stanza, this)
        } else if (stanza.contains("<message")) {
            delegate?.didReceiveMessage(stanza, this)
        } else if (stanza.contains("<stream:error")) {
            Log.e(TAG, "Received stream error: $stanza")
            onErrorCallback?.invoke("Stream error occurred")
            state = StreamState.NOT_CONNECTING
        } else if (stanza.contains("</stream:stream>")) {
            Log.w(TAG, "Received stream termination")
            onErrorCallback?.invoke("Connection closed by server")
            state = StreamState.NOT_CONNECTING
        } else {
            Log.w(TAG, "Unhandled stanza: $stanza")
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
            socket = null
            state = StreamState.NOT_CONNECTING
            messageCallbackChannel.close()
            Log.d(TAG, "Stream closed for $jid")
        }
        socket?.close()
    }

    fun logout(jid: String) {
        if (this.jid == jid) {
            runBlocking(Dispatchers.IO) {
                close()
            }
        }
    }

    fun extractUsernameFromJid(jid: String): String {
        try {
            val parts = jid.split("@")
            return if (parts.size > 1) parts[0] else jid
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting username from JID: ${e.message}", e)
            return jid
        }
    }

//    fun getSocket(): Socket? = socket

    open fun onNotConnecting() {}

    open suspend fun onStreamOpen() {}

    open suspend fun onProceed() {
        Log.d(TAG, "Awaiting stream features after TLS upgrade for JID: $jid")
    }

    open suspend fun onProcessAuth() {
        Log.d(TAG, "Awaiting authentication response for JID: $jid")
    }

    open suspend fun onConnected() {
        // Logic moved to Account's onConnected
    }
}