package com.xabber.xmpp.messages.message_archive

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.stream.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageForwardsInlineStorageItem
import com.xabber.data_base.models.messages.MessageForwardsInlineStorageItemKind
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.utils.parseTimestamp
import com.xabber.utils.prp
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.XMLElement
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

class MessageArchiveManager(private val owner: String) {
    private val namespace = "urn:xmpp:mam:2"
    private val pageSize = 70
    private val paginationSize = 70 // Increased for older messages loading
    private val callbacksQueue = mutableSetOf<CallbackQueueItem>()
    private val searchResultsQueries = mutableSetOf<String>()
    private val interactiveQueue = mutableListOf<String>()
    private var continuesTaskID: String? = null
    private val nanoIdAlphabet = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val nanoIdMask = 63
    private val nanoIdStep = 16
    private val TAG = "MessageArchiveManager"
    private val queryToReceivedCount = mutableMapOf<String, Int>()
    val queryIds = mutableMapOf<String, CallbackQueueItem>()
    private val queryIdsMutex = Mutex()




    data class MAMRequestItem(
        val jid: String?,
        val taskId: String,
        val isGroupchat: Boolean,
        val messageId: String?,
        val conversationType: ConversationType,
        val backward: Boolean,
        val isContinues: Boolean,
        val maxDate: Date?,
        val searchText: String?,
        val queryId: String?,
        val rsmBefore: String?,
        val rsmAfter: String?,
        val max: Int,
        val start: Date?,
        val end: Date?,
        val isNormalSynchronousTask: Boolean
    )

    data class CallbackQueueItem(
        val jid: String,
        val elementId: String,
        val task: MAMRequestItem,
        val callback: (() -> Unit)?
    )
    data class GapInfo(val queryId: String, val start: Date, val end: Date)

    data class HistoryGap(
        val newestMessageId: String,
        val oldestMessageId: String,
        var startDate: Date,
        var endDate: Date
    ) {
        init {
            startDate = Date(startDate.time + 600_000) // +10 minutes
            endDate = Date(endDate.time - 600_000) // -10 minutes
        }
    }

    interface TemporaryMessageReceiver {
        suspend fun didReceiveMessage(item: MessageStorageItem, queryId: String)
        fun didReceiveEndPage(queryId: String, fin: Boolean, first: String, last: String, count: Int)
        fun didStartPageLoad(queryId: String)
    }
    var temporaryMessageReceiver: TemporaryMessageReceiver? = null

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun requestArchive(
        stream: Stream,
        jid: String? = null,
        isContinues: Boolean = false,
        conversationType: ConversationType = ConversationType.Regular,
        queryId: String? = null,
        searchText: String? = null,
        flipPage: Boolean = false,
        start: Date? = null,
        end: Date? = null,
        beforeId: String? = null,
        afterId: String? = null,
        rsmBefore: String? = "",
        rsmAfter: String? = null,
        max: Int? = null,
        withCounter: Boolean = true,
        isNormalSynchronousTask: Boolean = false,
        backward: Boolean = true,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val isGroupchat = listOf(ConversationType.Group, ConversationType.Channel).contains(conversationType)
        val elementId = queryId ?: "MAM:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        val taskId = listOf(jid ?: "global_search", conversationType.rawValue).joinToString("_")

        queryIdsMutex.withLock {
            if (queryIds.containsKey(elementId)) {
                Log.w(TAG, "Query ID $elementId already exists, skipping request")
                callback?.invoke()
                return@withContext
            }
            val callbackItem = CallbackQueueItem(
                jid = jid ?: "",
                elementId = elementId,
                task = MAMRequestItem(
                    jid = jid,
                    taskId = taskId,
                    isGroupchat = isGroupchat,
                    messageId = null,
                    backward = backward,
                    conversationType = conversationType,
                    isContinues = isContinues,
                    maxDate = start,
                    searchText = searchText,
                    queryId = elementId,
                    rsmBefore = rsmBefore,
                    rsmAfter = rsmAfter,
                    max = max ?: pageSize,
                    start = start,
                    end = end,
                    isNormalSynchronousTask = isNormalSynchronousTask
                ),
                callback = callback
            )
            queryIds[elementId] = callbackItem
            callbacksQueue.add(callbackItem)
            interactiveQueue.add(elementId)
        }

        val queryXml = buildString {
            append("<query xmlns='$namespace' queryid='$elementId'>")
            append(buildX(searchText, start, end, withCounter, jid, conversationType, isGroupchat, beforeId, afterId))
            append(buildSet(max ?: pageSize, rsmBefore, rsmAfter))
            if (flipPage) append("<flip-page/>")
            append("</query>")
        }

        val toAttr = if (isGroupchat && jid != null) " to='$jid'" else ""
        val iqXml = "<iq type='set' id='$elementId'$toAttr>$queryXml</iq>"

        try {
            val success = stream.socket?.write(iqXml) == true
            if (success) {
                temporaryMessageReceiver?.didStartPageLoad(elementId)
                Log.v(TAG, "Sent MAM query stanza: $iqXml")
            } else {
                Log.e(TAG, "Failed to send MAM query stanza: $iqXml")
                queryIdsMutex.withLock {
                    queryIds.remove(elementId)
                    callbacksQueue.removeAll { it.elementId == elementId }
                    interactiveQueue.remove(elementId)
                }
                temporaryMessageReceiver?.didReceiveEndPage(elementId, false, "", "", 0)
                callback?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in requestArchive: queryId=$elementId, jid=$jid, error=${e.message}", e)
            queryIdsMutex.withLock {
                queryIds.remove(elementId)
                callbacksQueue.removeAll { it.elementId == elementId }
                interactiveQueue.remove(elementId)
            }
            temporaryMessageReceiver?.didReceiveEndPage(elementId, false, "", "", 0)
            callback?.invoke()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun syncChat(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            var isInitialArchiveLoaded = false
            var isSynced = false
            var archiveStart: Date? = null
            var chat: LastChatsStorageItem? = null
            var lastMessageId: String? = null

            // Step 1: Check for existing chat and initialize
            realm.write {
                val existingChats = query<LastChatsStorageItem>(
                    "jid = $0 AND owner = $1", jid, owner
                ).find()
                chat = existingChats.find { it.conversationType_ == conversationType.rawValue }
                if (chat == null && existingChats.isNotEmpty() && conversationType == ConversationType.Group) {
                    chat = existingChats.firstOrNull()
                    if (chat != null) {
                        findLatest(chat!!)?.apply {
                            conversationType_ = ConversationType.Group.rawValue
                        }
                    }
                }

                if (chat != null) {
                    isInitialArchiveLoaded = chat!!.isInitialArchiveLoaded
                    isSynced = chat!!.isSynced
                } else {
                    val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                    if (chatPrimary.isEmpty()) {
                        Log.w(TAG, "Skipping chat creation due to invalid primary key for jid=$jid, owner=$owner, type=${conversationType.rawValue}")
                        callback?.invoke()
                        return@write
                    }
                    val existingChat = query<LastChatsStorageItem>(
                        "primary = $0",
                        chatPrimary
                    ).first().find()
                    if (existingChat == null) {
                        val instance = LastChatsStorageItem().apply {
                            owner = this@MessageArchiveManager.owner
                            this.jid = jid
                            conversationType_ = conversationType.rawValue
                            messageDate = System.currentTimeMillis()
                            primary = chatPrimary
                            isSynced = false
                            isInitialArchiveLoaded = false
                            val roster = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find()
                            if (roster == null) {
                                val newRoster = RosterStorageItem().apply {
                                    this.jid = jid
                                    this.owner = this@MessageArchiveManager.owner
                                    primary = RosterStorageItem.genPrimary(jid, owner)
                                    groups = realmListOf("ungrouped")
                                }
                                copyToRealm(newRoster)
                                rosterItem = newRoster
                            } else {
                                rosterItem = roster
                            }
                        }
                        copyToRealm(instance)
                        chat = instance
                    } else {
                        chat = existingChat
                        isInitialArchiveLoaded = existingChat.isInitialArchiveLoaded
                        isSynced = existingChat.isSynced
                    }
                }

                // Set archive start for encrypted conversations
                if (listOf(ConversationType.Omemo, ConversationType.Omemo1, ConversationType.Axolotl).contains(conversationType)) {
                    archiveStart = query<AccountStorageItem>("jid = $0", owner).first().find()?.createdAt?.let { Date(it) }
                }
            }

            // Step 2: Early exit if already synced
            if (isInitialArchiveLoaded && isSynced) {
                Log.d(TAG, "Chat already synced for jid=$jid, owner=$owner, type=${conversationType.rawValue}")
                callback?.invoke()
                return@withContext
            }

            // Step 4: Request archive starting from the last message's ID
            val queryId = "MAM:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = true,
                conversationType = conversationType,
                queryId = queryId,
                flipPage = true,
                start = archiveStart,
                rsmBefore =  "",
                max = pageSize,
                withCounter = true,
                isNormalSynchronousTask = false,
                backward = true,
                callback = {
                    CoroutineScope(Dispatchers.IO).launch {
                        realm.write {
                            val chat = query<LastChatsStorageItem>(
                                "primary = $0",
                                LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                            ).first().find()
                            chat?.let {
                                findLatest(it)?.apply {
                                    isSynced = true
                                    isInitialArchiveLoaded = true
                                    val latestMessage = query<MessageStorageItem>(
                                        "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                                        owner, jid, conversationType.rawValue
                                    ).find().maxByOrNull { it.sentDate }
                                    if (latestMessage != null) {
                                        lastMessage = latestMessage
                                        messageDate = latestMessage.sentDate
                                        lastMessageId = latestMessage.archivedId
                                        if (!latestMessage.outgoing && muteExpired <= 0 && !latestMessage.isRead) {
                                            isArchived = false
                                            unread = (unread ?: 0) + 1
                                        }
                                    }
                                }
                            }
                        }
                        callback?.invoke()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncChat: ${e.message}", e)
            callback?.invoke()
            temporaryMessageReceiver?.didReceiveEndPage("", false, "", "", 0)
        } finally {
            realm.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getHistoryByDate(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        start: Date? = null,
        end: Date? = null,
        reversed: Boolean = false,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val queryId = "MAM:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        try {
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = true,
                conversationType = conversationType,
                queryId = queryId,
                searchText = null,
                flipPage = true,
                start = start,
                end = end,
                rsmBefore =  "",
                max = pageSize, // Use pageSize instead of 100
                isNormalSynchronousTask = true,
                callback = {
                    callback?.invoke()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in getHistoryByDate: queryId=$queryId, jid=$jid, error=${e.message}", e)
            temporaryMessageReceiver?.didReceiveEndPage(queryId, false, "", "", 0)
            callback?.invoke()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun searchText(
        stream: Stream,
        jid: String? = null,
        conversationType: ConversationType,
        text: String,
        max: Int = pageSize, // Use pageSize
        loadFull: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val taskId = listOf(jid ?: "global_search", conversationType.rawValue).prp()
        continuesTaskID?.let { currentTaskId ->
            if (taskId != currentTaskId) {
                callbacksQueue.find { it.task.taskId == currentTaskId }?.let { item ->
                    item.callback?.invoke()
                    callbacksQueue.remove(item)
                }
            }
        }
        val queryId = "MAM search:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = loadFull,
            conversationType = conversationType,
            queryId = queryId,
            searchText = text,
            flipPage = false,
            rsmBefore = "",
            max = max
        )
        continuesTaskID = taskId
        queryId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getLastMessage(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        callback: ((String?) -> Unit)? = null // Callback to return the archivedId
    ) = withContext(Dispatchers.IO) {
        val queryId = "MAM:last:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = false,
            conversationType = conversationType,
            queryId = queryId,
            rsmBefore = "",
            max = 1,
            callback = {
                // Query the database for the most recent message after the MAM response
                val realm = Realm.open(defaultRealmConfig())
                try {
                    val latestMessage = realm.query<MessageStorageItem>(
                        "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                        owner, jid, conversationType.rawValue
                    ).find().maxByOrNull { it.date }
                    callback?.invoke(latestMessage?.archivedId)
                } finally {
                    realm.close()
                }
            }
        )
    }

    suspend fun getPrevHistory(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        messageId: String,
        callback: (() -> Unit)?
    ) = withContext(Dispatchers.IO) {
        val queryId = "MAM:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"

        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = false,
            conversationType = conversationType,
            queryId = queryId,
            flipPage = true,
            rsmBefore = if (messageId.isEmpty()) "" else messageId,
            max = paginationSize,
            backward = true,
            callback = callback
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getNextHistory(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        messageId: String?,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val messageDate = realm.query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND archivedId = $2",
                owner, jid, messageId ?: ""
            ).first().find()?.date?.let { Date(it) } ?: Date()

            val modifiedDate = Date(messageDate.time + 20 * 60 * 1000) // +20 minutes
            val queryId = "MAM next history:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = false,
                conversationType = conversationType,
                queryId = queryId,
                flipPage = true,
                end = modifiedDate,
                rsmBefore = "",
                max = pageSize, // Use pageSize
                isNormalSynchronousTask = true,
                callback = callback
            )
        } finally {
            realm.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun startLoadHistory(
        stream: Stream,
        jid: String,
        conversationType: ConversationType
    ) = withContext(Dispatchers.IO) {
        val taskId = listOf(jid, conversationType.rawValue).prp()
        continuesTaskID?.let { currentTaskId ->
            if (taskId != currentTaskId) {
                callbacksQueue.find { it.task.taskId == currentTaskId }?.let { item ->
                    item.callback?.invoke()
                    callbacksQueue.remove(item)
                }
            }
        }

        val realm = Realm.open(defaultRealmConfig())
        try {
            val messageId = realm.query<MessageStorageItem>(
                "opponent = $0 AND owner = $1 AND conversationType_ = $2",
                jid, owner, conversationType.rawValue
            ).find().sortedByDescending { it.date }.lastOrNull()?.archivedId

            var archiveStart: Date? = null
            if (listOf(ConversationType.Omemo, ConversationType.Omemo1, ConversationType.Axolotl).contains(conversationType)) {
                archiveStart = realm.query<AccountStorageItem>("jid = $0", owner).first().find()?.createdAt?.let { Date(it) }
            }

//            val queryId = "MAM:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = true,
                conversationType = conversationType,
                rsmBefore = messageId,
                start = archiveStart,
                max = pageSize,
                callback = {
                    CoroutineScope(Dispatchers.IO).launch {
                        getLastMessage(stream, jid, conversationType)
                        realm.write {
                            val instance = query<LastChatsStorageItem>(
                                "primary = $0",
                                LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                            ).first().find()
                            if (instance != null) {
                                findLatest(instance)?.apply {
                                    isInitialArchiveLoaded = true
                                    isSynced = true
                                }
                            }
                        }
                    }
                }
            )
            continuesTaskID = taskId
        } finally {
            realm.close()
        }
    }

    fun endLoadHistory(jid: String, conversationType: ConversationType) {
        val taskId = listOf(jid, conversationType.rawValue).prp()
        continuesTaskID?.let { currentTaskId ->
            if (currentTaskId == taskId) {
                callbacksQueue.find { it.task.taskId == currentTaskId }?.let { item ->
                    item.callback?.invoke()
                    callbacksQueue.remove(item)
                }
                continuesTaskID = null
            }
        }
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

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun readMessage(
        message: XMPPMessage,
        queryId: String? = null  // теперь можно передать явно, если нужно
    ): MessageStorageItem? = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            // 1. Проверяем, это вообще сообщение с текстом или служебное
            if (isChatStateOrMarker(message) || message.type == "headline") {
                return@withContext null
            }

            val from = message.from?.bare() ?: return@withContext null
            val to = message.to?.bare() ?: return@withContext null

            val isGroupChat = message.hasElement("x", "https://xabber.com/protocol/groups")
            val conversationType = if (isGroupChat) ConversationType.Group else ConversationType.Regular

            val originalOutgoing = if (isGroupChat) {
                message.element("x", "https://xabber.com/protocol/groups")
                    ?.element("reference", "https://xabber.com/protocol/references")
                    ?.element("user", "https://xabber.com/protocol/groups")
                    ?.getAttribute("id") == owner
            } else {
                from == owner
            }

            var opponent = if (originalOutgoing) to else from
            if (opponent == owner) return@withContext null // self-message

            val timestamp = message.date?.let { Date(it) } ?: Date()
            var body = message.body?.takeIf { it.isNotBlank() } ?: return@withContext null

            // Пропускаем inline forwards — они обрабатываются отдельно
            val hasForwardedReference = message.elements("reference", "https://xabber.com/protocol/references")
                .any { it.element("forwarded", "urn:xmpp:forward:0") != null }

            if (hasForwardedReference) {
                Log.d(TAG, "Skipping inline forwarded message (processed as quote)")
                return@withContext null
            }

            var archivedId = message.element("archived", "urn:xmpp:mam:tmp")?.getAttribute("id")
                ?: message.element("stanza-id")?.getAttribute("id")
                ?: message.element("archived")?.getAttribute("id")
                ?: ""

            val originId = message.originId ?: message.id

            // Дедупликация по originId (если это MAM-сообщение, а у нас уже есть stub)
            if (originId != null) {
                val existingByOrigin = realm.query<MessageStorageItem>(
                    "messageId = $0 AND archivedId = '' AND owner = $1",
                    originId, owner
                ).first().find()

                if (existingByOrigin != null) {
                    realm.write {
                        findLatest(existingByOrigin)?.apply {
                            this.archivedId = archivedId
                            this.sentDate = timestamp.time
                            this.body = body
                            this.isRead = true
                            this.state = MessageSendingState.Sent
                        }
                    }
                    Log.d(TAG, "Updated stub → real message: originId=$originId, archivedId=$archivedId")
                    temporaryMessageReceiver?.didReceiveMessage(existingByOrigin, queryId ?: "mam")
                    return@withContext existingByOrigin
                }
            }

            // Создаём новое сообщение
            val instance = MessageStorageItem().apply {
                owner = this@MessageArchiveManager.owner
                opponent = this.opponent
                messageId = originId ?: NanoId.generate()
                body = this.body
                outgoing = originalOutgoing
                isRead = true  // MAM = всегда прочитано
                sentDate = timestamp.time
                date = timestamp.time
                conversationType_ = conversationType.rawValue
                archivedId = this.archivedId
                this.queryIds = queryId

                // References
                references = extractReferences(message)

                // Afterburn
                message.element("ephemeral", "urn:xmpp:ephemeral:0")
                    ?.getAttribute("timer")
                    ?.toDoubleOrNull()
                    ?.let { seconds ->
                        afterburnInterval = seconds.toLong()
                        burnDate = (timestamp.time + (seconds * 1000).toLong())
                        if (burnDate <= System.currentTimeMillis()) {
                            isDeleted = true
                            this.body = ""
                        }
                    }
            }

            realm.write {
                val saved = copyToRealm(instance, UpdatePolicy.ALL)
                saved.storeStanza(this) // если всё ещё нужен raw XML — сохраняем
            }

            temporaryMessageReceiver?.didReceiveMessage(instance, queryId ?: "mam")


            return@withContext instance

        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MAM message: ${e.message}", e)
            null
        } finally {
            realm.close()
        }
    }


    private fun extractReferences(message: XMPPMessage): RealmList<MessageReferenceStorageItem> {
        val references = realmListOf<MessageReferenceStorageItem>()

        message.children.forEach { child ->
            if (child.name == "reference" && child.namespace == "https://xabber.com/protocol/references") {
                val forwarded = child.element("forwarded", namespace = "urn:xmpp:forward:0")
                val forwardedMessage = forwarded?.element("message", namespace = "jabber:client")

                if (forwardedMessage != null) {
                    // Это пересланное сообщение — сохраняем как inline forward
                    val inlineForward = MessageForwardsInlineStorageItem().apply {
                        messageId = forwardedMessage.getAttribute("id") ?: ""
                        owner = this@MessageArchiveManager.owner
                        jid = forwardedMessage.getAttribute("from") ?: ""
                        forwardJid = jid
                        forwardNickname = "" // можно заполнить из roster
                        body = forwardedMessage.element("body")?.textContent ?: ""
                        kind_ = MessageForwardsInlineStorageItemKind.quote.rawValue
                        isOutgoing = false
                        originalDate = parseTimestamp(XMPPMessage(forwardedMessage.raw))
                    }
                    // Если нужно — рекурсивно обработать вложенные forwards
                    // inlineForward.subforwards.addAll(...)

                    // Но главное — НЕ создаём MessageStorageItem!
                    Log.d(TAG, "Detected inline forward: ${inlineForward.body.take(50)}")
                    return realmListOf() // ← Возвращаем пустой список references
                } else {
                    // Обычная reference (файл, гео и т.д.)
                    val uri = child.getAttribute("uri") ?: return@forEach
                    val refItem = MessageReferenceStorageItem().apply {
                        primary = "${child.getAttribute("id") ?: System.currentTimeMillis()}_ref"
                        this.uri = uri
                        mimeType = child.getAttribute("type") ?: ""
                        fileSize = child.getAttribute("size")?.toLongOrNull() ?: 0L
                        fileName = child.getAttribute("name") ?: ""
                        isGeo = child.getAttribute("type") == "geo"
                    }
                    references.add(refItem)
                }
            }
        }

        return references
    }



    private fun getMuteExpired(jid: String, conversationType: ConversationType, realm: Realm): Long {
        return realm.query<LastChatsStorageItem>(
            "primary = $0",
            LastChatsStorageItem.genPrimary(jid, owner, conversationType)
        ).first().find()?.muteExpired ?: 0L
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun read(iq: String, stream: Stream): Boolean = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(iq.byteInputStream())
            val iqElement = document.documentElement
            if (iqElement.getAttribute("type") != "result") {
                Log.w(TAG, "Ignoring non-result IQ: ${iq.take(200)}")
                return@withContext false
            }
            val finElement = iqElement.getElementsByTagNameNS(namespace, "fin").item(0) as? Element
            val queryId = finElement?.getAttribute("queryid") ?: return@withContext false

            val complete = finElement.getAttribute("complete")?.toBooleanStrictOrNull() ?: false
            val setElement = finElement.getElementsByTagNameNS("http://jabber.org/protocol/rsm", "set")?.item(0) as? Element
            val first = setElement?.getElementsByTagName("first")?.item(0)?.textContent ?: ""
            val last = setElement?.getElementsByTagName("last")?.item(0)?.textContent ?: ""
            val count = setElement?.getElementsByTagName("count")?.item(0)?.textContent?.toIntOrNull() ?: 0

            queryToReceivedCount[queryId] = (queryToReceivedCount[queryId] ?: 0) + count

            if (complete) {
                queryIdsMutex.withLock {
                    val callbackItem = queryIds[queryId]
                    callbackItem?.task?.let { task ->
                        realm.writeBlocking {
                            val chatPrimary = LastChatsStorageItem.genPrimary(
                                task.jid ?: owner,
                                owner,
                                task.conversationType
                            )
                            val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find()
                            chat?.let {
                                findLatest(it)?.apply {
                                    isSynced = true
                                    fullArchiveLoaded = true
                                    Log.d(TAG, "MAM archive fully loaded for chat $chatPrimary (complete=true)")
                                }
                            }
                        }
                    }
                }
            }

            queryIdsMutex.withLock {
                val callbackItem = queryIds[queryId]
                if (callbackItem != null) {
                    val task = callbackItem.task
                    realm.write {
                        val chat = query<LastChatsStorageItem>(
                            "primary = $0",
                            LastChatsStorageItem.genPrimary(task.jid ?: owner, owner, task.conversationType)
                        ).first().find()
                        if (chat != null) {
                            findLatest(chat)?.apply {
                                if (task.isNormalSynchronousTask || (task.isContinues && complete && count < 2)) {
                                    fullArchiveLoaded = true
                                }
                                lastLoadedMessageHistoryId = last
                            }
                        }
                    }
                    if (task.isContinues) {
                        if (!complete && count > 0) {
                            val continueUid = if (task.backward) first else last
                            continueLoadHistory(stream, task, continueUid)
                        } else {
                            // Последняя страница — завершаем
                            delay(300L) // чуть меньше, чтобы не ждать лишнего
                            callbackItem.callback?.invoke()
                            callbacksQueue.remove(callbackItem)
                            interactiveQueue.remove(queryId)
                            queryIds.remove(queryId)
                        }
                    } else {
                        // Не continues — просто завершаем
                        callbackItem.callback?.invoke()
                        callbacksQueue.remove(callbackItem)
                        interactiveQueue.remove(queryId)
                        queryIds.remove(queryId)
                    }
                }
            }
            temporaryMessageReceiver?.didReceiveEndPage(queryId, complete, first, last, count)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse IQ: ${e.message}, iq=$iq", e)
            temporaryMessageReceiver?.didReceiveEndPage("", false, "", "", 0)
            return@withContext false
        } finally {
            realm.close()
        }
    }



    private suspend fun continueLoadHistory(stream: Stream, task: MAMRequestItem, continueUid: String?) {
        if (continueUid == null || task.taskId != continuesTaskID) {
            callbacksQueue.find { it.task.taskId == task.taskId }?.let { item ->
                item.callback?.invoke()
                callbacksQueue.remove(item)
            }
            return
        }

        requestArchive(
            stream = stream,
            jid = task.jid,
            isContinues = true,
            conversationType = task.conversationType,
            queryId = task.queryId,
            searchText = task.searchText,
            start = task.start,
            end = task.end,
            max = task.max,
            withCounter = true,
            rsmBefore = continueUid,
            rsmAfter = null,
            backward = true
        )
    }

    private fun buildX(
        searchText: String?,
        start: Date?,
        end: Date?,
        withCounter: Boolean,
        jid: String?,
        conversationType: ConversationType,
        isGroupchat: Boolean,
        beforeId: String?, // Added
        afterId: String?   // Added
    ): String = buildString {
        append("<x xmlns='jabber:x:data' type='submit'>")
        append("<field var='FORM_TYPE' type='hidden'><value>$namespace</value></field>")
        if (jid != null && !isGroupchat) append("<field var='with'><value>$jid</value></field>")
        if (start != null) append("<field var='start'><value>${formatDate(start)}</value></field>")
        if (end != null) append("<field var='end'><value>${formatDate(end)}</value></field>")
        if (beforeId != null && beforeId.isNotEmpty()) append("<field var='before-id'><value>$beforeId</value></field>")
        if (afterId != null && afterId.isNotEmpty()) append("<field var='after-id'><value>$afterId</value></field>")
        if (searchText != null && searchText.isNotEmpty()) append("<field var='search'><value>$searchText</value></field>")
        append("<field var='conversation-type'><value>${conversationType.rawValue}</value></field>")
        append("</x>")
    }

    private fun buildSet(max: Int, before: String?, after: String?): String = buildString {
        append("<set xmlns='http://jabber.org/protocol/rsm'>")
        append("<max>$max</max>")
        if (before != null && before.isNotEmpty()) append("<before>$before</before>")
        else if (before != null) append("<before/>")
        if (after != null) append("<after>$after</after>")
        append("</set>")
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(date)
    }

    private fun getDelayedDate(message: XMPPMessage): Date? {
        return parseTimestamp(message, TAG)?.let { Date(it) }
    }

    private fun isSystemMessage(message: XMPPMessage): Boolean {
        return message.hasElement("system", namespace = "urn:xmpp:system") ||
                message.hasElement("x", namespace = "https://xabber.com/protocol/groups#system-message")
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
            val children = parseChildren(element)
            var originId: String? = null

            // Extract originId if present
            children.forEach { child ->
                if (child.name == "origin-id" && child.namespace == "urn:xmpp:sid:0") {
                    originId = child.attributes["id"]
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
            ).apply { this.originId = originId }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XMPP message: ${e.message}", e)
            return null
        }
    }

    private fun parseChildren(element: Element): List<XMLElement> {
        val children = mutableListOf<XMLElement>()
        val nodeList = element.childNodes

        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element && node.localName != "body" && node.localName != "subject" && node.localName != "thread" && node.localName != "error") {  // Skip flat elements already extracted
                val childAttributes = node.attributes.let { attrs ->
                    (0 until attrs.length).associate { idx ->
                        val attr = attrs.item(idx)
                        attr.nodeName to attr.nodeValue
                    }
                }
                val childChildren = parseChildren(node)  // Recurse
                val child = XMLElement(
                    name = node.localName,
                    namespace = node.namespaceURI,
                    raw = node.toString(),
                    attributes = childAttributes,
                    children = childChildren
                )
                children.add(child)
            }
        }
        return children
    }

    fun reset() {
        callbacksQueue.forEach { it.callback?.invoke() }
        callbacksQueue.clear()
        searchResultsQueries.clear()
        interactiveQueue.clear()
        continuesTaskID = null
    }

    fun didResetState() {
        callbacksQueue.forEach { it.callback?.invoke() }
        callbacksQueue.clear()
        searchResultsQueries.clear()
        interactiveQueue.clear()
        continuesTaskID = null
        Log.d(TAG, "Reset state for owner $owner, preserving queryIds=$queryIds")
    }

    fun incrementReceived(queryId: String) {
        queryToReceivedCount[queryId] = (queryToReceivedCount[queryId] ?: 0) + 1
    }
}