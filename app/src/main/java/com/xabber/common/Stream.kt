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
import com.xabber.utils.toMessageReferenceDto
import com.xabber.xmpp.XEP_0CCC.ClientSynchronizationManager
import com.xabber.xmpp.dns.DNSResolver
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.message.TemporaryMessageStanzaStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
    val messageQueue = Channel<MessageQueueItem>(Channel.UNLIMITED)
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
            processMessageQueue()
        }
        deleteSelfChats()
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
                    Log.d(TAG, "Received message via callback: ${message.substring(0, minOf(message.length, 400))}...")
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
        Log.d(TAG, "Handling incoming message: ${chunk.substring(0, minOf(chunk.length, 200))}...")
        try {
            streamBuffer.append(chunk)
            var content = streamBuffer.toString()
            while (content.isNotEmpty()) {
                val start = content.indexOf("<")
                if (start == -1) break
                if (content.startsWith("<?xml") || content.indexOf("<stream:stream", start) == start || content.indexOf("<stream:error", start) == start || content.indexOf("<stream:features", start) == start) {
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
                    fullEnd = stanzaEnd + 1
                } else {
                    // Use a stack-based approach to find the correct closing tag
                    var openTags = 1
                    var currentIndex = tagEnd + 1
                    while (openTags > 0 && currentIndex < content.length) {
                        val nextOpen = content.indexOf("<$tagName", currentIndex)
                        val nextClose = content.indexOf("</$tagName>", currentIndex)
                        if (nextClose == -1) {
                            Log.w(TAG, "No closing tag found for $tagName, breaking")
                            break
                        }
                        if (nextOpen != -1 && nextOpen < nextClose) {
                            openTags++
                            currentIndex = content.indexOf(">", nextOpen) + 1
                        } else {
                            openTags--
                            currentIndex = nextClose + "</$tagName>".length
                        }
                    }
                    if (openTags == 0) {
                        stanzaEnd = currentIndex - "</$tagName>".length
                        fullEnd = currentIndex
                    } else {
                        Log.w(TAG, "Incomplete stanza for $tagName, buffering: ${content.substring(0, minOf(content.length, 200))}...")
                        break
                    }
                }
                val stanza = content.substring(start, fullEnd)
                processStanza(stanza)
                Log.d(TAG, "Dispatched stanza: ${stanza.substring(0, minOf(stanza.length, 200))}...")
                content = content.substring(fullEnd)
            }
            streamBuffer.clear()
            streamBuffer.append(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}, chunk: $chunk", e)
            onErrorCallback?.invoke("Error processing server response: ${e.message}")
            state = StreamState.NOT_CONNECTING
        }
    }

    private suspend fun processStanza(stanza: String) {
        Log.d(TAG, "Received stanza: ${stanza.substring(0, minOf(stanza.length, 200))}...")
        try {
            if (stanza.startsWith("<message")) {
                // Parse message attributes manually
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
                val innerRaw = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tagName = parser.name
                            val namespace = parser.namespace
                            if (tagName == "message" && namespace == "jabber:client" && !inForwarded) {
                                messageId = parser.getAttributeValue(null, "id") ?: "unknown_${System.currentTimeMillis()}"
                                from = parser.getAttributeValue(null, "from") ?: jid
                                to = parser.getAttributeValue(null, "to") ?: jid
                                type = parser.getAttributeValue(null, "type")
                                lang = parser.getAttributeValue(null, "xml:lang")
                            } else if (tagName == "forwarded" && namespace == "urn:xmpp:forward:0") {
                                inForwarded = true
                            } else if (inForwarded && tagName == "message" && namespace == "jabber:client") {
                                innerMessageId = parser.getAttributeValue(null, "id") ?: "unknown_${System.currentTimeMillis()}"
                                innerFrom = parser.getAttributeValue(null, "from") ?: jid
                                innerTo = parser.getAttributeValue(null, "to") ?: jid
                                innerType = parser.getAttributeValue(null, "type")
                                innerLang = parser.getAttributeValue(null, "xml:lang")
                                innerRaw.append("<message")
                                for (i in 0 until parser.attributeCount) {
                                    innerRaw.append(" ${parser.getAttributeName(i)}='${parser.getAttributeValue(i)}'")
                                }
                                innerRaw.append(">")
                            } else if (tagName in listOf("active", "composing", "inactive") && namespace == "http://jabber.org/protocol/chatstates") {
                                isChatState = true
                                innerRaw.append("<$tagName xmlns='$namespace'/>")
                            } else if (tagName == "body" && (inForwarded || !inForwarded)) {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    if (inForwarded) {
                                        innerBody = parser.text.trim()
                                        innerRaw.append("<body>${parser.text}</body>")
                                    } else {
                                        body = parser.text.trim()
                                    }
                                }
                            } else if (inForwarded && namespace != "jabber:client") {
                                innerRaw.append("<${tagName} xmlns='${namespace}'")
                                for (i in 0 until parser.attributeCount) {
                                    innerRaw.append(" ${parser.getAttributeName(i)}='${parser.getAttributeValue(i)}'")
                                }
                                innerRaw.append("/>")
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val tagName = parser.name
                            if (tagName == "forwarded" && parser.namespace == "urn:xmpp:forward:0") {
                                inForwarded = false
                            } else if (inForwarded && tagName == "message" && parser.namespace == "jabber:client") {
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

                // Use inner message ID for forwarded messages
                messageId = innerMessageId ?: messageId ?: "unknown_${System.currentTimeMillis()}"
                Log.d(TAG, "Received message stanza: id=$messageId, isChatState=$isChatState, from=$from, to=$to, innerFrom=$innerFrom, innerTo=$innerTo")

                // Skip self-directed messages
                val opponent = if (innerTo != jid) innerTo ?: to ?: jid else innerFrom ?: from ?: jid
                if (opponent == jid) {
                    Log.w(TAG, "Skipping self-directed message: id=$messageId, from=$from, to=$to, innerFrom=$innerFrom, innerTo=$innerTo")
                    return
                }

                // Skip chat state notifications or messages with no body
                if (isChatState || (innerBody.isNullOrEmpty() && body.isNullOrEmpty())) {
                    Log.d(TAG, "Skipping storage for chat state or empty message: id=$messageId")
                    if (delegate != null) {
                        withContext(Dispatchers.IO) {
                            Log.d(TAG, "Dispatching chat state to delegate: id=$messageId, delegate=${delegate?.javaClass?.name}")
                            delegate?.didReceiveMessage(stanza, this@Stream)
                            Log.d(TAG, "Dispatched chat state notification to delegate: id=$messageId")
                        }
                    } else {
                        Log.e(TAG, "Delegate is null, cannot dispatch chat state notification: id=$messageId")
                    }
                    return
                }

                // Check for duplicates before processing
                val realm = Realm.open(defaultRealmConfig())
                val primary = TemporaryMessageStanzaStorageItem.genPrimary(messageId, jid)
                val existing = realm.query<TemporaryMessageStanzaStorageItem>("primary = $0", primary).first().find()
                if (existing != null && existing.isProcessed) {
                    Log.d(TAG, "Skipping duplicate message: id=$messageId, primary=$primary")
                    realm.close()
                    return
                }

                // Create XMPPMessage with parsed attributes
                val xmppMessage = XMPPMessage(
                    raw = stanza,
                    type = innerType ?: type,
                    id = messageId,
                    from = innerFrom?.let { XMPPJID(fullJID = it) } ?: from?.let { XMPPJID(fullJID = it) } ?: XMPPJID(fullJID = jid),
                    to = innerTo?.let { XMPPJID(fullJID = it) } ?: to?.let { XMPPJID(fullJID = it) } ?: XMPPJID(fullJID = jid),
                    lang = innerLang ?: lang,
                    body = innerBody ?: body
                )

                // Calculate timestamp
                val timestamp = parseTimestamp(xmppMessage) ?: System.currentTimeMillis()

                // Store the message stanza in TemporaryMessageStanzaStorageItem
                realm.write {
                    val tempStanza = TemporaryMessageStanzaStorageItem().apply {
                        this.primary = primary
                        owner = jid
                        jid = opponent
                        this.messageId = messageId
                        date = timestamp
                        this.stanza = stanza
                        isProcessed = false
                    }
                    copyToRealm(tempStanza, UpdatePolicy.ALL)
                    Log.d(TAG, "Stored TemporaryMessageStanzaStorageItem: messageId=$messageId, primary=$primary, opponent=$opponent")
                }

                // Verify storage
                val storedStanza = realm.query<TemporaryMessageStanzaStorageItem>(
                    "primary = $0", primary
                ).first().find()
                if (storedStanza != null) {
                    Log.d(TAG, "Verified storage: messageId=$messageId, primary=$primary, owner=${storedStanza.owner}, isProcessed=${storedStanza.isProcessed}")
                } else {
                    Log.e(TAG, "Failed to verify storage for messageId=$messageId, primary=$primary")
                }
                realm.close()

                // Route to delegate's didReceiveMessage
                if (delegate != null) {
                    withContext(Dispatchers.IO) {
                        Log.d(TAG, "Delegate state before dispatch: id=$messageId, delegate=${delegate?.javaClass?.name ?: "null"}")
                        if (delegate == null) {
                            Log.e(TAG, "Delegate is null, cannot dispatch message: id=$messageId")
                            val account = AccountManager.find(jid)
                            if (account != null) {
                                delegate = account
                                Log.d(TAG, "Reassigned delegate for jid=$jid")
                            } else {
                                Log.e(TAG, "No account found for jid=$jid, cannot reassign delegate")
                                return@withContext
                            }
                        }
                        delegate?.didReceiveMessage(stanza, this@Stream)
                        Log.d(TAG, "Dispatched message to delegate: id=$messageId")
                    }
                } else {
                    Log.e(TAG, "Delegate is null, cannot dispatch message: id=$messageId")
                }

                // Queue the message for further processing
                val isCarbon = xmppMessage.element("sent", namespace = "urn:xmpp:carbons:2") != null ||
                        xmppMessage.element("received", namespace = "urn:xmpp:carbons:2") != null
                val isArchived = xmppMessage.element("archived", namespace = "urn:xmpp:mam:tmp") != null
                val isClientSync = xmppMessage.element("synchronization", namespace = "https://xabber.com/protocol/synchronization") != null
                val queryId = xmppMessage.element("archived", namespace = "urn:xmpp:mam:tmp")?.getAttribute("id")
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
                    Log.d(TAG, "Enqueued message: id=$messageId, isCarbon=$isCarbon, isArchived=$isArchived, isClientSync=$isClientSync")
                } else {
                    Log.w(TAG, "Skipping enqueue for invalid message: id=$messageId, from=${xmppMessage.from?.bare()}, to=${xmppMessage.to?.bare()}")
                }
            } else if (stanza.contains("urn:xmpp:mam:tmp") || stanza.contains("urnlabels")) {
                Log.d(TAG, "Received MAM response: $stanza")
                ClientSynchronizationManager(jid).read(stanza)
            } else if (stanza.startsWith("<iq")) {
                val iq = parseIQ(stanza)
                if (iq != null) {
                    Log.d(TAG, "Parsed IQ stanza: id=${iq.id}")
                    delegate?.didReceiveIQ(iq, this)
                } else {
                    Log.w(TAG, "Failed to parse IQ stanza: $stanza")
                }
            } else if (stanza.contains("<stream:stream") && !stanza.contains("<stream:features")) {
                Log.d(TAG, "Received stream header")
                delegate?.didReceiveStreamHeader(stanza, this)
            } else if (stanza.contains("<stream:features>")) {
                Log.d(TAG, "Received stream features")
                delegate?.didReceiveStreamFeatures(stanza, this)
            } else if (stanza.contains("<challenge")) {
                Log.d(TAG, "Received challenge")
                delegate?.didReceiveChallenge(stanza, this)
            } else if (stanza.contains("<success")) {
                Log.d(TAG, "Received success")
                delegate?.didReceiveSuccess(stanza, this)
            } else if (stanza.contains("<failure")) {
                Log.d(TAG, "Received failure")
                delegate?.didReceiveFailure(stanza, this)
            } else if (stanza.contains("<proceed")) {
                Log.d(TAG, "Received proceed")
                delegate?.didReceiveProceed(stanza, this)
            } else if (stanza.contains("<presence")) {
                Log.d(TAG, "Received presence stanza")
                delegate?.didReceivePresence(stanza, this)
            } else if (stanza.contains("<stream:error")) {
                Log.e(TAG, "Received stream error: $stanza")
                onErrorCallback?.invoke("Stream error occurred")
                state = StreamState.NOT_CONNECTING
            } else if (stanza.contains("</stream:stream>")) {
                Log.w(TAG, "Received stream termination")
                onErrorCallback?.invoke("Connection closed by server")
                state = StreamState.NOT_CONNECTING
            } else {
                Log.w(TAG, "Unhandled stanza: ${stanza.substring(0, minOf(stanza.length, 200))}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing stanza: ${e.message}, stanza: $stanza", e)
            onErrorCallback?.invoke("Error processing stanza: ${e.message}")
        }
    }

    private suspend fun processMessageQueue() {
        for (item in messageQueue) {
            if (item.message.id == null || item.message.from == null || item.message.to == null) {
                Log.w(TAG, "Skipping invalid queue item: id=${item.message.id}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}")
                continue
            }
            Log.d(TAG, "Processing queued message: id=${item.message.id}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}")
            try {
                val realm = Realm.open(defaultRealmConfig())
                realm.write {
                    val messageId = item.message.id!!
                    val from = item.message.from?.bare() ?: return@write
                    val to = item.message.to?.bare() ?: jid
                    val opponent = if (to != jid) to else from
                    val isOutgoing = from == jid
                    val conversationType = when {
                        item.isClientSync && to == "favorites.redsolution.com" -> "urn:xabber:favorites:0"
                        item.message.element("x", namespace = "https://xabber.com/protocol/groups") != null -> "https://xabber.com/protocol/groups"
                        else -> "urn:xabber:chat"
                    }

                    val existingMessage = query<MessageStorageItem>("primary = $0", MessageStorageItem.genPrimary(messageId, jid)).first().find()
                    if (existingMessage != null) {
                        Log.d(TAG, "Skipping duplicate message: id=$messageId, primary=${existingMessage.primary}")
                        return@write
                    }

                    val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", opponent, jid).first().find()
                        ?: copyToRealm(RosterStorageItem().apply {
                            primary = RosterStorageItem.genPrimary(opponent, jid)
                            this.jid = opponent
                            this.owner = jid
                            this.customNickname = opponent
                        }, UpdatePolicy.ALL)

                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = MessageStorageItem.genPrimary(messageId, jid)
                        this.messageId = messageId
                        this.owner = jid
                        this.opponent = opponent
                        this.body = item.message.body ?: ""
                        this.date = item.timestamp
                        this.sentDate = item.timestamp
                        this.editDate = 0L
                        this.outgoing = isOutgoing
                        this.conversationType_ = conversationType
                        this.isRead = isOutgoing || item.isArchived
                        this.state = if (isOutgoing) MessageStorageItem.MessageSendingState.DELIVERED else MessageStorageItem.MessageSendingState.SENT
                    }, UpdatePolicy.ALL)

                    val chatPrimary = LastChatsStorageItem.genPrimary(opponent, jid, ConversationType.fromRaw(conversationType))
                    val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find()
                    if (chat == null) {
                        copyToRealm(LastChatsStorageItem().apply {
                            primary = chatPrimary
                            this.jid = opponent
                            this.owner = jid
                            this.conversationType_ = conversationType
                            this.isArchived = false
                            this.unread = if (isOutgoing || item.isArchived) 0 else 1
                            this.messageDate = item.timestamp
                            this.lastMessageId = messageId
                            this.pinnedPosition = 0
                            this.muteExpired = -1
                            this.rosterItem = rosterItem
                            this.lastMessage = message
                        }, UpdatePolicy.ALL)
                        Log.d(TAG, "Created new LastChatsStorageItem for jid=$opponent, type=$conversationType")
                    } else {
                        findLatest(chat)?.apply {
                            this.unread = if (isOutgoing || item.isArchived) this.unread else this.unread + 1
                            this.messageDate = item.timestamp
                            this.lastMessageId = messageId
                            this.lastMessage = message
                            this.isArchived = false
                        }
                        Log.d(TAG, "Updated LastChatsStorageItem for jid=$opponent, type=$conversationType")
                    }

                    // Convert MessageStorageItem to MessageDto
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
                        displayType = MessageDisplayType.Text,
                        canEditMessage = message.outgoing,
                        canDeleteMessage = message.outgoing,
                        urlAvatar = null,
                        isGroup = message.conversationType_ == "https://xabber.com/protocol/groups",
                        kind = null,
                        isSelected = false,
                        references = message.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                        isUnread = !message.isRead,
                        isChecked = false
                    )

                    // Notify ChatViewModel
                    val chatViewModel = AccountManager.getChatViewModel(chatPrimary)
                    if (chatViewModel != null) {
                        chatViewModel.insertMessagesFromReceiver(listOf(messageDto))
                        Log.d(TAG, "Notified ChatViewModel for chatId=$chatPrimary with message $messageId")
                    } else {
                        Log.w(TAG, "ChatViewModel not found for chatId=$chatPrimary, messageId=$messageId")
                    }
                }
                realm.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing queued message: ${e.message}, id=${item.message.id}", e)
            }
        }
    }
    fun deleteSelfChats() {
        val realm = Realm.open(defaultRealmConfig())

        realm.writeBlocking {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", jid).find()
            delete(selfChats)
            Log.d(TAG, "Deleted ${selfChats.size} self-chats for owner=$jid")
        }
    }

    private fun parseTimestamp(message: XMPPMessage): Long? {
        val timeElement = message.element("time", namespace = "https://xabber.com/protocol/delivery")
        val stamp = timeElement?.getAttribute("stamp")
        return stamp?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(it)?.time
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse timestamp: ${e.message}")
                null
            }
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
            Log.e(TAG, "Error parsing IQ: ${e.message}", e)
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