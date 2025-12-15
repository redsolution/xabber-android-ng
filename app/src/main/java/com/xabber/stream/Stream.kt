package com.xabber.stream

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.stream.serializers.XMPPIQ
import com.xabber.stream.delegates.XMPPStreamDelegate
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
import com.xabber.utils.parseXMPPDate
import com.xabber.utils.parseXMPPDateToMillis
import com.xabber.utils.toMessageReferenceDto
import com.xabber.xmpp.dns.DNSResolver
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.XMLElement
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
import kotlin.collections.iterator

// Define ProcessedMessageId as a regular class
class ProcessedMessageId : RealmObject {
    @PrimaryKey
    var messageId: String = ""
    var owner: String = ""
    var timestamp: Long = 0L

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
    @PrimaryKey
    var host: String = extractHostFromJid(jid)
    var remoteAddress: String = ""
    var socket: Socket? = null
    private val connectionLock = Any()
    private var isConnecting = false
    private val streamBuffer = StringBuilder()
    private val bufferMutex = Mutex()
    private val stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(999) + SupervisorJob())
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
                    messageCallbackChannel.send(message)
                    handleIncomingStanza(message)
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
            logUnprocessedMessages(jid)
//                retryUnprocessedMessages()
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

    private suspend fun handleIncomingStanza(chunk: String) {
        bufferMutex.withLock {
            streamBuffer.append(chunk)
            var content = streamBuffer.toString()
            var processedStanzas = 0
            while (content.isNotEmpty()) {
                val start = content.indexOf("<")
                if (start == -1) {
                    Log.w(TAG, "No XML start tag found in buffer: ${content.take(200)}")
                    break
                }

                // Handle stream headers and special cases
                if (content.startsWith("<?xml", start) || content.indexOf("<stream:stream", start) == start) {
                    val end = content.indexOf(">", start)
                    if (end == -1) {
                        Log.w(TAG, "Incomplete stream header, buffering: ${content.take(200)}")
                        break
                    }
                    val header = content.substring(start, end + 1)
                    stanzaProcessingScope.launch {
                        delegate?.didReceiveStreamHeader(header, this@Stream)
                    }
                    content = content.substring(end + 1).trimStart()
                    processedStanzas++
                    continue
                }

                if (content.indexOf("<stream:features>", start) == start) {
                    val end = content.indexOf("</stream:features>", start) + "</stream:features>".length
                    if (end == -1) {
                        Log.w(TAG, "Incomplete stream features, buffering: ${content.take(200)}")
                        break
                    }
                    val features = content.substring(start, end)
                    stanzaProcessingScope.launch {
                        delegate?.didReceiveStreamFeatures(features, this@Stream)
                    }
                    content = content.substring(end).trimStart()
                    processedStanzas++
                    continue
                }

                if (content.indexOf("<stream:error>", start) == start) {
                    val end = content.indexOf("</stream:error>", start) + "</stream:error>".length
                    if (end == -1) {
                        Log.w(TAG, "Incomplete stream error, buffering: ${content.take(200)}")
                        break
                    }
                    val error = content.substring(start, end)
                    stanzaProcessingScope.launch {
                        Log.e(TAG, "Received stream error: $error")
                        onErrorCallback?.invoke("Stream error occurred")
                        state = StreamState.NOT_CONNECTING
                    }
                    content = content.substring(end).trimStart()
                    processedStanzas++
                    continue
                }

                if (content.indexOf("</stream:stream>", start) == start) {
                    val end = content.indexOf("</stream:stream>", start) + "</stream:stream>".length
                    if (end == -1) {
                        Log.w(TAG, "Incomplete stream termination, buffering: ${content.take(200)}")
                        break
                    }
                    val termination = content.substring(start, end)
                    stanzaProcessingScope.launch {
                        Log.w(TAG, "Received stream termination: $termination")
                        onErrorCallback?.invoke("Connection closed by server")
                        state = StreamState.NOT_CONNECTING
                    }
                    content = content.substring(end).trimStart()
                    processedStanzas++
                    continue
                }

                // Determine the stanza type
                val iqStart = content.indexOf("<iq", start)
                val presenceStart = content.indexOf("<presence", start)
                val messageStart = content.indexOf("<message", start)

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
                    Log.w(TAG, "Incomplete stanza tag, buffering: ${content}")
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
                            break
                        }
                        if (nextOpen != -1 && nextOpen < nextClose) {
                            val gtPos = content.indexOf(">", nextOpen)
                            if (gtPos == -1) {
                                break
                            }
                            openTags++
                            currentIndex = gtPos + 1
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
                        break
                    }
                }
                val stanza = content.substring(nextStart.first, fullEnd)
                Log.d("XMPP STANZA", "RECV:$stanza")

                stanzaProcessingScope.launch {
                    try {
                        when (tagName) {
                            "iq" -> {
//                                    TODO val result = delegate call result, addd return XMPP ERROR Stanza, <iq type=error
                                val iq = parseIQ(stanza)
                                delegate?.didReceiveIQ(iq!!, this@Stream)
                            }
                            "message" -> {
                                var message = try {
                                    parseMessage(stanza)
                                } catch (e: Exception) {
                                    Log.w(TAG, "parseMessage failed, using fallback: ${e.message}")
                                    null
                                }

                                if (message == null) {
                                    message = extractFallbackMessage(stanza)
                                    if (message != null) {
                                        Log.w(TAG, "Used fallback parsing for message: id=${message.id}")
                                    } else {
                                        Log.e(TAG, "Both parsers failed, dropping message")
                                        return@launch
                                    }
                                }
                                delegate?.didReceiveMessage(message, this@Stream)
                            }
                            "presence" -> {
                                delegate?.didReceivePresence(stanza, this@Stream)
                            }
                            "challenge" -> {
                                delegate?.didReceiveChallenge(stanza, this@Stream)
                            }
                            "success" -> {
                                delegate?.didReceiveSuccess(stanza, this@Stream)
                            }
                            "proceed" -> {
                                delegate?.didReceiveProceed(stanza, this@Stream)
                            }
                            "failure" -> {
                                delegate?.didReceiveFailure(stanza, this@Stream)
                            }
                            else -> {
                                Log.w(TAG, "Unhandled stanza type: $tagName, stanza: ${stanza.take(200)}")
                                onErrorCallback?.invoke("Unhandled stanza type: $tagName")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing stanza: ${e.message}, stanza=$stanza", e)
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

    private fun extractFallbackMessage(stanza: String): XMPPMessage? {
        val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(stanza)
        val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(stanza)
        val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(stanza)
        val bodyMatch = Regex("""<body[^>]*>([^<]+)</body>""", RegexOption.DOT_MATCHES_ALL).find(stanza)
        val originMatch = Regex("""<origin-id[^>]+id=['"]([^'"]+)['"]""").find(stanza)
        val archivedMatch = Regex("""<archived[^>]+id=['"]([^'"]+)['"]""").find(stanza)
        val stampMatch = Regex("""stamp=['"]([^'"]+)['"]""").find(stanza)

        val id = idMatch?.groupValues?.get(1) ?: return null
        val fromStr = fromMatch?.groupValues?.get(1) ?: return null
        val toStr = toMatch?.groupValues?.get(1) ?: return null
        val body = bodyMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val originId = originMatch?.groupValues?.get(1)
        val archivedId = archivedMatch?.groupValues?.get(1) ?: ""
        val timestamp = stampMatch?.groupValues?.get(1)?.parseXMPPDateToMillis() ?: System.currentTimeMillis()

        return XMPPMessage(
            raw = stanza,
            type = "chat",
            id = id,
            from = try { XMPPJID(fromStr) } catch (e: Exception) { return null },
            to = try { XMPPJID(toStr) } catch (e: Exception) { return null },
            date = timestamp,
            body = body,
            originId = originId,
            archivedId = archivedId
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
                    Log.w(TAG, "Skipping invalid queue item: id=${item.message.id}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}, stanza=${item.stanza}")
                    return@withLock
                }
                val messageId = item.message.id
                if (messageId in processedIds) {
                    Log.d(TAG, "Already processed messageId=$messageId, checking if in MessageStorageItem")
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
                val from = item.message.from.bare() ?: return@withLock
                val to = item.message.to.bare() ?: return@withLock
                var isOutgoing = item.isArchived ?: (from == jid)
                val opponent = if (isOutgoing) to else from
                if (item.message.body.isNullOrEmpty()) {
                    Log.d(TAG, "Skipping message with no body: id=$messageId, stanza=${item.stanza}")
                    return@withLock
                }
                Log.d(TAG, "Processing queued message: id=$messageId, from=$from, to=$to, body=${item.message.body.take(50)}, isOutgoing=$isOutgoing, isArchived=${item.isArchived}, thread=${Thread.currentThread().id}")
                try {
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
                        if (isGroupChat) {
                            val userId = item.message.element("x", namespace = "https://xabber.com/protocol/groups")
                                ?.element("reference", namespace = "https://xabber.com/protocol/references")
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
                                this.rosterItem = rosterItem
                                this.lastMessage = message
                            }, UpdatePolicy.ALL)
                            Log.d(TAG, "Created new LastChatsStorageItem for jid=$opponent, type=${conversationType.rawValue}, messageId=$messageId, timestamp=${item.timestamp}, isOutgoing=$isOutgoing")
                        } else {
                            findLatest(chat)?.apply {
                                if (item.timestamp / 10000 > this.messageDate) {
                                    this.unread = if (isOutgoing || item.isArchived) this.unread else this.unread + 1
                                    this.messageDate = item.timestamp
                                    this.lastMessageId = messageId
                                    this.lastMessage = message
                                    this.isArchived = false
                                    Log.d(TAG, "Updated LastChatsStorageItem to latest: jid=$opponent, type=${conversationType.rawValue}, messageId=$messageId, timestamp=${item.timestamp / 10000}, isOutgoing=$isOutgoing")
                                } else {
                                    Log.d(TAG, "Skipped LastChatsStorageItem update, older timestamp: jid=$opponent, messageId=$messageId, timestamp=${item.timestamp / 10000}, current=${this.messageDate}")
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
                        Log.w("CHECK", "check it STREA ${message.archivedId}")

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
                            this.timestamp = item.timestamp/10000
                        }, UpdatePolicy.ALL)
                    }
                    realm.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing queued message: ${e.message}, id=$messageId, stanza=${item.stanza}", e)
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

    suspend fun logUnprocessedMessages(owner: String) {
        val realm = Realm.open(defaultRealmConfig())
        realm.write {
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

    fun parseIQ(stanza: String): XMPPIQ? {
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
            Log.e(TAG, "Error parsing IQ: error ${e.message}, stanza=$stanza", e)
            return null
        }
    }


    fun parseMessage(stanza: String): XMPPMessage? {
        try {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(stanza))

            var type: String? = null
            var id: String? = null
            var from: XMPPJID? = null
            var to: XMPPJID? = null
            var lang: String? = null

            var body: String? = null
            var originId: String? = null
            var archivedId: String? = null
            var queryId: String? = null
            var timestamp: Long? = null

            var realFrom: XMPPJID? = null
            var realTo: XMPPJID? = null
            var realId: String? = null

            var inForwarded = false
            var currentMessageDepth = 0
            var targetMessageDepth = -1  // Depth of the message whose children we collect

            // For preserving full child hierarchy of the target <message>
            val elementStack = mutableListOf<XMLElement>()
            val rawBuilders = mutableListOf<StringBuilder>()
            val targetChildren = mutableListOf<XMLElement>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        val namespace = parser.namespace.ifEmpty { null }
                        val attrs = mutableMapOf<String, String>()
                        for (i in 0 until parser.attributeCount) {
                            attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        }

                        // === Original logic: extract known fields ===
                        when (name) {
                            "message" -> {
                                currentMessageDepth++
                                if (currentMessageDepth == 1) {
                                    type = attrs["type"] ?: "chat"
                                    id = attrs["id"]
                                    from = attrs["from"]?.let { XMPPJID(it) }
                                    to = attrs["to"]?.let { XMPPJID(it) }
                                    lang = attrs["xml:lang"]
                                }
                                // Improved target detection (from new version)
                                if (targetMessageDepth == -1) {
                                    if (inForwarded) {
                                        targetMessageDepth = currentMessageDepth
                                        realFrom = attrs["from"]?.let { XMPPJID(it) } ?: from
                                        realTo = attrs["to"]?.let { XMPPJID(it) } ?: to
                                        realId = attrs["id"] ?: id
                                        type = attrs["type"] ?: type ?: "chat"
                                    } else {
                                        // Direct message → root is target
                                        targetMessageDepth = 1
                                        realFrom = from
                                        realTo = to
                                        realId = id
                                    }
                                }
                            }
                            "result" -> {
                                if (namespace == "urn:xmpp:mam:2") {
                                    archivedId = attrs["id"]
                                    queryId = attrs["queryid"]
                                }
                            }
                            "forwarded" -> {
                                if (namespace == "urn:xmpp:forward:0") {
                                    inForwarded = true
                                }
                            }
                            "delay" -> {
                                if (namespace == "urn:xmpp:delay") {
                                    attrs["stamp"]?.let {
                                        timestamp = it.parseXMPPDateToMillis() ?: timestamp
                                    }
                                }
                            }
                            "time" -> {
                                if (namespace == "https://xabber.com/protocol/delivery") {
                                    attrs["stamp"]?.let {
                                        val t = it.parseXMPPDateToMillis()
                                        if (t != null && (timestamp == null || t > timestamp!!)) timestamp = t
                                    }
                                }
                            }
                            "origin-id" -> {
                                if (namespace == "urn:xmpp:sid:0") {
                                    originId = attrs["id"]
                                }
                            }
                            "body" -> {
                                // === Critical: use original working body extraction ===
                                if (currentMessageDepth == targetMessageDepth) {
                                    val text = parser.nextText()
                                    if (text.isNotBlank()) {
                                        body = text.trim()
                                    }
                                }
                            }
                        }

                        // === New logic: build element hierarchy and raw XML ===
                        if (targetMessageDepth != -1 && currentMessageDepth >= targetMessageDepth) {
                            val element = XMLElement(
                                name = name,
                                namespace = namespace,
                                raw = "",
                                attributes = attrs.toMap(),
                                children = mutableListOf()
                            )

                            val openingTag = buildString {
                                append("<$name")
                                if (namespace != null) append(" xmlns='$namespace'")
                                attrs.forEach { (k, v) -> append(" $k='$v'") }
                                append(">")
                            }
                            rawBuilders.add(StringBuilder(openingTag))
                            elementStack.add(element)

                            // Add to hierarchy
                            if (elementStack.size == 1) {
                                targetChildren.add(element)
                            } else {
                                (elementStack[elementStack.size - 2].children as MutableList).add(element)
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text
                        if (text.isNotBlank() && targetMessageDepth != -1 && currentMessageDepth >= targetMessageDepth && rawBuilders.isNotEmpty()) {
                            rawBuilders.last().append(text.escapeXml())
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "message") currentMessageDepth--
                        if (name == "forwarded") inForwarded = false

                        // Close element if we're inside target scope
                        if (targetMessageDepth != -1 && currentMessageDepth + 1 >= targetMessageDepth && elementStack.isNotEmpty()) {
                            val element = elementStack.removeLast()
                            rawBuilders.last().append("</$name>")
                            element.raw = rawBuilders.removeLast().toString()
                        }
                    }
                }
                event = parser.next()
            }

            val finalFrom = realFrom ?: from
            val finalTo = realTo ?: to
            val finalId = realId ?: originId ?: id

            // === Use original filtering logic (safe and proven) ===
            if (body == null) {
                val hasKnownExtension = stanza.contains("http://jabber.org/protocol/chatstates") ||
                        stanza.contains("urn:xmpp:chat-markers") ||
                        stanza.contains("urn:xmpp:receipt") ||
                        stanza.contains("urn:xmpp:carbons") ||
                        type == "headline" || type == "error"

                if (!hasKnownExtension && targetChildren.isEmpty()) {
                    return null
                }
            }

            // === Original fallback timestamp ===
            if (timestamp == null) {
                Regex("""<delay[^>]+stamp=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
                    ?.let { timestamp = it.parseXMPPDateToMillis() }
            }

            Log.w("CHECK ENGINE", "CHECK STREAM XMPP MESSAGE FORMING: $targetChildren")

            return XMPPMessage(
                raw = stanza,
                type = type,
                id = finalId,
                from = finalFrom,
                to = finalTo,
                lang = lang,
                date = timestamp,
                body = body,
                originId = originId,
                archivedId = archivedId ?: queryId?.let { "query:$it" },
                children = targetChildren  // Now fully populated for both direct and forwarded messages
            )

        } catch (e: Exception) {
            Log.e("Stream", "Failed to parse message stanza: ${e.message}\nStanza: ${stanza.take(1000)}", e)
            return null
        }
    }

    // Helper remains the same
    private fun String.escapeXml(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

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

    fun onNotConnecting() {}
    suspend fun onStreamOpen() {}
    suspend fun onProceed() {
        Log.d(TAG, "Awaiting stream features after TLS upgrade for JID: $jid")
    }
    suspend fun onProcessAuth() {
        Log.d(TAG, "Awaiting authentication response for JID: $jid")
    }
}