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
import com.xabber.utils.parseXMPPDateToMillis
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.collections.iterator
import kotlin.coroutines.cancellation.CancellationException

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
    private val streamJob = SupervisorJob()
    private val streamScope = CoroutineScope(Dispatchers.IO + streamJob)
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
    private var onSocketReadLoopError: (() -> Unit)? = null

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
        streamScope.launch {
            clearStaleProcessedMessages() // Add this to clear stale entries on initialization
            processMessageQueue()

        }
        deleteSelfChats()

    }

    fun setOnSocketReadLoopError(callback: () -> Unit) {
        onSocketReadLoopError = callback
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
            val resolver = DNSResolver
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
            onSocketReadLoopError?.let { socket?.setOnReadLoopError(it) }
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
//            debugDatabaseState()
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
                    else -> content.substring(start + 1, content.indexOf(">", start).takeIf { it != -1 } ?: content.length)
                        .split(Regex("\\s+"))[0]
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
                Log.d("XMPP STANZA READ", "RECV:$stanza")

                // Обработка каждой отдельной станзы в отдельном try-catch,
                // чтобы ошибка в одной станзе не прерывала цикл обработки остальных
                try {
                    when (tagName) {
                        "iq" -> {
                            val iq = parseIQ(stanza)
                            stanzaProcessingScope.launch {
                                try {
                                    delegate?.didReceiveIQ(iq!!, this@Stream)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Delegate error on IQ: ${e.message}", e)
                                }
                            }
                        }
                        "message" -> {
                            var message: XMPPMessage? = null

                            // Первичный парсер
                            try {
                                message = parseMessage(stanza)
                            } catch (e: XmlPullParserException) {
                                Log.w(TAG, "Primary parser failed (malformed XML): ${e.message}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Primary parser failed: ${e.message}", e)
                            }

                            // Fallback-парсер, если первичный не справился
                            if (message == null) {
                                message = extractFallbackMessage(stanza)
                                if (message != null) {
                                    Log.i(TAG, "Recovered via fallback parser: id=${message.id}, body=${message.body?.take(50)}")
                                } else {
                                    Log.w(TAG, "Both parsers failed – skipping malformed message")
                                    // Продолжаем обработку следующей станзы
                                }
                            }

                            // Передаём сообщение делегату, если удалось получить
                            if (message != null) {
                                stanzaProcessingScope.launch {
                                    try {
                                        delegate?.didReceiveMessage(message, this@Stream)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Delegate error on message: ${e.message}", e)
                                    }
                                }
                            }
                        }
                        "presence" -> {
                            stanzaProcessingScope.launch {
                                try {
                                    delegate?.didReceivePresence(stanza, this@Stream)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Delegate error on presence: ${e.message}", e)
                                }
                            }
                        }
                        "challenge" -> {
                            stanzaProcessingScope.launch {
                                try {
                                    delegate?.didReceiveChallenge(stanza, this@Stream)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Delegate error on challenge: ${e.message}", e)
                                }
                            }
                        }
                        "success" -> {
                            stanzaProcessingScope.launch {
                                try {
                                    delegate?.didReceiveSuccess(stanza, this@Stream)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Delegate error on success: ${e.message}", e)
                                }
                            }
                        }
                        "proceed" -> {
                            stanzaProcessingScope.launch {
                                try {
                                    delegate?.didReceiveProceed(stanza, this@Stream)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Delegate error on proceed: ${e.message}", e)
                                }
                            }
                        }
                        "failure" -> {
                            stanzaProcessingScope.launch {
                                try {
                                    delegate?.didReceiveFailure(stanza, this@Stream)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Delegate error on failure: ${e.message}", e)
                                }
                            }
                        }
                        else -> {
                            Log.w(TAG, "Unhandled stanza type: $tagName, stanza preview: ${stanza.take(200)}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error while processing stanza (continuing with next): ${e.message}\nStanza preview: ${stanza.take(500)}", e)
                }

                content = content.substring(fullEnd).trimStart()
                processedStanzas++
            }
            streamBuffer.clear()
            streamBuffer.append(content)

            if (processedStanzas > 0) {
                Log.d(TAG, "Processed $processedStanzas stanzas in this chunk")
            }
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

        // Загружаем уже обработанные ID один раз в начале
        val initialRealm = Realm.open(defaultRealmConfig())
        try {
            initialRealm.write {
                val storedIds = query<ProcessedMessageId>("owner = $0", jid).find().map { it.messageId }
                processedIds.addAll(storedIds)
                Log.d(TAG, "Loaded ${storedIds.size} processed message IDs for owner=$jid")
            }
        } finally {
            initialRealm.close()
        }

        while (true) {
            val result = messageQueue.receiveCatching()
            if (result.isClosed) {
                Log.d(TAG, "processMessageQueue: channel closed, exiting")
                break
            }
            val item = result.getOrNull() ?: break

            currentCoroutineContext().ensureActive()

            queueMutex.withLock {
                if (item.message.id == null || item.message.from == null || item.message.to == null) {
                    Log.w(TAG, "Skipping invalid queue item: id=${item.message.id}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}, stanza=${item.stanza}")
                    return@withLock
                }

                val messageId = item.message.id!!
                if (messageId in processedIds) {
                    Log.d(TAG, "Already processed messageId=$messageId, checking if in MessageStorageItem")
                    val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                    val tempRealm = Realm.open(defaultRealmConfig())
                    try {
                        val existingMessage = tempRealm.query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
                        if (existingMessage != null) {
                            Log.d(TAG, "Confirmed messageId=$messageId in MessageStorageItem, skipping")
                            return@withLock
                        }
                    } finally {
                        tempRealm.close()
                    }
                    Log.w(TAG, "MessageId=$messageId marked as processed but not in MessageStorageItem, reprocessing")
                }

                val from = item.message.from!!.bare()!!
                val to = item.message.to!!.bare()!!
                var isOutgoing = item.isArchived ?: (from == jid)
                val opponent = if (isOutgoing) to else from

                if (item.message.body.isNullOrEmpty()) {
                    Log.d(TAG, "Skipping message with no body: id=$messageId")
                    return@withLock
                }

                Log.d(TAG, "Processing queued message: id=$messageId, from=$from, to=$to, body=${item.message.body.take(50)}, isOutgoing=$isOutgoing")

                val realm = Realm.open(defaultRealmConfig())
                try {
                    realm.write {
                        val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                        val existingMessage = query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
                        if (existingMessage != null) {
                            Log.d(TAG, "Skipping duplicate message in MessageStorageItem: id=$messageId")
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

                        Log.w("CHECK", "check it STREAM ${message.archivedId}")

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
                        } else {
                            findLatest(chat)?.apply {
                                if (item.timestamp / 10000 > this.messageDate) {
                                    this.unread = if (isOutgoing || item.isArchived) this.unread else this.unread + 1
                                    this.messageDate = item.timestamp
                                    this.lastMessageId = messageId
                                    this.lastMessage = message
                                    this.isArchived = false
                                }
                            }
                        }

                        processedIds.add(messageId)
                        copyToRealm(ProcessedMessageId().apply {
                            this.messageId = messageId
                            this.owner = jid
                            this.timestamp = item.timestamp / 10000
                        }, UpdatePolicy.ALL)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing queued message id=$messageId: ${e.message}", e)
                } finally {
                    realm.close()
                }
            }
        }
    }
    suspend fun debugDatabaseState() {
        val realm = Realm.open(defaultRealmConfig())
        realm.write {

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
            var targetMessageDepth = -1

            // Collects ALL child elements of the target message
            val messageChildren = mutableListOf<XMLElement>()

            // Elements that act as structural wrappers and must NOT be consumed by
            // parseElementFull — their children are iterated by the main loop.
            val structuralTags = setOf("message", "result", "forwarded")

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        val tagNs = parser.namespace ?: ""

                        when {
                            // --- <message> ---------------------------------------------------
                            tagName == "message" -> {
                                currentMessageDepth++
                                if (currentMessageDepth == 1) {
                                    type = parser.getAttributeValue(null, "type") ?: "chat"
                                    id = parser.getAttributeValue(null, "id")
                                    from = parser.getAttributeValue(null, "from")?.let { XMPPJID(it) }
                                    to = parser.getAttributeValue(null, "to")?.let { XMPPJID(it) }
                                    lang = parser.getAttributeValue(null, "xml:lang")
                                }
                                if (inForwarded && targetMessageDepth == -1) {
                                    targetMessageDepth = currentMessageDepth
                                    realFrom = parser.getAttributeValue(null, "from")?.let { XMPPJID(it) } ?: from
                                    realTo = parser.getAttributeValue(null, "to")?.let { XMPPJID(it) } ?: to
                                    realId = parser.getAttributeValue(null, "id") ?: id
                                    type = parser.getAttributeValue(null, "type") ?: type ?: "chat"
                                }
                                // Do NOT consume children — main loop iterates them
                            }

                            // --- <result xmlns='urn:xmpp:mam:2'> ----------------------------
                            tagName == "result" && tagNs == "urn:xmpp:mam:2" -> {
                                archivedId = parser.getAttributeValue(null, "id")
                                queryId = parser.getAttributeValue(null, "queryid")
                                // Structural — main loop iterates its children
                            }

                            // --- <forwarded xmlns='urn:xmpp:forward:0'> ---------------------
                            tagName == "forwarded" && tagNs == "urn:xmpp:forward:0" -> {
                                inForwarded = true
                                // Structural — main loop iterates its children
                            }

                            // --- <body> at message level ------------------------------------
                            tagName == "body" &&
                                (currentMessageDepth == targetMessageDepth || targetMessageDepth == -1) -> {
                                val text = parser.nextText().trim()
                                if (text.isNotBlank()) body = text
                            }

                            // --- All other elements: parse fully and add to children --------
                            else -> {
                                // Whether this is a direct child of the target message
                                val isAtMessageLevel =
                                    (currentMessageDepth == 1 && !inForwarded) ||
                                    (inForwarded && currentMessageDepth == targetMessageDepth)

                                // Extract specific top-level fields before consuming
                                when (tagName) {
                                    "delay" -> if (tagNs == "urn:xmpp:delay") {
                                        val stamp = parser.getAttributeValue(null, "stamp")
                                        stamp?.let { timestamp = it.parseXMPPDateToMillis() ?: timestamp }
                                    }
                                    "time" -> if (tagNs == "https://xabber.com/protocol/delivery") {
                                        val stamp = parser.getAttributeValue(null, "stamp")
                                        stamp?.let {
                                            val timeMillis = it.parseXMPPDateToMillis()
                                            if (timeMillis != null && (timestamp == null || timeMillis > timestamp!!)) {
                                                timestamp = timeMillis
                                            }
                                        }
                                    }
                                    "origin-id" -> if (tagNs == "urn:xmpp:sid:0") {
                                        originId = parser.getAttributeValue(null, "id")
                                    }
                                }

                                // Parse the full element tree
                                val element = parseElementFull(parser, tagName, tagNs)

                                if (isAtMessageLevel) {
                                    messageChildren.add(element)
                                }
                                // parseElementFull leaves parser at END_TAG of this element
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "message") currentMessageDepth--
                        if (parser.name == "forwarded") inForwarded = false
                    }
                }
                event = parser.next()
            }

            val finalFrom = realFrom ?: from
            val finalTo = realTo ?: to
            val finalId = realId ?: originId ?: id

            // If no body, but known extensions exist — don't discard
            if (body == null) {
                val hasKnownExtension = stanza.contains("urn:xmpp:chat-markers:0") ||
                        stanza.contains("http://jabber.org/protocol/chatstates") ||
                        stanza.contains("urn:xmpp:receipt") ||
                        stanza.contains("urn:xmpp:carbons") ||
                        stanza.contains("https://xabber.com/protocol/groups") ||
                        type == "headline" || type == "error"

                if (!hasKnownExtension) return null
            }

            if (timestamp == null && stanza.contains("<delay")) {
                val delayMatch = Regex("""<delay[^>]+stamp=['"]([^'"]+)['"]""").find(stanza)
                timestamp = delayMatch?.groupValues?.get(1)?.parseXMPPDateToMillis()
            }

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
                children = messageChildren
            )

        } catch (e: Exception) {
            Log.e("Stream", "Failed to parse message stanza: ${e.message}\nStanza: ${stanza.take(1000)}", e)
            return null
        }
    }

    /**
     * Recursively parses an element and all its children from the current parser position.
     * When called, the parser must be positioned at the START_TAG of the element.
     * On return, the parser is positioned at the END_TAG of this element.
     */
    private fun parseElementFull(parser: XmlPullParser, elementName: String, elementNs: String): XMLElement {
        val attributes = mutableMapOf<String, String>()
        for (i in 0 until parser.attributeCount) {
            attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
        }

        // Handle self-closing tags
        if (parser.isEmptyElementTag) {
            return XMLElement(
                name = elementName,
                namespace = elementNs,
                raw = "<$elementName/>",
                attributes = attributes,
                children = emptyList()
            )
        }

        val children = mutableListOf<XMLElement>()
        val textBuilder = StringBuilder()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val childName = parser.name
                    val childNs = parser.namespace ?: ""
                    // Recursively parse nested child element
                    val child = parseElementFull(parser, childName, childNs)
                    children.add(child)
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrBlank()) {
                        textBuilder.append(text.trim())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == elementName) {
                        // Build raw XML for textContent support
                        val rawContent = if (textBuilder.isNotEmpty()) {
                            "<$elementName>${textBuilder}</$elementName>"
                        } else {
                            "<$elementName/>"
                        }
                        return XMLElement(
                            name = elementName,
                            namespace = elementNs,
                            raw = rawContent,
                            attributes = attributes,
                            children = children
                        )
                    }
                }
            }
            event = parser.next()
        }

        // Fallback: reached END_DOCUMENT without closing tag
        return XMLElement(
            name = elementName,
            namespace = elementNs,
            raw = "<$elementName/>",
            attributes = attributes,
            children = children
        )
    }
    suspend fun close() = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
            socket = null
            state = StreamState.NOT_CONNECTING
        }
        socket?.close()
        messageQueue.close()
        stanzaProcessingScope.cancel()
        streamJob.cancel()
        delegate = null
        Log.d(TAG, "Stream closed for $jid")
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