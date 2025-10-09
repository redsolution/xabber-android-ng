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
    import kotlinx.coroutines.SupervisorJob
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
        private val stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())

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
                while (content.isNotEmpty() && processedStanzas < 50) {
                    val start = content.indexOf("<")
                    if (start == -1) {
                        Log.w(TAG, "No XML start tag found in buffer: ${content.take(200)}")
                        break
                    }

                    // Handle stream headers first
                    if (content.startsWith("<?xml", start) || content.indexOf("<stream:stream", start) == start ||
                        content.indexOf("<stream:error", start) == start || content.indexOf("<stream:features", start) == start) {
                        var end = content.indexOf(">", start)
                        if (end == -1) {
                            Log.w(TAG, "Incomplete stream header, buffering: ${content.take(200)}")
                            break
                        }
                        if (content.indexOf("<stream:features>", start) != -1) {
                            end = content.indexOf("</stream:features>", end) + "</stream:features>".length
                        } else if (content.indexOf("</stream:stream>", start) != -1) {
                            end = content.indexOf("</stream:stream>", end) + "</stream:stream>".length
                        } else if (content.indexOf("</stream:error>", start) != -1) {
                            end = content.indexOf("</stream:error>", end) + "</stream:error>".length
                        }
                        val header = content.substring(start, end)
                        // Process stream header synchronously as it's critical
                        processStanza(header)
                        content = content.substring(end).trimStart()
                        processedStanzas++
                        continue
                    }

                    // Prioritize IQ stanzas (especially sync)
                    val iqStart = content.indexOf("<iq", start)
                    val presenceStart = content.indexOf("<presence", start)
                    val messageStart = content.indexOf("<message", start)

                    // Choose the earliest stanza type, prioritizing IQ
                    val nextStart = listOfNotNull(
                        iqStart.takeIf { it != -1 }?.let { it to "iq" },
                        presenceStart.takeIf { it != -1 }?.let { it to "presence" },
                        messageStart.takeIf { it != -1 }?.let { it to "message" }
                    ).minByOrNull { it.first }?.let { it.first to it.second } ?: (start to null)

                    val tagName = when (nextStart.second) {
                        "iq" -> "iq"
                        "presence" -> "presence"
                        "message" -> "message"
                        else -> content.substring(start + 1, content.indexOf(">", start).takeIf { it != -1 } ?: content.length).split(Regex("\\s+"))[0]
                    }

                    val tagEnd = content.indexOf(">", nextStart.first)
                    if (tagEnd == -1) {
                        Log.w(TAG, "Incomplete stanza tag, buffering: ${content.take(200)}")
                        break
                    }
                    val fullTag = content.substring(nextStart.first + 1, tagEnd)
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
                                Log.w(TAG, "No closing tag for $tagName, buffering: ${content.take(200)}")
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
                    val stanza = content.substring(nextStart.first, fullEnd)

                    // Launch concurrent processing for the stanza without waiting
                    stanzaProcessingScope.launch {
                        try {
                            if (tagName == "message" && (stanza.contains("urn:xmpp:mam:2") || stanza.contains("urn:xmpp:mam:tmp") || stanza.contains("urn:xmpp:last-message"))) {
                                processMAMStanza(stanza)
                            } else if (tagName == "iq" && stanza.contains("https://xabber.com/protocol/synchronization")) {
                                processStanza(stanza)
                                Log.d(TAG, "Processed sync IQ stanza: ${stanza.take(200)}")
                            } else {
                                processStanza(stanza)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing stanza concurrently: ${e.message}, stanza=${stanza.take(200)}", e)
                            onErrorCallback?.invoke("Error processing stanza: ${e.message}")
                        }
                    }

                    content = content.substring(fullEnd).trimStart()
                    processedStanzas++
                }
                streamBuffer.clear()
                streamBuffer.append(content)
            }
        }

        private suspend fun processMAMStanza(stanza: String) {
            try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(StringReader(stanza))
                var eventType = parser.eventType
                var messageId: String? = null
                var queryId: String? = null

                // Early check for message ID to avoid redundant processing
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "result" && parser.namespace == "urn:xmpp:mam:2") {
                        queryId = parser.getAttributeValue(null, "queryid")
                        messageId = parser.getAttributeValue(null, "id")
                        break
                    }
                    eventType = parser.next()
                }

                if (messageId != null) {
                    val primary = ProcessedMessageId.genPrimary(messageId, jid)
                    val realm = Realm.open(defaultRealmConfig())
                    val existing = realm.query<ProcessedMessageId>("messageId = $0 AND owner = $1", messageId, jid).first().find()
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate MAM stanza: id=$messageId, primary=$primary, thread=${Thread.currentThread().id}")
                        realm.close()
                        return
                    }
                    val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                    val existingMsg = realm.query<MessageStorageItem>("primary = $0 OR (archivedId = $1 AND archivedId != '')", msgPrimary, messageId).first().find()
                    if (existingMsg != null) {
                        Log.d(TAG, "Skipping duplicate MAM message in MessageStorageItem: id=$messageId, primary=$msgPrimary, body=${existingMsg.body.take(50)}")
                        realm.write {
                            copyToRealm(ProcessedMessageId().apply {
                                this.messageId = messageId as String
                                this.owner = jid
                                this.timestamp = System.currentTimeMillis()
                            }, UpdatePolicy.ALL)
                        }
                        realm.close()
                        return
                    }
                    realm.close()
                }

                // Reset parser for full processing
                parser.setInput(StringReader(stanza))
                eventType = parser.eventType
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
                                    } else {
                                        Log.w(TAG, "Skipping malformed attribute in tag $tagName: name=$attrName, value=$attrValue")
                                    }
                                }
                            } catch (e: XmlPullParserException) {
                                Log.w(TAG, "Skipping malformed attribute in tag $tagName: ${e.message}")
                                continue
                            }
                            if (tagName == "result" && namespace == "urn:xmpp:mam:2") {
                                isMAM = true
                                queryId = attributes["queryid"]
                                elements.add(XMLElement(tagName, namespace, "", attributes))
                            } else if (tagName == "forwarded" && namespace == "urn:xmpp:forward:0") {
                                inForwarded = true
                                elements.add(XMLElement(tagName, namespace, "", attributes))
                            } else if (tagName == "message" && (namespace == "jabber:client" || namespace.isEmpty())) {
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

                messageId = innerMessageId ?: messageId ?: "unknown_${System.currentTimeMillis()}"
                Log.d(TAG, "Received message stanza: id=$messageId, isChatState=$isChatState, from=$from, to=$to, innerFrom=$innerFrom, innerTo=$innerTo, body=$body, innerBody=$innerBody, isMAM=$isMAM")

                if (isChatState && (innerBody.isNullOrEmpty() && body.isNullOrEmpty())) {
                    Log.d(TAG, "Skipping storage for chat state or marker message: id=$messageId")
                    if (delegate != null) {
                        withContext(Dispatchers.IO) {
                            Log.d(TAG, "Dispatching chat state to delegate: id=$messageId")
                            delegate?.didReceiveMessage(stanza, this@Stream)
                        }
                    }
                    return
                }

                val fromJid = innerFrom ?: from
                val toJid = innerTo ?: to
                if (fromJid == null || toJid == null) {
                    Log.w(TAG, "Skipping message with missing from/to: id=$messageId, from=$fromJid, to=$toJid, stanza=$stanza")
                    return
                }

                // Correct opponent calculation: use bare JIDs and check isOutgoing
                val isOutgoing = fromJid.split("/")[0] == jid
                var opponent = if (isOutgoing) toJid.split("/")[0] else fromJid.split("/")[0]
                val realm = Realm.open(defaultRealmConfig())
                var primary = ProcessedMessageId.genPrimary(messageId, jid)

                if (isMAM) {
                    val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                    val existingMsg = realm.query<MessageStorageItem>("primary = $0 OR (archivedId = $1 AND archivedId != '')", msgPrimary, messageId).first().find()
                    if (existingMsg != null) {
                        Log.d(TAG, "Skipping duplicate MAM message in MessageStorageItem: id=$messageId, primary=$msgPrimary, body=${existingMsg.body.take(50)}")
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
                } else {
                    val existing = realm.query<ProcessedMessageId>("messageId = $0 AND owner = $1", messageId, jid).first().find()
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate non-MAM message: id=$messageId, primary=$primary, body=$body")
                        realm.close()
                        return
                    }
                }

                val messageRaw = if (isMAM) innerRaw.toString() else stanza
                val xmppMessage = XMPPMessage(
                    raw = messageRaw,
                    type = innerType ?: type,
                    id = messageId,
                    from = (innerFrom ?: from)?.let { XMPPJID(fullJID = it) },
                    to = (innerTo ?: to)?.let { XMPPJID(fullJID = it) },
                    lang = innerLang ?: lang,
                    body = innerBody ?: body,
                    children = elements.filter { it.namespace != "urn:xmpp:mam:tmp" && it.namespace != "urn:xmpp:forward:0" }
                )

                val timestamp = parseTimestamp(xmppMessage, TAG) ?: run {
                    if (!isChatState) {
                        Log.w(TAG, "Using fallback timestamp for non-chat-state messageId=$messageId")
                        System.currentTimeMillis()
                    } else {
                        Log.d(TAG, "No timestamp required for chat state messageId=$messageId")
                        null
                    }
                }

                if (timestamp == null && isChatState) {
                    Log.d(TAG, "Skipping storage for chat state message with no timestamp: id=$messageId")
                    realm.close()
                    return
                }

                // Determine conversation type
                val conversationType = MessageCommonReceiver(this.jid).conversationTypeByMessage(xmppMessage)
                val chatPrimary = LastChatsStorageItem.genPrimary(opponent, jid, conversationType)

                // Fix primary key for TemporaryMessageStanzaStorageItem
                val tempStanzaPrimary = "${messageId}_$jid"
                realm.write {
                    val tempStanza = TemporaryMessageStanzaStorageItem().apply {
                        this.primary = tempStanzaPrimary
                        owner = jid
                        jid = opponent
                        this.messageId = messageId
                        date = timestamp ?: System.currentTimeMillis()
                        this.stanza = stanza
                        isProcessed = false
                    }
                    copyToRealm(tempStanza, UpdatePolicy.ALL)
                    Log.d(TAG, "Stored TemporaryMessageStanzaStorageItem: messageId=$messageId, primary=$tempStanzaPrimary, opponent=$opponent, date=$timestamp, body=${xmppMessage.body?.take(50)}")
                }

                val storedStanza = realm.query<TemporaryMessageStanzaStorageItem>("primary = $0", tempStanzaPrimary).first().find()
                if (storedStanza != null) {
                    Log.d(TAG, "Verified storage: messageId=$messageId, primary=$tempStanzaPrimary, owner=${storedStanza.owner}, isProcessed=${storedStanza.isProcessed}, body=${xmppMessage.body?.take(50)}")
                } else {
                    Log.e(TAG, "Failed to verify storage for messageId=$messageId, primary=$tempStanzaPrimary")
                }
                realm.close()

                // Create MessageDto for ChatViewModel
                val messageDto = MessageDto(
                    primary = MessageStorageItem.genPrimary(messageId, jid),
                    isOutgoing = isOutgoing,
                    owner = jid,
                    opponentJid = opponent,
                    messageBody = xmppMessage.body ?: "",
                    messageSendingState = if (isOutgoing) MessageSendingState.Deliver else MessageSendingState.Sent,
                    sentTimestamp = timestamp ?: System.currentTimeMillis(),
                    editTimestamp = 0,
                    displayType = MessageDisplayType.Text,
                    canEditMessage = isOutgoing,
                    canDeleteMessage = isOutgoing,
                    urlAvatar = null,
                    isGroup = conversationType == ConversationType.Group,
                    kind = null,
                    isSelected = false,
                    references = ArrayList<MessageReferenceDto>(), // Use ArrayList instead of emptyList
                    isUnread = !isOutgoing,
                    isChecked = false,
                    archivedId = messageId
                )

                // Notify MessageArchiveManager's temporaryMessageReceiver
                val instance = MessageStorageItem().apply {
                    primary = messageDto.primary
                    owner = jid
                    opponent = opponent
                    body = messageDto.messageBody
                    date = messageDto.sentTimestamp
                    sentDate = messageDto.sentTimestamp
                    editDate = messageDto.editTimestamp
                    outgoing = isOutgoing
                    isRead = !messageDto.isUnread
                    conversationType_ = conversationType.rawValue
                    archivedId = messageId
                    queryIds = queryId
                }
                temporaryMessageReceiver?.didReceiveMessage(instance, queryId ?: "")

                // Notify ChatViewModel
                val chatViewModel = AccountManager.getChatViewModel(chatPrimary)
                if (chatViewModel != null) {
                    chatViewModel.insertMessagesFromReceiver(listOf(messageDto))
                    Log.d(TAG, "Notified ChatViewModel for chatId=$chatPrimary with message $messageId, body=${xmppMessage.body?.take(50)}")
                } else {
                    Log.w(TAG, "ChatViewModel not found for chatId=$chatPrimary, messageId=$messageId")
                    // Create ChatViewModel if it doesn't exist
                    AccountManager.createChatViewModel(jid, opponent, conversationType)
                    val newChatViewModel = AccountManager.getChatViewModel(chatPrimary)
                    newChatViewModel?.insertMessagesFromReceiver(listOf(messageDto))
                    Log.d(TAG, "Created and notified new ChatViewModel for chatId=$chatPrimary with message $messageId")
                }

                if (delegate != null) {
                    withContext(Dispatchers.IO) {
                        Log.d(TAG, "Dispatching message to delegate: id=$messageId")
                        delegate?.didReceiveMessage(stanza, this@Stream)
                    }
                } else {
                    Log.e(TAG, "Delegate is null, cannot dispatch message: id=$messageId, stanza=$stanza")
                }

                val isCarbon = xmppMessage.element("sent", namespace = "urn:xmpp:carbons:2") != null ||
                        xmppMessage.element("received", namespace = "urn:xmpp:carbons:2") != null
                val isArchived = isMAM
                val isClientSync = xmppMessage.element("synchronization", namespace = "https://xabber.com/protocol/synchronization") != null
                val queueItem = MessageQueueItem(
                    stanza = stanza,
                    message = xmppMessage,
                    isCarbon = isCarbon,
                    isArchived = isArchived,
                    isClientSync = isClientSync,
                    timestamp = timestamp ?: System.currentTimeMillis(),
                    queryId = queryId
                )
                if (queueItem.message.id != null && queueItem.message.from != null && queueItem.message.to != null) {
                    messageQueue.send(queueItem)
                    Log.d(TAG, "Enqueued message: id=$messageId, isCarbon=$isCarbon, isArchived=$isArchived, isClientSync=$isClientSync, body=${xmppMessage.body?.take(50)}, thread=${Thread.currentThread().id}")
                } else {
                    Log.w(TAG, "Skipping enqueue for invalid message: id=$messageId, from=${xmppMessage.from?.bare()}, to=${xmppMessage.to?.bare()}, stanza=$stanza")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing MAM stanza: ${e.message}, stanza=$stanza", e)
                onErrorCallback?.invoke("Error processing MAM stanza: ${e.message}")
            }
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
                        Log.w(TAG, "Skipping invalid queue item: id=${item.message.id}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}, stanza=${item.stanza}")
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
                        Log.d(TAG, "Skipping message with no body: id=$messageId, stanza=${item.stanza}")
                        return@withLock
                    }
                    Log.d(TAG, "Processing queued message: id=$messageId, from=$from, to=$to, body=${item.message.body.take(50)}, thread=${Thread.currentThread().id}")
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

                            val isGroupChat = item.message.element("x", namespace = "https://xabber.com/protocol/groups") != null
                            var isOutgoing = from == jid
                            if (isGroupChat) {
                                val userId = item.message.element("x", namespace = "https://xabber.com/protocol/groups")
                                    ?.element("reference", namespace = "https://xabber.com/protocol/groups")
                                    ?.element("user", namespace = "https://xabber.com/protocol/groups")?.getAttribute("id")
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
                                this.archivedId = item.message.element("archived", namespace = "urn:xmpp:mam:tmp")?.getAttribute("id") ?: ""
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
                                Log.d(TAG, "Notified ChatViewModel for chatId=$chatPrimary with message $messageId, body=${message.body.take(50)}, timestamp=${item.timestamp}, isOutgoing=$isOutgoing, thread=${Thread.currentThread().id}")
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
                        Log.e(TAG, "Error processing queued message: ${e.message}, id=$messageId, stanza=${item.stanza}", e)
                    }
                }
            }
        }

        private suspend fun processStanza(stanza: String) {
            Log.d(TAG, "Received stanza: ${stanza.take(200)}")
            try {
                if (stanza.startsWith("<message")) {
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
                                for (i in 0 until parser.attributeCount) {
                                    attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                                }
                                if (tagName == "message" && (namespace == "jabber:client" || namespace.isEmpty())) {
                                    if (!inForwarded) {
                                        messageId = parser.getAttributeValue(null, "id") ?: "unknown_${System.currentTimeMillis()}"
                                        from = parser.getAttributeValue(null, "from")?.trim()
                                        to = parser.getAttributeValue(null, "to")?.trim()
                                        type = parser.getAttributeValue(null, "type")
                                        lang = parser.getAttributeValue(null, "xml:lang")
                                        Log.d(TAG, "Outer message attributes: id=$messageId, from=$from, to=$to, type=$type, lang=$lang")
                                    } else {
                                        innerMessageId = parser.getAttributeValue(null, "id") ?: "unknown_${System.currentTimeMillis()}"
                                        innerFrom = parser.getAttributeValue(null, "from")?.trim()
                                        innerTo = parser.getAttributeValue(null, "to")?.trim()
                                        innerType = parser.getAttributeValue(null, "type")
                                        innerLang = parser.getAttributeValue(null, "xml:lang")
                                        Log.d(TAG, "Inner message attributes: id=$innerMessageId, from=$innerFrom, to=$innerTo, type=$innerType, lang=$innerLang")
                                        innerRaw.append("<message")
                                        for (i in 0 until parser.attributeCount) {
                                            innerRaw.append(" ${parser.getAttributeName(i)}='${parser.getAttributeValue(i)}'")
                                        }
                                        innerRaw.append(">")
                                    }
                                } else if (tagName == "archived" && namespace == "urn:xmpp:mam:tmp") {
                                    isMAM = true
                                    elements.add(XMLElement(tagName, namespace, "", attributes))
                                } else if (tagName == "forwarded" && namespace == "urn:xmpp:forward:0") {
                                    inForwarded = true
                                    elements.add(XMLElement(tagName, namespace, "", attributes))
                                } else if (tagName in listOf("active", "composing", "inactive", "received", "displayed") && (namespace == "http://jabber.org/protocol/chatstates" || namespace == "urn:xmpp:chat-markers:0")) {
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
                                    for (i in 0 until parser.attributeCount) {
                                        innerRaw.append(" ${parser.getAttributeName(i)}='${parser.getAttributeValue(i)}'")
                                    }
                                    innerRaw.append(">")
                                    if (tagName == "time" || tagName == "delay") {
                                        innerRaw.append("</$tagName>")
                                    }
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

                    messageId = innerMessageId ?: messageId ?: "unknown_${System.currentTimeMillis()}"
                    Log.d(TAG, "Received message stanza: id=$messageId, isChatState=$isChatState, from=$from, to=$to, innerFrom=$innerFrom, innerTo=$innerTo, body=$body, innerBody=$innerBody, isMAM=$isMAM")

                    if (isChatState && (innerBody.isNullOrEmpty() && body.isNullOrEmpty())) {
                        Log.d(TAG, "Skipping storage for chat state or marker message: id=$messageId")
                        if (delegate != null) {
                            withContext(Dispatchers.IO) {
                                Log.d(TAG, "Dispatching chat state to delegate: id=$messageId")
                                delegate?.didReceiveMessage(stanza, this@Stream)
                            }
                        }
                        return
                    }

                    val fromJid = innerFrom ?: from
                    val toJid = innerTo ?: to
                    if (fromJid == null || toJid == null) {
                        Log.w(TAG, "Skipping message with missing from/to: id=$messageId, from=$fromJid, to=$toJid, stanza=$stanza")
                        return
                    }

                    val opponent = if (toJid != jid) toJid else fromJid
                    val realm = Realm.open(defaultRealmConfig())
                    val primary = ProcessedMessageId.genPrimary(messageId, jid)

                    if (isMAM) {
                        val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                        val existingMsg = realm.query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
                        if (existingMsg != null) {
                            Log.d(TAG, "Skipping duplicate MAM message in MessageStorageItem: id=$messageId, primary=$msgPrimary, body=${existingMsg.body.take(50)}")
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
                    } else {
                        val existing = realm.query<ProcessedMessageId>("messageId = $0 AND owner = $1", messageId, jid).first().find()
                        if (existing != null) {
                            Log.d(TAG, "Skipping duplicate non-MAM message: id=$messageId, primary=$primary, body=$body")
                            realm.close()
                            return
                        }
                    }

                    val messageRaw = if (isMAM) innerRaw.toString() else stanza
                    val xmppMessage = XMPPMessage(
                        raw = messageRaw,
                        type = innerType ?: type,
                        id = messageId,
                        from = (innerFrom ?: from)?.let { XMPPJID(fullJID = it) },
                        to = (innerTo ?: to)?.let { XMPPJID(fullJID = it) },
                        lang = innerLang ?: lang,
                        body = innerBody ?: body,
                        children = elements.filter { it.namespace != "urn:xmpp:mam:tmp" && it.namespace != "urn:xmpp:forward:0" }
                    )

                    val timestamp = parseTimestamp(xmppMessage, TAG) ?: run {
                        if (!isChatState) {
                            Log.w(TAG, "Using fallback timestamp for non-chat-state messageId=$messageId")
                            System.currentTimeMillis()
                        } else {
                            Log.d(TAG, "No timestamp required for chat state messageId=$messageId")
                            null
                        }
                    }

                    if (timestamp == null && isChatState) {
                        Log.d(TAG, "Skipping storage for chat state message with no timestamp: id=$messageId")
                        realm.close()
                        return
                    }

                    realm.write {
                        val tempStanza = TemporaryMessageStanzaStorageItem().apply {
                            this.primary = TemporaryMessageStanzaStorageItem.genPrimary(messageId, jid)
                            owner = jid
                            jid = opponent
                            this.messageId = messageId
                            date = timestamp ?: System.currentTimeMillis()
                            this.stanza = stanza
                            isProcessed = false
                        }
                        copyToRealm(tempStanza, UpdatePolicy.ALL)
                        Log.d(TAG, "Stored TemporaryMessageStanzaStorageItem: messageId=$messageId, primary=${tempStanza.primary}, opponent=$opponent, date=$timestamp, body=${xmppMessage.body?.take(50)}")
                    }

                    val storedStanza = realm.query<TemporaryMessageStanzaStorageItem>("primary = $0", TemporaryMessageStanzaStorageItem.genPrimary(messageId, jid)).first().find()
                    if (storedStanza != null) {
                        Log.d(TAG, "Verified storage: messageId=$messageId, primary=${storedStanza.primary}, owner=${storedStanza.owner}, isProcessed=${storedStanza.isProcessed}, body=${xmppMessage.body?.take(50)}")
                    } else {
                        Log.e(TAG, "Failed to verify storage for messageId=$messageId, primary=${TemporaryMessageStanzaStorageItem.genPrimary(messageId, jid)}")
                    }
                    realm.close()

                    if (delegate != null) {
                        withContext(Dispatchers.IO) {
                            Log.d(TAG, "Dispatching message to delegate: id=$messageId")
                            delegate?.didReceiveMessage(stanza, this@Stream)
                        }
                    } else {
                        Log.e(TAG, "Delegate is null, cannot dispatch message: id=$messageId, stanza=$stanza")
                    }

                    val isCarbon = xmppMessage.element("sent", namespace = "urn:xmpp:carbons:2") != null ||
                            xmppMessage.element("received", namespace = "urn:xmpp:carbons:2") != null
                    val isArchived = isMAM
                    val isClientSync = xmppMessage.element("synchronization", namespace = "https://xabber.com/protocol/synchronization") != null
                    val queryId = xmppMessage.element("result", namespace = "urn:xmpp:mam:2")?.getAttribute("queryid")
                    val queueItem = MessageQueueItem(
                        stanza = stanza,
                        message = xmppMessage,
                        isCarbon = isCarbon,
                        isArchived = isArchived,
                        isClientSync = isClientSync,
                        timestamp = timestamp ?: System.currentTimeMillis(),
                        queryId = queryId
                    )
                    if (queueItem.message.id != null && queueItem.message.from != null && queueItem.message.to != null) {
                        messageQueue.send(queueItem)
                        Log.d(TAG, "Enqueued message: id=$messageId, isCarbon=$isCarbon, isArchived=$isArchived, isClientSync=$isClientSync, body=${xmppMessage.body?.take(50)}, thread=${Thread.currentThread().id}")
                    } else {
                        Log.w(TAG, "Skipping enqueue for invalid message: id=$messageId, from=${xmppMessage.from?.bare()}, to=${xmppMessage.to?.bare()}, stanza=$stanza")
                    }
                } else if (stanza.startsWith("<iq")) {
                    val iq = parseIQ(stanza)
                    if (iq != null) {
                        Log.d(TAG, "Parsed IQ stanza: id=${iq.id}, stanza=$stanza")
                        delegate?.didReceiveIQ(iq, this)
                    } else {
                        Log.w(TAG, "Failed to parse IQ stanza: $stanza")
                    }
                } else if (stanza.contains("<stream:stream") && !stanza.contains("<stream:features")) {
                    Log.d(TAG, "Received stream header: $stanza")
                    delegate?.didReceiveStreamHeader(stanza, this)
                } else if (stanza.contains("<stream:features>")) {
                    Log.d(TAG, "Received stream features: $stanza")
                    delegate?.didReceiveStreamFeatures(stanza, this)
                } else if (stanza.contains("<challenge")) {
                    Log.d(TAG, "Received challenge: $stanza")
                    delegate?.didReceiveChallenge(stanza, this)
                } else if (stanza.contains("<success")) {
                    Log.d(TAG, "Received success: $stanza")
                    delegate?.didReceiveSuccess(stanza, this)
                } else if (stanza.contains("<failure")) {
                    Log.d(TAG, "Received failure: $stanza")
                    delegate?.didReceiveFailure(stanza, this)
                } else if (stanza.contains("<proceed")) {
                    Log.d(TAG, "Received proceed: $stanza")
                    delegate?.didReceiveProceed(stanza, this)
                } else if (stanza.contains("<presence")) {
                    Log.d(TAG, "Received presence stanza: $stanza")
                    delegate?.didReceivePresence(stanza, this)
                } else if (stanza.contains("<stream:error")) {
                    Log.e(TAG, "Received stream error: $stanza")
                    onErrorCallback?.invoke("Stream error occurred")
                    state = StreamState.NOT_CONNECTING
                } else if (stanza.contains("</stream:stream>")) {
                    Log.w(TAG, "Received stream termination: $stanza")
                    onErrorCallback?.invoke("Connection closed by server")
                    state = StreamState.NOT_CONNECTING
                } else {
                    Log.w(TAG, "Unhandled stanza: $stanza")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing stanza: ${e.message}, stanza=$stanza", e)
                onErrorCallback?.invoke("Error processing stanza: ${e.message}")
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
                Log.d(TAG, "Retrying unprocessed message: id=${stanza.messageId}, primary=${stanza.primary}, stanza=${stanza.stanza}")
                withContext(Dispatchers.IO) {
                    delegate?.didReceiveMessage(stanza.stanza, this@Stream)
                    realm.write {
                        val latest = findLatest(stanza)
                        if (latest != null) {
                            latest.isProcessed = true
                            Log.d(TAG, "Marked retried message as processed: id=${stanza.messageId}, primary=${stanza.primary}")
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