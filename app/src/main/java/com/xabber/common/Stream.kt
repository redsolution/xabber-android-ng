package com.xabber.common

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.utils.parseTimestamp
import com.xabber.utils.toMessageReferenceDto
import com.xabber.xmpp.dns.DNSResolver
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.message.TemporaryMessageStanzaStorageItem
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager.TemporaryMessageReceiver
import com.xabber.xmpp.messages.messages_manager.MessageCommonReceiver
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

// Define ProcessedMessageId as a regular class
class ProcessedMessageId : RealmObject {
    @PrimaryKey
    var messageId: String = ""
    var owner: String = ""
    var timestamp: Long = 0

    companion object {
        private const val TAG = "ProcessedMessageid"

        fun genPrimary(messageId: String, owner: String): String {
            return "${messageId}_$owner"
        }
    }
}

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
    private val bufferMutex = Mutex()
    var temporaryMessageReceiver: TemporaryMessageReceiver? = null

    val messageCallbackChannel = Channel<String>(Channel.UNLIMITED)
    val messageQueue = Channel<MessageQueueItem>(Channel.UNLIMITED)
    private val queueMutex = Mutex()
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

    data class MessageQueueItem(
        val stanza: String,
        val message: XMPPMessage,
        val isCarbon: Boolean,
        val isArchived: Boolean,
        val isClientSync: Boolean,
        val timestamp: Long,
        val queryId: String?
    )

    init {
        CoroutineScope(Dispatchers.IO).launch {
            clearStaleProcessedMessages() // Add this to clear stale entries on initialization
            processMessageQueue()

        }
        deleteSelfChats()

    }
    suspend fun clearStaleProcessedMessages() {
        val realm = Realm.open(defaultRealmConfig())
        realm.write {
            val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000 // 24 hours
            val stale = query<ProcessedMessageId>("owner = $0 AND timestamp < $1", jid, threshold).find()
            delete(stale)
            Log.d(TAG, "Cleared ${stale.size} stale ProcessedMessageId entries for owner=$jid")
        }
        realm.close()
    }
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
                    Log.d(TAG, "Received message via callback: ${message.take(200)}")
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
            socket?.initiateXmppStream(socket!!, host, jid)
            Log.d(TAG, "XMPP stream initiation started, waiting for server response")
            clearStaleTemporaryMessages()
            logUnprocessedMessages(jid)
            retryUnprocessedMessages()
            debugDatabaseState()
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
        bufferMutex.withLock {
            streamBuffer.append(chunk)
            var content = streamBuffer.toString()
            var processedStanzas = 0
            while (content.isNotEmpty() && processedStanzas < 10) {
                val start = content.indexOf("<")
                if (start == -1) {
                    Log.w(TAG, "No XML start tag found in buffer, waiting for more data: ${content.take(200)}")
                    break
                }

                if (content.startsWith("<?xml", start) || content.indexOf("<stream:stream", start) == start ||
                    content.indexOf("<stream:error", start) == start || content.indexOf("<stream:features", start) == start) {
                    var end = content.indexOf(">", start)
                    if (end == -1) {
                        break
                    }
                    if (content.indexOf("<stream:features>", start) != -1) {
                        end = content.indexOf("</stream:features>", end)
                        if (end == -1) {
                            break
                        }
                        end += "</stream:features>".length
                    } else if (content.indexOf("</stream:stream>", start) != -1) {
                        end = content.indexOf("</stream:stream>", end)
                        if (end == -1) {
                            Log.w(TAG, "Incomplete stream:stream, buffering: ${content.take(200)}")
                            break
                        }
                        end += "</stream:stream>".length
                    } else if (content.indexOf("</stream:error>", start) != -1) {
                        end = content.indexOf("</stream:error>", end)
                        if (end == -1) {
                            Log.w(TAG, "Incomplete stream:error, buffering: ${content.take(200)}")
                            break
                        }
                        end += "</stream:error>".length
                    }
                    val header = content.substring(start, end)
                    processStanza(header)
                    content = content.substring(end).trimStart()
                    processedStanzas++
                    continue
                }

                val tagEnd = content.indexOf(">", start)
                if (tagEnd == -1) {
                    Log.w(TAG, "Incomplete stanza tag, buffering: ${content.take(200)}")
                    break
                }
                val fullTag = content.substring(start + 1, tagEnd)
                val tagName = fullTag.split(Regex("\\s+"))[0]
                val isSelfClosing = fullTag.endsWith("/")
                val stanzaEnd: Int
                val fullEnd: Int
                if (isSelfClosing) {
                    stanzaEnd = tagEnd
                    fullEnd = stanzaEnd + 1
                } else {
                    var openTags = 1
                    var currentIndex = tagEnd + 1
                    while (openTags > 0 && currentIndex < content.length) {
                        val nextOpen = content.indexOf("<$tagName", currentIndex)
                        val nextClose = content.indexOf("</$tagName>", currentIndex)
                        if (nextClose == -1) {
                            break
                        }
                        if (nextOpen != -1 && nextOpen < nextClose) {
                            openTags++
                            currentIndex = content.indexOf(">", nextOpen) + 1
                        } else {
                            openTags--
                            currentIndex = nextClose + "</$tagName>".length
                            if (openTags == 0) break
                        }
                    }
                    if (openTags == 0) {
                        stanzaEnd = currentIndex - "</$tagName>".length
                        fullEnd = currentIndex
                    } else {
                        Log.w(TAG, "Incomplete stanza for $tagName, buffering: ${content.take(200)}")
                        break
                    }
                }
                val stanza = content.substring(start, fullEnd)
                if (tagName == "message" && (stanza.contains("urn:xmpp:mam:2") || stanza.contains("urn:xmpp:mam:tmp") || stanza.contains("urn:xmpp:last-message"))) {
                    processMAMStanza(stanza)
                } else if (tagName == "presence") {
                    val presence = parsePresenceStanza(stanza)
                    if (presence != null) {
                        delegate?.didReceivePresence(presence, this@Stream)
                    } else {
                        Log.w(TAG, "Failed to parse presence stanza: ${stanza.take(200)}")
                    }
                } else {
                    processStanza(stanza)
                }
                content = content.substring(fullEnd).trimStart()
                processedStanzas++
            }
            if (streamBuffer.length > 1024 * 1024) {
                Log.w(TAG, "Stream buffer size exceeded 1MB, clearing older data")
                streamBuffer.delete(0, streamBuffer.length - 1024 * 1024)
            } else {
                streamBuffer.clear()
                streamBuffer.append(content)
            }
        }
    }

    private fun parsePresenceStanza(stanza: String): XMPPPresence? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(stanza))
            var eventType = parser.eventType
            var type: String? = null
            var from: String? = null
            var to: String? = null
            var id: String? = null
            var show: String? = null
            var status: String? = null
            var priority: Int? = null
            var deviceId: String? = null
            var timestamp: Long = 0 // Renamed from delayStamp

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        val namespace = parser.namespace
                        when {
                            tagName == "presence" && (namespace == "jabber:client" || namespace.isEmpty()) -> {
                                type = parser.getAttributeValue(null, "type")
                                from = parser.getAttributeValue(null, "from")
                                to = parser.getAttributeValue(null, "to")
                                id = parser.getAttributeValue(null, "id")
                            }
                            tagName == "show" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    show = parser.text.trim()
                                }
                            }
                            tagName == "status" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    status = parser.text.trim()
                                }
                            }
                            tagName == "priority" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    priority = parser.text.toIntOrNull()
                                }
                            }
                            tagName == "device" && namespace == "https://xabber.com/protocol/devices" -> {
                                deviceId = parser.getAttributeValue(null, "id")
                            }
                            tagName == "delay" && namespace == "urn:xmpp:delay" -> {
//                                timestamp = parser.getAttributeValue(null, "stamp")
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            return XMPPPresence(
                raw = stanza,
                type = type,
                from = from,
                to = to,
                id = id,
                show = show,
                status = status,
                priority = priority,
                deviceId = deviceId,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing presence stanza: ${e.message}, stanza=$stanza", e)
            return null
        }
    }

    private suspend fun processMAMStanza(stanza: String) {
        try {
            val xmppMessage = parseMessageStanza(stanza)
            if (xmppMessage == null) {
                Log.w(TAG, "Failed to parse MAM message stanza: $stanza")
                return
            }

            val messageId = xmppMessage.id ?: return
            val primary = ProcessedMessageId.genPrimary(messageId, jid)
            val realm = Realm.open(defaultRealmConfig())
            val existing = realm.query<ProcessedMessageId>("messageId = $0 AND owner = $1", messageId, jid).first().find()
            if (existing != null) {
                realm.close()
                return
            }
            val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
            val existingMsg = realm.query<MessageStorageItem>("primary = $0 OR (archivedId = $1 AND archivedId != '')", msgPrimary, messageId).first().find()
            if (existingMsg != null) {
                realm.write {
                    copyToRealm(ProcessedMessageId().apply {
                        this.messageId = messageId
                        this.owner = jid
                        this.timestamp = System.currentTimeMillis()
                    }, UpdatePolicy.ALL)
                }
                realm.close()
                return
            }
            realm.close()

            delegate?.didReceiveMessage(xmppMessage, this@Stream)

            val isCarbon = xmppMessage.hasElement("sent", "urn:xmpp:carbons:2") || xmppMessage.hasElement("received", "urn:xmpp:carbons:2")
            val isArchived = stanza.contains("urn:xmpp:mam:2") || stanza.contains("urn:xmpp:mam:tmp")
            val isClientSync = xmppMessage.hasElement("synchronization", "https://xabber.com/protocol/synchronization")
            val queryId = xmppMessage.element("result", "urn:xmpp:mam:2")?.getAttribute("queryid")
            val timestamp = parseTimestamp(xmppMessage, TAG) ?: System.currentTimeMillis()
            val queueItem = MessageQueueItem(
                stanza = stanza,
                message = xmppMessage,
                isCarbon = isCarbon,
                isArchived = isArchived,
                isClientSync = isClientSync,
                timestamp = timestamp,
                queryId = queryId
            )
            if (queueItem.message.id != null && queueItem.message.from != null && queueItem.message.to != null) {
                messageQueue.send(queueItem)
                Log.d(TAG, "Enqueued MAM message: id=$messageId, isCarbon=$isCarbon, isArchived=$isArchived, isClientSync=$isClientSync")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MAM stanza: ${e.message}, stanza=$stanza", e)
            onErrorCallback?.invoke("Error processing MAM stanza: ${e.message}")
        }
    }

    private suspend fun processStanza(stanza: String) {
        Log.d(TAG, "Received stanza: ${stanza.take(200)}")
        try {
            when {
                stanza.startsWith("<message") -> {
                    val xmppMessage = parseMessageStanza(stanza)
                    if (xmppMessage != null) {
                        delegate?.didReceiveMessage(xmppMessage, this@Stream)
                        val isCarbon = xmppMessage.hasElement("sent", "urn:xmpp:carbons:2") || xmppMessage.hasElement("received", "urn:xmpp:carbons:2")
                        val isArchived = stanza.contains("urn:xmpp:mam:tmp")
                        val isClientSync = xmppMessage.hasElement("synchronization", "https://xabber.com/protocol/synchronization")
                        val queryId = xmppMessage.element("result", "urn:xmpp:mam:2")?.getAttribute("queryid")
                        val timestamp = parseTimestamp(xmppMessage, TAG) ?: System.currentTimeMillis()
                        val queueItem = MessageQueueItem(
                            stanza = stanza,
                            message = xmppMessage,
                            isCarbon = isCarbon,
                            isArchived = isArchived,
                            isClientSync = isClientSync,
                            timestamp = timestamp,
                            queryId = queryId
                        )
                        if (queueItem.message.id != null && queueItem.message.from != null && queueItem.message.to != null) {
                            messageQueue.send(queueItem)
                            Log.d(TAG, "Enqueued message: id=${xmppMessage.id}, isCarbon=$isCarbon, isArchived=$isArchived, isClientSync=$isClientSync")
                        }
                    } else {
                        Log.w(TAG, "Failed to parse message stanza: $stanza")
                    }
                }
                stanza.startsWith("<iq") -> {
                    val iq = parseIQ(stanza)
                    if (iq != null) {
                        Log.d(TAG, "Parsed IQ stanza: id=${iq.id}")
                        delegate?.didReceiveIQ(iq, this@Stream)
                    } else {
                        Log.w(TAG, "Failed to parse IQ stanza: $stanza")
                    }
                }
                stanza.contains("<stream:stream") && !stanza.contains("<stream:features") -> {
                    Log.d(TAG, "Received stream header: $stanza")
                    delegate?.didReceiveStreamHeader(stanza, this)
                }
                stanza.contains("<stream:features>") -> {
                    Log.d(TAG, "Received stream features: $stanza")
                    delegate?.didReceiveStreamFeatures(stanza, this)
                }
                stanza.contains("<challenge") -> {
                    Log.d(TAG, "Received challenge: $stanza")
                    delegate?.didReceiveChallenge(stanza, this)
                }
                stanza.contains("<success") -> {
                    Log.d(TAG, "Received success: $stanza")
                    delegate?.didReceiveSuccess(stanza, this)
                }
                stanza.contains("<failure") -> {
                    Log.d(TAG, "Received failure: $stanza")
                    delegate?.didReceiveFailure(stanza, this)
                }
                stanza.contains("<proceed") -> {
                    Log.d(TAG, "Received proceed: $stanza")
                    delegate?.didReceiveProceed(stanza, this)
                }
                stanza.contains("<stream:error") -> {
                    Log.e(TAG, "Received stream error: $stanza")
                    onErrorCallback?.invoke("Stream error occurred")
                    state = StreamState.NOT_CONNECTING
                }
                stanza.contains("</stream:stream>") -> {
                    Log.w(TAG, "Received stream termination: $stanza")
                    onErrorCallback?.invoke("Connection closed by server")
                    state = StreamState.NOT_CONNECTING
                }
                else -> {
                    Log.w(TAG, "Unhandled stanza: $stanza")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing stanza: ${e.message}, stanza=$stanza", e)
            onErrorCallback?.invoke("Error processing stanza: ${e.message}")
        }
    }

    // Unified parser for message stanzas (handles both MAM and regular)
    private fun parseMessageStanza(stanza: String): XMPPMessage? {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(stanza))
        var eventType = parser.eventType
        var messageId: String? = null
        var from: String? = null
        var to: String? = null
        var type: String? = null
        var lang: String? = null
        var body: String? = null
        var isChatState = false
        var innerMessageId: String? = null
        var innerFrom: String? = null
        var innerTo: String? = null
        var innerBody: String? = null
        var innerType: String? = null
        var innerLang: String? = null
        var inForwarded = false
        var isMAM = false
        val innerRaw = StringBuilder()
        val elements = mutableListOf<XMLElement>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    val namespace = parser.namespace
                    val attributes = mutableMapOf<String, String>()
                    try {
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i)
                            val attrValue = parser.getAttributeValue(i)
                            if (attrName != null && attrValue != null && !attrName.contains("xmlns=")) {
                                attributes[attrName] = attrValue
                            }
                        }
                    } catch (e: XmlPullParserException) {
                        Log.w(TAG, "Skipping malformed attribute in tag $tagName: ${e.message}")
                        continue
                    }
                    if (tagName == "message" && (namespace == "jabber:client" || namespace.isEmpty())) {
                        if (!inForwarded) {
                            messageId = attributes["id"] ?: "unknown_${System.currentTimeMillis()}"
                            from = attributes["from"]?.trim()
                            to = attributes["to"]?.trim()
                            type = attributes["type"]
                            lang = attributes["xml:lang"]
                            Log.d(TAG, "Outer message attributes: id=$messageId, from=$from, to=$to, type=$type, lang=$lang")
                        } else {
                            innerMessageId = attributes["id"] ?: "unknown_${System.currentTimeMillis()}"
                            innerFrom = attributes["from"]?.trim() ?: jid
                            innerTo = attributes["to"]?.trim()
                            innerType = attributes["type"]
                            innerLang = attributes["xml:lang"]
                            Log.d(TAG, "Inner message attributes: id=$innerMessageId, from=$innerFrom, to=$innerTo, type=$innerType, lang=$innerLang")
                            innerRaw.append("<message")
                            for ((key, value) in attributes) {
                                innerRaw.append(" $key='$value'")
                            }
                            innerRaw.append(">")
                        }
                    } else if (tagName == "result" && namespace == "urn:xmpp:mam:2") {
                        isMAM = true
                        elements.add(XMLElement(tagName, namespace, "", attributes))
                    } else if (tagName == "archived" && namespace == "urn:xmpp:mam:tmp") {
                        isMAM = true
                        elements.add(XMLElement(tagName, namespace, "", attributes))
                    } else if (tagName == "forwarded" && namespace == "urn:xmpp:forward:0") {
                        inForwarded = true
                        elements.add(XMLElement(tagName, namespace, "", attributes))
                    } else if (tagName in listOf("active", "composing", "inactive", "received", "displayed") &&
                        (namespace == "http://jabber.org/protocol/chatstates" || namespace == "urn:xmpp:chat-markers:0")) {
                        isChatState = true
                        innerRaw.append("<$tagName xmlns='$namespace'/>")
                        elements.add(XMLElement(tagName, namespace, "", attributes))
                    } else if (tagName == "body" && (inForwarded || !inForwarded)) {
                        parser.next()
                        if (parser.eventType == XmlPullParser.TEXT) {
                            if (inForwarded) {
                                innerBody = parser.text.trim()
                                innerRaw.append("<body>${parser.text}</body>")
                            } else {
                                body = parser.text.trim()
                            }
                            Log.d(TAG, "Body parsed: inForwarded=$inForwarded, body=${if (inForwarded) innerBody else body}")
                            elements.add(XMLElement(tagName, namespace, parser.text, attributes))
                        }
                    } else if (inForwarded && namespace != "jabber:client") {
                        innerRaw.append("<${tagName} xmlns='${namespace}'")
                        for ((key, value) in attributes) {
                            innerRaw.append(" $key='$value'")
                        }
                        innerRaw.append(">")
                        if (tagName == "time" || tagName == "delay") {
                            innerRaw.append("</$tagName>")
                        }
                        elements.add(XMLElement(tagName, namespace, "", attributes))
                    } else {
                        // Add other elements
                        elements.add(XMLElement(tagName, namespace, "", attributes))
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name
                    if (tagName == "forwarded" && parser.namespace == "urn:xmpp:forward:0") {
                        inForwarded = false
                    } else if (inForwarded && tagName == "message" && (parser.namespace == "jabber:client" || parser.namespace.isEmpty())) {
                        innerRaw.append("</message>")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inForwarded) {
                        innerRaw.append(parser.text)
                    }
                }
            }
            eventType = parser.next()
        }

        val finalId = innerMessageId ?: messageId ?: "unknown_${System.currentTimeMillis()}"
        val finalFrom = (innerFrom ?: from)?.let { XMPPJID(fullJID = it) }
        val finalTo = (innerTo ?: to)?.let { XMPPJID(fullJID = it) }
        val finalBody = innerBody ?: body
        val finalType = innerType ?: type
        val finalLang = innerLang ?: lang
        val messageRaw = if (isMAM || inForwarded) innerRaw.toString() else stanza
        val filteredElements = elements.filter { it.namespace != "urn:xmpp:mam:tmp" && it.namespace != "urn:xmpp:forward:0" }

        if (isChatState && finalBody.isNullOrEmpty()) {
            Log.d(TAG, "Skipping chat state message: id=$finalId")
            return null
        }

        return XMPPMessage(
            raw = messageRaw,
            type = finalType,
            id = finalId,
            from = finalFrom,
            to = finalTo,
            lang = finalLang,
            body = finalBody,
            children = filteredElements
        )
    }

    private suspend fun processMessageQueue() {
        val processedIds = mutableSetOf<String>()
        val realm = Realm.open(defaultRealmConfig())
        realm.write {
            val storedIds = query<ProcessedMessageId>("owner = $0", jid).find().map { it.messageId }
            processedIds.addAll(storedIds)
            Log.d(TAG, "Loaded ${storedIds.size} processed message IDs for owner=$jid")
        }
        realm.close()

        while (true) {
            val item = messageQueue.receiveCatching().getOrNull() ?: break
            queueMutex.withLock {
                if (item.message.id == null || item.message.from == null || item.message.to == null) {
                    Log.w(TAG, "Skipping invalid queue item: id=${item.message.id}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}")
                    return@withLock
                }
                val messageId = item.message.id!!
                if (messageId in processedIds) {
                    Log.d(TAG, "Already processed messageId=$messageId, checking if in MessageStorageItem")
                    val realm = Realm.open(defaultRealmConfig())
                    val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                    val existingMessage = realm.query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
                    if (existingMessage == null) {
                        Log.w(TAG, "MessageId=$messageId marked as processed but not in MessageStorageItem, reprocessing")
                    } else {
                        Log.d(TAG, "Confirmed messageId=$messageId in MessageStorageItem, skipping")
                        realm.close()
                        return@withLock
                    }
                    realm.close()
                }
                val from = item.message.from?.bare() ?: return@withLock
                val to = item.message.to?.bare() ?: jid
                val opponent = if (to != jid) to else from
                if (item.message.body.isNullOrEmpty()) {
                    Log.d(TAG, "Skipping message with no body: id=$messageId")
                    return@withLock
                }
                Log.d(TAG, "Processing queued message: id=$messageId, from=$from, to=$to, body=${item.message.body.take(50)}")
                try {
                    val realm = Realm.open(defaultRealmConfig())
                    realm.write {
                        val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                        val existingMessage = query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
                        if (existingMessage != null) {
                            Log.d(TAG, "Skipping duplicate message in MessageStorageItem: id=$messageId, primary=$msgPrimary, body=${existingMessage.body.take(50)}")
                            return@write
                        }

                        val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", opponent, jid).first().find()
                            ?: copyToRealm(RosterStorageItem().apply {
                                primary = RosterStorageItem.genPrimary(opponent, jid)
                                this.jid = opponent
                                this.owner = jid
                                this.customNickname = opponent
                            }, UpdatePolicy.ALL)

                        val isGroupChat = item.message.hasElement("x", "https://xabber.com/protocol/groups")
                        var isOutgoing = from == jid
                        if (isGroupChat) {
                            val userId = item.message.element("x", "https://xabber.com/protocol/groups")
                                ?.element("reference", "https://xabber.com/protocol/groups")
                                ?.element("user", "https://xabber.com/protocol/groups")?.getAttribute("id")
                            isOutgoing = userId == jid
                            Log.d(TAG, "Group chat message: userId=$userId, jid=$jid, isOutgoing=$isOutgoing")
                        }

                        val message = copyToRealm(MessageStorageItem().apply {
                            primary = msgPrimary
                            this.messageId = messageId
                            this.owner = jid
                            this.opponent = opponent
                            this.body = item.message.body ?: ""
                            this.date = item.timestamp
                            this.sentDate = item.timestamp
                            this.editDate = 0L
                            this.outgoing = isOutgoing
                            this.conversationType_ = when {
                                item.isClientSync && to == "favorites.redsolution.com" -> "urn:xabber:favorites:0"
                                isGroupChat -> "https://xabber.com/protocol/groups"
                                else -> "urn:xabber:chat"
                            }
                            this.isRead = isOutgoing || item.isArchived
                            this.state = if (isOutgoing) MessageSendingState.Deliver else MessageSendingState.Sent
                            this.queryIds = item.queryId
                            this.archivedId = item.message.element("archived", "urn:xmpp:mam:tmp")?.getAttribute("id") ?: ""
                        }, UpdatePolicy.ALL)

                        val conversationType = ConversationType.fromRaw(message.conversationType_)
                        val chatPrimary = LastChatsStorageItem.genPrimary(opponent, jid, conversationType)
                        val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find()
                        if (chat == null) {
                            copyToRealm(LastChatsStorageItem().apply {
                                primary = chatPrimary
                                this.jid = opponent
                                this.owner = jid
                                this.conversationType_ = conversationType.rawValue
                                this.isArchived = false
                                this.unread = if (isOutgoing || item.isArchived) 0 else 1
                                this.messageDate = item.timestamp
                                this.lastMessageId = messageId
                                this.pinnedPosition = 0
                                this.muteExpired = -1
                                this.rosterItem = rosterItem
                                this.lastMessage = message
                            }, UpdatePolicy.ALL)
                            Log.d(TAG, "Created new LastChatsStorageItem for jid=$opponent, type=${conversationType.rawValue}, messageId=$messageId, timestamp=${item.timestamp}, isOutgoing=$isOutgoing")
                        } else {
                            findLatest(chat)?.apply {
                                if (item.timestamp > this.messageDate) {
                                    this.unread = if (isOutgoing || item.isArchived) this.unread else this.unread + 1
                                    this.messageDate = item.timestamp
                                    this.lastMessageId = messageId
                                    this.lastMessage = message
                                    this.isArchived = false
                                    Log.d(TAG, "Updated LastChatsStorageItem to latest: jid=$opponent, type=${conversationType.rawValue}, messageId=$messageId, timestamp=${item.timestamp}, isOutgoing=$isOutgoing")
                                } else {
                                    Log.d(TAG, "Skipped LastChatsStorageItem update, older timestamp: jid=$opponent, messageId=$messageId, timestamp=${item.timestamp}, current=${this.messageDate}")
                                }
                            }
                        }

                        val messageDto = MessageDto(
                            primary = message.primary,
                            isOutgoing = message.outgoing,
                            owner = message.owner,
                            opponentJid = message.opponent,
                            messageBody = message.body,
                            messageSendingState = when {
                                message.isRead -> MessageSendingState.Read
                                message.outgoing -> MessageSendingState.Deliver
                                else -> MessageSendingState.Sent
                            },
                            sentTimestamp = message.sentDate,
                            editTimestamp = message.editDate,
                            displayType = if (message.displayAs == "system") MessageDisplayType.System else MessageDisplayType.Text,
                            canEditMessage = message.outgoing,
                            canDeleteMessage = message.outgoing,
                            urlAvatar = null,
                            isGroup = message.conversationType_ == "https://xabber.com/protocol/groups",
                            kind = null,
                            isSelected = false,
                            references = message.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                            isUnread = !message.isRead,
                            isChecked = false,
                            archivedId = message.archivedId
                        )

                        val chatViewModel = AccountManager.getChatViewModel(chatPrimary)
                        if (chatViewModel != null) {
                            chatViewModel.insertMessagesFromReceiver(listOf(messageDto))
                            Log.d(TAG, "Notified ChatViewModel for chatId=$chatPrimary with message $messageId, body=${message.body.take(50)}, timestamp=${item.timestamp}, isOutgoing=$isOutgoing")
                        } else {
                            Log.w(TAG, "ChatViewModel not found for chatId=$chatPrimary, messageId=$messageId")
                        }

                        processedIds.add(messageId)
                        copyToRealm(ProcessedMessageId().apply {
                            this.messageId = messageId
                            this.owner = jid
                            this.timestamp = System.currentTimeMillis()
                        }, UpdatePolicy.ALL)
                    }
                    realm.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing queued message: ${e.message}, id=$messageId", e)
                }
            }
        }
    }

    suspend fun debugDatabaseState() {
        val realm = Realm.open(defaultRealmConfig())
        realm.write {
            val messages = query<MessageStorageItem>("owner = $0 AND opponent = $1", jid, "igor.boldin@redsolution.com").find()
            messages.forEach { msg ->
                Log.d(TAG, "MessageStorageItem: primary=${msg.primary}, messageId=${msg.messageId}, body=${msg.body.take(50)}, sentDate=${msg.sentDate}, isDeleted=${msg.isDeleted}, isRead=${msg.isRead}")
            }
            val tempStanzas = query<TemporaryMessageStanzaStorageItem>("owner = $0 AND jid = $1", jid, "igor.boldin@redsolution.com").find()
            tempStanzas.forEach { stanza ->
                Log.d(TAG, "TemporaryMessageStanzaStorageItem: primary=${stanza.primary}, messageId=${stanza.messageId}, isProcessed=${stanza.isProcessed}, body=${stanza.stanza.substringAfter("<body>").substringBefore("</body>").take(50)}")
            }
            val processedIds = query<ProcessedMessageId>("owner = $0", jid).find()
            processedIds.forEach { id ->
                Log.d(TAG, "ProcessedMessageId: messageId=${id.messageId}, owner=${id.owner}, timestamp=${id.timestamp}")
            }
            val chat = query<LastChatsStorageItem>("primary = $0", "igor.boldin@redsolution.com_aleksey.boldin@redsolution.com_urn:xabber:chat").first().find()
            if (chat != null) {
                Log.d(TAG, "LastChatsStorageItem: primary=${chat.primary}, lastMessageId=${chat.lastMessageId}, lastMessageBody=${chat.lastMessage?.body?.take(50)}, lastMessageDate=${chat.lastMessage?.sentDate}")
            }
        }
        realm.close()
    }

    suspend fun clearStaleTemporaryMessages() {
        val realm = Realm.open(defaultRealmConfig())
        realm.write {
            val stale = query<TemporaryMessageStanzaStorageItem>("owner = $0", jid).find()
            delete(stale)
            Log.d(TAG, "Cleared ${stale.size} stale TemporaryMessageStanzaStorageItem entries for owner=$jid")
        }
        realm.close()
    }

    suspend fun retryUnprocessedMessages() {
        val realm = Realm.open(defaultRealmConfig())
        val unprocessed = realm.query<TemporaryMessageStanzaStorageItem>("owner = $0 AND isProcessed = false", jid).find()
        unprocessed.forEach { stanza ->
            Log.d(TAG, "Retrying unprocessed message: id=${stanza.messageId}, primary=${stanza.primary}")
            withContext(Dispatchers.IO) {
                // Re-parse the stanza for retry
                val xmppMessage = parseMessageStanza(stanza.stanza)
                if (xmppMessage != null) {
                    delegate?.didReceiveMessage(xmppMessage, this@Stream)
                }
                realm.write {
                    val latest = findLatest(stanza)
                    if (latest != null) {
                        latest.isProcessed = true
                        Log.d(TAG, "Marked retried message as processed: id=${stanza.messageId}")
                    }
                }
            }
        }
        realm.close()
    }

    suspend fun logUnprocessedMessages(owner: String) {
        val realm = Realm.open(defaultRealmConfig())
        realm.write {
            query<TemporaryMessageStanzaStorageItem>("owner = $0 AND isProcessed = false", owner).find().forEach { item ->
                Log.d(
                    TAG,
                    "Unprocessed TemporaryMessageStanzaStorageItem: primary=${item.primary}, messageId=${item.messageId}, owner=${item.owner}, jid=${item.jid}, stanza=${item.stanza}"
                )
            }
        }
        realm.close()
    }

    fun deleteSelfChats() {
        val realm = Realm.open(defaultRealmConfig())
        realm.writeBlocking {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", jid).find()
            delete(selfChats)
            Log.d(TAG, "Deleted ${selfChats.size} self-chats for owner=$jid")
        }
    }

    private fun parseIQ(stanza: String): XMPPIQ? {
        try {
            val typeMatch = Regex("""type=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1) ?: return null
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
            val content = if (headerEnd != -1 && iqEnd > headerEnd + 1) stanza.substring(headerEnd + 1, iqEnd).trim() else ""
            val queryNamespace = if (content.isNotEmpty()) {
                val childStart = content.indexOf("<")
                if (childStart != -1) {
                    val childHeaderEnd = content.indexOf(">", childStart)
                    Regex("""xmlns=['"]([^'"]+)['"]""").find(content.substring(childStart, childHeaderEnd + 1))?.groupValues?.get(1)
                } else null
            } else null
            return XMPPIQ(stanza, typeMatch, idMatch, fromMatch, toMatch, error, queryNamespace, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IQ: ${e.message}, stanza=$stanza", e)
            return null
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
            socket = null
            state = StreamState.NOT_CONNECTING
            messageCallbackChannel.close()
            messageQueue.close()
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

    open fun onNotConnecting() {}
    open suspend fun onStreamOpen() {}
    open suspend fun onProceed() {
        Log.d(TAG, "Awaiting stream features after TLS upgrade for JID: $jid")
    }
    open suspend fun onProcessAuth() {
        Log.d(TAG, "Awaiting authentication response for JID: $jid")
    }
    open suspend fun onConnected() {}
}