package com.xabber.xmpp.messages.message_archive

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.utils.parseTimestamp
import com.xabber.utils.prp
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.XMPPMessage
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.time.DateTimeException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

@RequiresApi(Build.VERSION_CODES.O)
class MessageArchiveManager(private val owner: String) {

    private val namespace = "urn:xmpp:mam:2"
    private val pageSize = 50 // Match ChatViewModel pageSize
    private val callbacksQueue = mutableMapOf<String, CallbackQueueItem>()
    private val nanoIdAlphabet = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val nanoIdMask = 63
    private val nanoIdStep = 16
    private val TAG = "MessageArchiveManager"

    data class MAMRequestItem(
        val jid: String?,
        val taskId: String,
        val isGroupchat: Boolean,
        val conversationType: ConversationType,
        val isContinues: Boolean,
        val queryId: String,
        val max: Int,
        val beforeId: String? = null,
        val afterId: String? = null,
        val start: Date? = null,
        val end: Date? = null
    )

    data class CallbackQueueItem(
        val jid: String,
        val queryId: String,
        val task: MAMRequestItem,
        val callback: (() -> Unit)?
    )

    interface TemporaryMessageReceiver {
        suspend fun didReceiveMessage(item: MessageStorageItem, queryId: String)
        fun didReceiveEndPage(queryId: String, complete: Boolean, first: String, last: String, count: Int)
    }

    var temporaryMessageReceiver: TemporaryMessageReceiver? = null
    private val queryIds = mutableSetOf<String>()
    private var continuesTaskID: String? = null

    suspend fun requestArchive(
        stream: Stream,
        jid: String?,
        isContinues: Boolean,
        conversationType: ConversationType,
        queryId: String? = null,
        searchText: String? = null,
        flipPage: Boolean = false,
        before: String? = null,
        beforeId: String? = null,
        afterId: String? = null,
        start: Date? = null,
        end: Date? = null,
        nextPage: String? = null,
        prevPage: String? = null,
        max: Int? = null,
        withCounter: Boolean = true,
        isNormalSynchronousTask: Boolean = false,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val isGroupchat = listOf(ConversationType.Group, ConversationType.Channel).contains(conversationType)
        val generatedQueryId = queryId ?: "MAM:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        val taskId = listOf(jid ?: "global", conversationType.rawValue).prp()

        val queryXml = buildString {
            append("<query xmlns='$namespace' queryid='$generatedQueryId'>")
            append(buildX(jid, isGroupchat, conversationType, beforeId, afterId, start, end))
            append(max?.let { buildSet(it, beforeId, afterId) })
            append("</query>")
        }

        val toAttr = if (isGroupchat && jid != null) " to='$jid'" else ""
        val iqXml = "<iq type='set' id='$generatedQueryId'$toAttr>$queryXml</iq>"

        val success = stream.socket?.write(iqXml) == true
        if (success) {
            callbacksQueue[generatedQueryId] = CallbackQueueItem(
                jid = jid ?: "",
                queryId = generatedQueryId,
                task = MAMRequestItem(
                    jid = jid,
                    taskId = taskId,
                    isGroupchat = isGroupchat,
                    conversationType = conversationType,
                    isContinues = isContinues,
                    queryId = generatedQueryId,
                    max = max!!,
                    beforeId = beforeId,
                    afterId = afterId,
                    start = start,
                    end = end
                ),
                callback = callback
            )
            queryIds.add(generatedQueryId)
            Log.d(TAG, "Sent MAM query: id=$generatedQueryId, jid=$jid, conversationType=${conversationType.rawValue}, isContinues=$isContinues")
        } else {
            Log.e(TAG, "Failed to send MAM query: $iqXml")
        }
    }

    suspend fun getPrevHistory(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        messageId: String,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = false,
            conversationType = conversationType,
            queryId = "MAM prev:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
            beforeId = messageId,
            max = pageSize,
            callback = callback
        )
    }

    suspend fun checkShouldLoadFullHistory(jid: String, conversationType: ConversationType): Boolean = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val instance = realm.query<LastChatsStorageItem>(
                "primary = $0",
                LastChatsStorageItem.genPrimary(jid, owner, conversationType)
            ).first().find()
            if (instance != null) {
                if (instance.fullArchiveLoaded) return@withContext false
                if (continuesTaskID == null) return@withContext true
                val taskId = listOf(jid, conversationType.rawValue).prp()
                if (continuesTaskID == taskId) return@withContext false
                if (!instance.fullArchiveLoaded) return@withContext true
            }
            false
        } finally {
            realm.close()
        }
    }

    suspend fun getNextHistory(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        messageId: String?,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = false,
            conversationType = conversationType,
            queryId = "MAM next:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
            afterId = messageId,
            max = pageSize,
            callback = callback
        )
    }

    suspend fun startLoadHistory(
        stream: Stream,
        jid: String,
        conversationType: ConversationType
    ) = withContext(Dispatchers.IO) {
        val taskId = listOf(jid, conversationType.rawValue).prp()
        if (continuesTaskID != null && continuesTaskID != taskId) {
            callbacksQueue.values.find { it.task.taskId == continuesTaskID }?.let { item ->
                item.callback?.invoke()
                callbacksQueue.remove(item.queryId)
            }
        }

        val realm = Realm.open(defaultRealmConfig())
        try {
            val messageId = realm.query<MessageStorageItem>(
                "opponent = $0 AND owner = $1 AND conversationType_ = $2",
                jid, owner, conversationType.rawValue
            ).find().sortedByDescending { it.date }.lastOrNull()?.archivedId

            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = true,
                conversationType = conversationType,
                queryId = "MAM init:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                beforeId = messageId,
                max = pageSize,
                callback = {
                    realm.writeBlocking {
                        val chat = query<LastChatsStorageItem>(
                            "primary = $0",
                            LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                        ).first().find()
                        if (chat != null) {
                            findLatest(chat)?.apply {
                                isSynced = true
                                isInitialArchiveLoaded = true
                            }
                            Log.d(TAG, "Updated LastChatsStorageItem for jid=$jid: isSynced=true, isInitialArchiveLoaded=true")
                        }
                    }
                }
            )
            continuesTaskID = taskId
        } finally {
            realm.close()
        }
    }

    suspend fun syncChat(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        callback: (() -> Unit)?
    ) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val chat = realm.query<LastChatsStorageItem>(
                "primary = $0",
                LastChatsStorageItem.genPrimary(jid, owner, conversationType)
            ).first().find()
            var isSynced = chat?.isSynced ?: false
            val messageId = realm.query<MessageStorageItem>(
                "opponent = $0 AND owner = $1 AND conversationType_ = $2",
                jid, owner, conversationType.rawValue
            ).find().sortedByDescending { it.date }.lastOrNull()?.archivedId

            if (!isSynced) {
                requestArchive(
                    stream = stream,
                    jid = jid,
                    isContinues = true,
                    conversationType = conversationType,
                    queryId = "MAM sync:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                    beforeId = messageId,
                    max = pageSize,
                    callback = {
                        realm.writeBlocking {
                            val instance = query<LastChatsStorageItem>(
                                "primary = $0",
                                LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                            ).first().find()
                            if (instance != null) {
                                findLatest(instance)?.apply {
                                    isSynced = true
                                    isInitialArchiveLoaded = true
                                }
                                Log.d(TAG, "Updated LastChatsStorageItem for jid=$jid: isSynced=true, isInitialArchiveLoaded=true")
                            }
                        }
                        callback?.invoke()
                    }
                )
            } else {
                callback?.invoke()
            }
        } finally {
            realm.close()
        }
    }

    suspend fun read(iq: String, stream: Stream): Boolean = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(iq.byteInputStream())
            val iqElement = document.documentElement
            if (iqElement.getAttribute("type") == "error") {
                val errorElement = iqElement.getElementsByTagName("error").item(0) as? Element
                val errorText = errorElement?.getElementsByTagName("text")?.item(0)?.textContent
                Log.e(TAG, "Received MAM error: $errorText")
                return@withContext false
            }
            if (iqElement.getAttribute("type") != "result") {
                Log.w(TAG, "Ignoring non-result IQ: $iq")
                return@withContext false
            }
            val finElement = iqElement.getElementsByTagNameNS(namespace, "fin").item(0) as? Element
            val queryId = finElement?.getAttribute("queryid") ?: return@withContext false
            val complete = finElement.getAttribute("complete")?.toBooleanStrictOrNull() ?: false
            val setElement = finElement.getElementsByTagNameNS("http://jabber.org/protocol/rsm", "set")?.item(0) as? Element
            val first = setElement?.getElementsByTagName("first")?.item(0)?.textContent ?: ""
            val last = setElement?.getElementsByTagName("last")?.item(0)?.textContent ?: ""
            val count = setElement?.getElementsByTagName("count")?.item(0)?.textContent?.toIntOrNull() ?: 0

            val callbackItem = callbacksQueue[queryId]
            if (callbackItem == null) {
                Log.w(TAG, "No callback found for queryId: $queryId")
                return@withContext false
            }

            realm.writeBlocking {
                val chat = query<LastChatsStorageItem>(
                    "primary = $0",
                    LastChatsStorageItem.genPrimary(callbackItem.jid, owner, callbackItem.task.conversationType)
                ).first().find()
                if (chat != null) {
                    findLatest(chat)?.apply {
                        fullArchiveLoaded = complete
                        lastLoadedMessageHistoryId = last
                    }
                    Log.d(TAG, "Updated LastChatsStorageItem for jid=${callbackItem.jid}: fullArchiveLoaded=$complete, lastLoadedMessageHistoryId=$last")
                }
            }

            temporaryMessageReceiver?.didReceiveEndPage(queryId, complete, first, last, count)

            if (callbackItem.task.isContinues && count > 0 && !complete) {
                val nextId = if (callbackItem.task.afterId != null) last else first
                continueLoadHistory(stream, callbackItem.task, nextId)
                Log.d(TAG, "Continuing MAM pagination for queryId=$queryId, nextId=$nextId, count=$count")
            } else {
                callbackItem.callback?.invoke()
                callbacksQueue.remove(queryId)
                queryIds.remove(queryId)
                Log.d(TAG, "Completed MAM query for queryId=$queryId, complete=$complete, count=$count")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse IQ: ${e.message}", e)
            false
        } finally {
            realm.close()
        }
    }

    private suspend fun continueLoadHistory(stream: Stream, task: MAMRequestItem, nextId: String?) {
        if (nextId == null || task.taskId != continuesTaskID) {
            callbacksQueue[task.queryId]?.let { item ->
                item.callback?.invoke()
                callbacksQueue.remove(task.queryId)
                queryIds.remove(task.queryId)
            }
            return
        }

        requestArchive(
            stream = stream,
            jid = task.jid,
            isContinues = true,
            conversationType = task.conversationType,
            queryId = "MAM cont:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
            beforeId = if (task.afterId == null) nextId else null,
            afterId = if (task.afterId != null) nextId else null,
            max = task.max,
            start = task.start,
            end = task.end,
            callback = callbacksQueue[task.queryId]?.callback
        )
        Log.d(TAG, "Requested next MAM page for queryId=${task.queryId}, jid=${task.jid}, nextId=$nextId")
    }

    private fun buildX(
        jid: String?,
        isGroupchat: Boolean,
        conversationType: ConversationType,
        beforeId: String?,
        afterId: String?,
        start: Date?,
        end: Date?
    ): String = buildString {
        append("<x xmlns='jabber:x:data' type='submit'>")
        append("<field var='FORM_TYPE' type='hidden'><value>$namespace</value></field>")
        if (!beforeId.isNullOrEmpty()) append("<field var='before-id'><value>$beforeId</value></field>")
        if (!afterId.isNullOrEmpty()) append("<field var='after-id'><value>$afterId</value></field>")
        if (start != null) append("<field var='start'><value>${formatDate(start)}</value></field>")
        if (end != null) append("<field var='end'><value>${formatDate(end)}</value></field>")
        if (!isGroupchat && jid != null) append("<field var='with'><value>$jid</value></field>")
        if (!isGroupchat) append("<field var='conversation-type'><value>${conversationType.rawValue}</value></field>")
        append("</x>")
    }

    private fun buildSet(max: Int, beforeId: String?, afterId: String?): String = buildString {
        append("<set xmlns='http://jabber.org/protocol/rsm'>")
        append("<max>$max</max>")
        if (!beforeId.isNullOrEmpty()) append("<before>$beforeId</before>")
        else if (!afterId.isNullOrEmpty()) append("<after>$afterId</after>")
        else append("<before/>")
        append("</set>")
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(date)
    }

    private fun getDelayedDate(message: XMPPMessage): Date? {
        val timestamp = parseTimestamp(message, TAG)
        return timestamp?.let { Date(it) }
    }

    private fun isSystemMessage(message: XMPPMessage): Boolean {
        return message.hasElement("system", namespace = "urn:xmpp:system")
    }

    private fun isChatStateOrMarker(message: XMPPMessage): Boolean {
        return message.element("active", namespace = "http://jabber.org/protocol/chatstates") != null ||
                message.element("composing", namespace = "http://jabber.org/protocol/chatstates") != null ||
                message.element("inactive", namespace = "http://jabber.org/protocol/chatstates") != null ||
                message.element("received", namespace = "urn:xmpp:chat-markers:0") != null ||
                message.element("displayed", namespace = "urn:xmpp:chat-markers:0") != null
    }

    private fun parseXMPPMessage(element: Element): XMPPMessage? {
        try {
            val id = element.getAttribute("id")
            val from = element.getAttribute("from")?.let { XMPPJID(it) }
            val to = element.getAttribute("to")?.let { XMPPJID(it) }
            val type = element.getAttribute("type")
            val lang = element.getAttribute("xml:lang")
            val body = element.getElementsByTagName("body").item(0)?.textContent
            val subject = element.getElementsByTagName("subject").item(0)?.textContent
            val thread = element.getElementsByTagName("thread").item(0)?.textContent
            val error = element.getElementsByTagName("error").item(0)?.textContent
            val children = mutableListOf<XMLElement>()
            val nodeList = element.childNodes
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node is Element) {
                    children.add(
                        XMLElement(
                            name = node.localName,
                            namespace = node.namespaceURI,
                            raw = node.toString(),
                            attributes = node.attributes.let { attrs ->
                                (0 until attrs.length).associate { idx ->
                                    val attr = attrs.item(idx)
                                    attr.nodeName to attr.nodeValue
                                }
                            }
                        )
                    )
                }
            }
            return XMPPMessage(
                raw = element.toString(),
                type = type,
                id = id,
                from = from,
                to = to,
                lang = lang,
                body = body,
                subject = subject,
                thread = thread,
                error = error,
                children = children
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XMPP message: ${e.message}", e)
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun processMAMMessage(messageXml: String) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            // Parse XML
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(messageXml.byteInputStream())
            val messageElement = document.documentElement
            if (messageElement.tagName != "message") {
                Log.w(TAG, "Ignoring non-message stanza: $messageXml")
                return@withContext
            }
            val resultElement = messageElement.getElementsByTagNameNS(namespace, "result").item(0) as? Element
            val queryId = resultElement?.getAttribute("queryid") ?: run {
                Log.w(TAG, "No queryid in MAM result: $messageXml")
                return@withContext
            }
            if (!queryIds.contains(queryId)) {
                Log.w(TAG, "Unknown queryId: $queryId")
                return@withContext
            }
            val archivedId = resultElement.getAttribute("id")
            val forwarded = resultElement.getElementsByTagNameNS("urn:xmpp:forward:0", "forwarded").item(0) as? Element
            val delay = forwarded?.getElementsByTagNameNS("urn:xmpp:delay", "delay")?.item(0) as? Element
            // Use a local function to parse the timestamp string directly
            fun parseDelayTimestamp(stamp: String): Long? {
                try {
                    val formatter = DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .optionalStart()
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                        .optionalEnd()
                        .appendOffsetId()
                        .toFormatter()
                    val zdt = ZonedDateTime.parse(stamp, formatter)
                    return zdt.toInstant().toEpochMilli().also {
                        Log.d(TAG, "Parsed timestamp for archivedId=$archivedId: $stamp -> $it")
                    }
                } catch (e: DateTimeException) {
                    Log.w(TAG, "Failed to parse timestamp for archivedId=$archivedId: $stamp, error=${e.message}")
                    return null
                }
            }
            val stampedTime = delay?.getAttribute("stamp")?.let { parseDelayTimestamp(it) } ?: System.currentTimeMillis()
            val innerMessage = forwarded?.getElementsByTagName("message")?.item(0) as? Element ?: run {
                Log.w(TAG, "No forwarded message in MAM result: $messageXml")
                return@withContext
            }
            val innerMsg = parseXMPPMessage(innerMessage) ?: run {
                Log.w(TAG, "Failed to parse inner message: $messageXml")
                return@withContext
            }
            if (isChatStateOrMarker(innerMsg) || isSystemMessage(innerMsg)) {
                Log.d(TAG, "Skipping chat state or system message: id=${innerMsg.id}")
                return@withContext
            }

            // Extract message details
            val fromJid = innerMsg.from?.bare()?.toString() ?: run {
                Log.w(TAG, "No 'from' JID in message: $messageXml")
                return@withContext
            }
            val toJid = innerMsg.to?.bare()?.toString() ?: run {
                Log.w(TAG, "No 'to' JID in message: $messageXml")
                return@withContext
            }
            val isOutgoing = fromJid == owner
            val opponent = if (isOutgoing) toJid else fromJid
            val convTypeStr = innerMsg.children.firstOrNull { it.name == "conversation-type" }?.attributes?.get("value") ?: "urn:xabber:chat"
            val convType = ConversationType.values().firstOrNull { it.rawValue == convTypeStr } ?: ConversationType.Regular
            val body = innerMsg.body ?: ""
            val references = realmListOf<MessageReferenceStorageItem>()
            val primary = "${owner}_${opponent}_${stampedTime}_${archivedId}"

            // Perform the blocking write and capture the inserted message
            val insertedMessage = realm.writeBlocking {
                if (query<MessageStorageItem>("archivedId = $0", archivedId).first().find() != null) {
                    Log.d(TAG, "Skipping duplicate message: archivedId=$archivedId")
                    return@writeBlocking null  // Return null if duplicate
                }
                val message = copyToRealm(MessageStorageItem().apply {
                    this.primary = primary
                    this.owner = owner
                    this.opponent = opponent
                    this.body = body
                    this.date = stampedTime
                    this.sentDate = stampedTime
                    this.editDate = 0
                    this.outgoing = isOutgoing
                    this.isRead = isOutgoing
                    this.references = references
                    this.archivedId = archivedId
                    this.conversationType_ = convType.rawValue
                })
                val chatPrimary = LastChatsStorageItem.genPrimary(opponent, owner, convType)
                val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find()
                if (chat != null) {
                    findLatest(chat)?.apply {
                        lastMessage = message
                        messageDate = stampedTime
                        if (!isOutgoing && muteExpired <= 0) {
                            isArchived = false
                            unread = (unread ?: 0) + 1
                        }
                    }
                }
                message  // Return the inserted message
            }

            // Notify receiver outside the blocking write, if message was inserted
            if (insertedMessage != null) {
                withContext(Dispatchers.Main) {
                    temporaryMessageReceiver?.didReceiveMessage(insertedMessage, queryId)
                }
                Log.d(TAG, "Processed MAM message: archivedId=$archivedId, primary=$primary, opponent=$opponent, body=${body.take(50)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MAM message: ${e.message}", e)
//            temporaryMessageReceiver?.onError(queryId, "Error processing message: ${e.message}")
        } finally {
            realm.close()
        }
    }

    fun reset() {
        callbacksQueue.values.forEach { it.callback?.invoke() }
        callbacksQueue.clear()
        queryIds.clear()
        continuesTaskID = null
        Log.d(TAG, "Reset MessageArchiveManager for owner $owner")
    }
}