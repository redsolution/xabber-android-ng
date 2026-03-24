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
import com.xabber.xmpp.core.parser.CoreStanzaParser
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicBoolean
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
    private val coreParser = CoreStanzaParser("Stream")
    var delegate: XMPPStreamDelegate? = null
    @PrimaryKey
    var host: String = extractHostFromJid(jid)
    var remoteAddress: String = ""
    var socket: Socket? = null
    private val isConnecting = AtomicBoolean(false)
    private val streamBuffer = StringBuilder()
    private val bufferMutex = Mutex()
    private val stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(4) + SupervisorJob())
    val messageQueue = Channel<MessageQueueItem>(Channel.UNLIMITED)
    private val queueMutex = Mutex()
    private val streamJob = SupervisorJob()
    private val streamScope = CoroutineScope(Dispatchers.IO + streamJob)
    var state: StreamState = StreamState.NOT_CONNECTING
        set(value) {
            field = value
            Log.d(TAG, "Transitioned to state: $value")
            streamScope.launch {
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

    fun setOnSocketReadLoopError(callback: (() -> Unit)?) {
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
        if (!isConnecting.compareAndSet(false, true)) {
            Log.w(TAG, "Connect already in progress for $jid, ignoring")
            return@withContext "Connect already in progress"
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
                streamScope.launch {
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
            isConnecting.set(false)
        }
    }

    /**
     * Lightweight data holder produced by [extractRawStanzas].
     * The [tagName] drives dispatch; [raw] is the unparsed stanza text.
     */
    private data class RawStanza(val tagName: String, val raw: String)

    /**
     * Extract complete stanza strings from [streamBuffer] + [chunk].
     *
     * **Must be called inside [bufferMutex].** Only performs fast string-scanning
     * (indexOf / substring). No XML parsing happens here, so the mutex is released
     * as quickly as possible.
     */
    private fun extractRawStanzas(chunk: String): List<RawStanza> {
        val MAX_BUFFER_SIZE = 2 * 1024 * 1024
        if (streamBuffer.length + chunk.length > MAX_BUFFER_SIZE) {
            Log.e(TAG, "Stream buffer overflow (${streamBuffer.length + chunk.length} B), clearing")
            streamBuffer.clear()
            return emptyList()
        }
        streamBuffer.append(chunk)
        var content = streamBuffer.toString()
        val result = mutableListOf<RawStanza>()
        var processedCount = 0

        while (content.isNotEmpty()) {
            val start = content.indexOf("<")
            if (start == -1) break

            // ── XML declaration / stream open ────────────────────────────────
            if (content.startsWith("<?xml", start) || content.indexOf("<stream:stream", start) == start) {
                val end = content.indexOf(">", start)
                if (end == -1) break
                result.add(RawStanza("stream:open", content.substring(start, end + 1)))
                content = content.substring(end + 1).trimStart()
                processedCount++
                continue
            }
            // ── stream:features ───────────────────────────────────────────────
            if (content.indexOf("<stream:features>", start) == start) {
                val closeTag = "</stream:features>"
                val end = content.indexOf(closeTag)
                if (end == -1) break
                val fullEnd = end + closeTag.length
                result.add(RawStanza("stream:features", content.substring(start, fullEnd)))
                content = content.substring(fullEnd).trimStart()
                processedCount++
                continue
            }
            // ── stream:error ──────────────────────────────────────────────────
            if (content.indexOf("<stream:error>", start) == start) {
                val closeTag = "</stream:error>"
                val end = content.indexOf(closeTag)
                if (end == -1) break
                val fullEnd = end + closeTag.length
                result.add(RawStanza("stream:error", content.substring(start, fullEnd)))
                content = content.substring(fullEnd).trimStart()
                processedCount++
                continue
            }
            // ── stream:stream close ───────────────────────────────────────────
            if (content.indexOf("</stream:stream>", start) == start) {
                val closeTag = "</stream:stream>"
                result.add(RawStanza("stream:close", closeTag))
                content = content.substring(start + closeTag.length).trimStart()
                processedCount++
                continue
            }

            // ── Regular stanzas (iq / message / presence / …) ─────────────────
            val candidates = listOfNotNull(
                content.indexOf("<iq",       start).takeIf { it != -1 }?.let { it to "iq" },
                content.indexOf("<presence", start).takeIf { it != -1 }?.let { it to "presence" },
                content.indexOf("<message",  start).takeIf { it != -1 }?.let { it to "message" }
            ).minByOrNull { it.first }

            val stanzaStart: Int
            val tagName: String
            if (candidates != null) {
                stanzaStart = candidates.first
                tagName = candidates.second
            } else {
                val gtPos = content.indexOf(">", start)
                if (gtPos == -1) break
                tagName = content.substring(start + 1, gtPos).split(Regex("\\s+")).first().trimEnd('/')
                stanzaStart = start
            }

            val tagEnd = content.indexOf(">", stanzaStart)
            if (tagEnd == -1) break

            val fullTag = content.substring(stanzaStart + 1, tagEnd)
            val isSelfClosing = fullTag.endsWith("/")
            val fullEnd: Int

            if (isSelfClosing) {
                fullEnd = tagEnd + 1
            } else {
                var openTags = 1
                var currentIndex = tagEnd + 1
                var complete = false
                while (openTags > 0 && currentIndex < content.length) {
                    val nextOpen  = content.indexOf("<$tagName",   currentIndex)
                    val nextClose = content.indexOf("</$tagName>", currentIndex)
                    if (nextClose == -1) break
                    if (nextOpen != -1 && nextOpen < nextClose) {
                        val gtPos = content.indexOf(">", nextOpen)
                        if (gtPos == -1) break
                        openTags++
                        currentIndex = gtPos + 1
                    } else {
                        openTags--
                        currentIndex = nextClose + "</$tagName>".length
                        if (openTags == 0) { complete = true; break }
                    }
                }
                if (!complete) break
                fullEnd = currentIndex
            }

            result.add(RawStanza(tagName, content.substring(stanzaStart, fullEnd)))
            content = content.substring(fullEnd).trimStart()
            processedCount++
        }

        streamBuffer.clear()
        streamBuffer.append(content)
        if (processedCount > 0) Log.d(TAG, "Processed $processedCount stanzas in this chunk")
        return result
    }

    /**
     * Entry point called from the socket read loop.
     *
     * 1. Acquires [bufferMutex] briefly to extract complete raw stanza strings.
     * 2. Releases the mutex.
     * 3. Parses each stanza (potentially slow) **outside** the mutex so that the
     *    next incoming chunk is not blocked during XML parsing.
     */
    private suspend fun handleIncomingStanza(chunk: String) {
        // Step 1 — fast: buffer management and boundary detection under lock
        val stanzas = bufferMutex.withLock { extractRawStanzas(chunk) }

        // Step 2 — slow: XML parsing and delegate dispatch, no lock held
        for (stanza in stanzas) {
            Log.d("XMPP STANZA READ", "RECV:${stanza.raw.take(4096)}")
            try {
                when (stanza.tagName) {
                    "stream:open" -> stanzaProcessingScope.launch {
                        delegate?.didReceiveStreamHeader(stanza.raw, this@Stream)
                    }
                    "stream:features" -> stanzaProcessingScope.launch {
                        delegate?.didReceiveStreamFeatures(stanza.raw, this@Stream)
                    }
                    "stream:error" -> stanzaProcessingScope.launch {
                        Log.e(TAG, "Received stream error: ${stanza.raw}")
                        onErrorCallback?.invoke("Stream error occurred")
                        state = StreamState.NOT_CONNECTING
                    }
                    "stream:close" -> stanzaProcessingScope.launch {
                        Log.w(TAG, "Server closed the stream")
                        onErrorCallback?.invoke("Connection closed by server")
                        state = StreamState.NOT_CONNECTING
                    }
                    "iq" -> {
                        // parseIQ now runs outside the mutex
                        val iq = parseIQ(stanza.raw)
                        if (iq != null) stanzaProcessingScope.launch {
                            try { delegate?.didReceiveIQ(iq, this@Stream) }
                            catch (e: Exception) { Log.e(TAG, "Delegate error on IQ: ${e.message}", e) }
                        }
                    }
                    "message" -> {
                        // Both parsers run outside the mutex
                        val msg = try { parseMessage(stanza.raw) }
                                  catch (e: XmlPullParserException) { null }
                                  catch (e: Exception) { null }
                            ?: extractFallbackMessage(stanza.raw)?.also {
                                Log.i(TAG, "Recovered via fallback parser: id=${it.id}")
                            }
                        if (msg != null) stanzaProcessingScope.launch {
                            try { delegate?.didReceiveMessage(msg, this@Stream) }
                            catch (e: Exception) { Log.e(TAG, "Delegate error on message: ${e.message}", e) }
                        } else {
                            Log.w(TAG, "Both parsers failed – skipping malformed message stanza")
                        }
                    }
                    "presence" -> stanzaProcessingScope.launch {
                        try { delegate?.didReceivePresence(stanza.raw, this@Stream) }
                        catch (e: Exception) { Log.e(TAG, "Delegate error on presence: ${e.message}", e) }
                    }
                    "challenge" -> stanzaProcessingScope.launch {
                        try { delegate?.didReceiveChallenge(stanza.raw, this@Stream) }
                        catch (e: Exception) { Log.e(TAG, "Delegate error on challenge: ${e.message}", e) }
                    }
                    "success" -> stanzaProcessingScope.launch {
                        try { delegate?.didReceiveSuccess(stanza.raw, this@Stream) }
                        catch (e: Exception) { Log.e(TAG, "Delegate error on success: ${e.message}", e) }
                    }
                    "proceed" -> stanzaProcessingScope.launch {
                        try { delegate?.didReceiveProceed(stanza.raw, this@Stream) }
                        catch (e: Exception) { Log.e(TAG, "Delegate error on proceed: ${e.message}", e) }
                    }
                    "failure" -> stanzaProcessingScope.launch {
                        try { delegate?.didReceiveFailure(stanza.raw, this@Stream) }
                        catch (e: Exception) { Log.e(TAG, "Delegate error on failure: ${e.message}", e) }
                    }
                    else -> Log.w(TAG, "Unhandled stanza type: ${stanza.tagName}, preview: ${stanza.raw.take(200)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error dispatching stanza (${stanza.tagName}): ${e.message}")
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
        val realm = Realm.open(defaultRealmConfig())
        try {
            // Load already-processed IDs once at startup
            realm.write {
                val stored = query<ProcessedMessageId>("owner = $0", jid).find()
                processedIds.addAll(stored.map { it.messageId })
                Log.d(TAG, "Loaded ${stored.size} processed message IDs for owner=$jid")
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
                        Log.w(TAG, "Skipping invalid queue item: id=${item.message.id}")
                        return@withLock
                    }

                    val messageId = item.message.id!!
                    if (messageId in processedIds) {
                        // Double-check in Realm in case of a restart
                        val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                        val existingMessage = realm.query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
                        if (existingMessage != null) {
                            Log.d(TAG, "Confirmed messageId=$messageId already stored, skipping")
                            return@withLock
                        }
                        Log.w(TAG, "messageId=$messageId marked processed but absent in DB, reprocessing")
                    }

                    val from = item.message.from!!.bare()!!
                    val to   = item.message.to!!.bare()!!
                    var isOutgoing = item.isArchived ?: (from == jid)
                    val opponent = if (isOutgoing) to else from

                    if (item.message.body.isNullOrEmpty()) {
                        Log.d(TAG, "Skipping bodyless message: id=$messageId")
                        return@withLock
                    }

                    Log.d(TAG, "Processing queued message: id=$messageId, from=$from, to=$to, body=${item.message.body.take(50)}")

                    try {
                        realm.write {
                            val msgPrimary = MessageStorageItem.genPrimary(messageId, jid)
                            val existing = query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
                            if (existing != null) {
                                Log.d(TAG, "Skipping duplicate message in DB: id=$messageId")
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
                        Log.e(TAG, "Error storing message id=$messageId: ${e.message}", e)
                    }
                }
            }
        } finally {
            realm.close()
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
        streamScope.launch {
            val realm = Realm.open(defaultRealmConfig())
            realm.write {
                val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", jid).find()
                delete(selfChats)
                Log.d(TAG, "Deleted ${selfChats.size} self-chats for owner=$jid")
            }
            realm.close()
        }
    }

    fun parseIQ(stanza: String): XMPPIQ? {
        return coreParser.parseIQ(stanza)
    }

    fun parseMessage(stanza: String): XMPPMessage? {
        return coreParser.parseMessage(stanza)
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
        isConnecting.set(false)
        val socketToClose = socket
        socket = null
        state = StreamState.NOT_CONNECTING
        socketToClose?.close()
        messageQueue.close()
        stanzaProcessingScope.cancel()
        streamJob.cancelAndJoin()
        delegate = null
        Log.d(TAG, "Stream closed for $jid")
    }

    fun logout(jid: String) {
        if (this.jid == jid) {
            streamScope.launch { close() }
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
