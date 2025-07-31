package com.xabber.xmpp.messages.message_archive

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
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
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

@RequiresApi(Build.VERSION_CODES.O)
class MessageArchiveManager(private val owner: String) {

    private val namespace = "urn:xmpp:mam:2"
    private val pageSize = 50
    private val callbacksQueue = mutableSetOf<CallbackQueueItem>()
    private val searchResultsQueries = mutableSetOf<String>()
    private val interactiveQueue = mutableListOf<String>()
    private var continuesTaskID: String? = null
    private var isInitialArchiveRequested: Boolean = false
    private var allowHistoryFixTask: Boolean = false
    private val nanoIdAlphabet = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val nanoIdMask = 63
    private val nanoIdStep = 16
    private val TAG = "MessageArchiveManager"

    data class MAMRequestItem(
        val jid: String?,
        val taskId: String,
        val isGroupchat: Boolean,
        val messageId: String?,
        val conversationType: ConversationType,
        val isContinues: Boolean,
        val maxDate: Date?,
        val searchText: String?,
        val queryId: String?,
        val afterId: String?,
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
    }

    var temporaryMessageReceiver: TemporaryMessageReceiver? = null
    private val queryIds = mutableSetOf<String>()


    init {
        // Initialize isInitialArchiveRequested (e.g., from SharedPreferences or similar)
        isInitialArchiveRequested = false // Replace with actual storage check
    }

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
        val elementId = queryId ?: "MAM:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        val taskId = listOf(jid ?: "global_search", conversationType.rawValue).prp()

        val queryXml = buildString {
            append("<query xmlns='$namespace' queryid='$elementId'>")
            append(buildX(searchText, beforeId, afterId, start, end, withCounter, jid, conversationType, isGroupchat))
            append(buildSet(max ?: pageSize, nextPage, prevPage))
            append("</query>")
            if (flipPage) append("<flip-page/>")
        }

        val toAttr = if (isGroupchat && jid != null) " to='$jid'" else ""
        val iqXml = "<iq type='set' id='$elementId'$toAttr>$queryXml</iq>"

        val success = stream.socket?.write(iqXml) == true
        if (success) {
            if (searchText != null) searchResultsQueries.add(elementId)
            callbacksQueue.add(
                CallbackQueueItem(
                    jid = jid ?: "",
                    elementId = elementId,
                    task = MAMRequestItem(
                        jid = jid,
                        taskId = taskId,
                        isGroupchat = isGroupchat,
                        messageId = before,
                        conversationType = conversationType,
                        isContinues = isContinues,
                        maxDate = start,
                        searchText = searchText,
                        queryId = elementId,
                        afterId = afterId,
                        max = max ?: pageSize,
                        start = start,
                        end = end,
                        isNormalSynchronousTask = isNormalSynchronousTask
                    ),
                    callback = callback
                )
            )
            interactiveQueue.add(elementId)
            queryIds.add(elementId)  // Added to track query IDs
            Log.d(TAG, "Sent MAM query: id=$elementId, jid=$jid, conversationType=${conversationType.rawValue}")
        } else {
            Log.e(TAG, "Failed to send MAM query: $iqXml")
        }
    }

    suspend fun getHistoryByDate(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        start: Date? = null,
        end: Date? = null,
        reversed: Boolean = false,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = false,
            conversationType = conversationType,
            queryId = "MAM untill rev=${reversed} history:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
            searchText = null,
            flipPage = true,
            start = start,
            end = end,
            nextPage = if (reversed) "" else null,
            max = 250,
            isNormalSynchronousTask = true,
            callback = callback
        )
    }

    suspend fun searchText(
        stream: Stream,
        jid: String? = null,
        conversationType: ConversationType,
        text: String,
        max: Int = 250,
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
            nextPage = "",
            max = max
        )
        continuesTaskID = taskId
        queryId
    }

    suspend fun getLastMessage(
        stream: Stream,
        jid: String,
        conversationType: ConversationType
    ) = withContext(Dispatchers.IO) {
        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = false,
            conversationType = conversationType,
            nextPage = "",
            max = 1
        )
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
            queryId = "MAM prev history:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
            flipPage = true,
            prevPage = messageId,
            max = 250,
            callback = callback
        )
    }

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

            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = false,
                conversationType = conversationType,
                queryId = "MAM next history:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                flipPage = true,
                end = modifiedDate,
                nextPage = "",
                max = 250,
                isNormalSynchronousTask = true,
                callback = callback
            )
        } finally {
            realm.close()
        }
    }

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

            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = true,
                conversationType = conversationType,
                before = messageId,
                start = archiveStart,
                max = pageSize
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
        Log.d(TAG, "Ended load history for jid=$jid, conversationType=${conversationType.rawValue}")
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

    suspend fun readMessage(message: String): Boolean = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(message.byteInputStream())
            val messageElement = document.documentElement
            val resultElement = messageElement.getElementsByTagNameNS(namespace, "result").item(0) as? Element
            val queryId = resultElement?.getAttribute("queryid") ?: return@withContext false

            if (!searchResultsQueries.contains(queryId)) {
                Log.w(TAG, "Query ID $queryId not found in searchResultsQueries")
                return@withContext false
            }

            val forwardedElement = resultElement.getElementsByTagNameNS("urn:xmpp:forward:0", "forwarded").item(0) as? Element
            val forwardedMessage = forwardedElement?.getElementsByTagName("message")?.item(0) as? Element
                ?: return@withContext true
            val xmppMessage = parseXMPPMessage(forwardedMessage) ?: return@withContext true

            // Check for chat state or marker messages
            if (isChatStateOrMarker(xmppMessage)) {
                Log.d(TAG, "Skipping chat state/marker message for queryId=$queryId")
                return@withContext true
            }

            val from = xmppMessage.from?.bare() ?: return@withContext true
            val to = xmppMessage.to?.bare() ?: return@withContext true
            val opponent = if (to != owner) to else from
            val delayedDate = getDelayedDate(xmppMessage) ?: Date()
            val isEncrypted = xmppMessage.hasElement("encrypted", namespace = "urn:xmpp:omemo:2")
            val omemoError = if (isEncrypted) {
                !xmppMessage.hasElement("omemo-result__system", namespace = "urn:xmpp:omemo:0")
            } else false
            val afterburnInterval = xmppMessage.element("ephemeral", namespace = "urn:xmpp:ephemeral:0")
                ?.getAttribute("timer")?.toDoubleOrNull() ?: 0.0
            var errorMetadata = mutableMapOf<String, Any>()
            var envelopeContainer: String? = null
            val hasSignElement = xmppMessage.hasElement("time-signature", namespace = "urn:xmpp:signatures")
            if (hasSignElement) {
                envelopeContainer = xmppMessage.element("time-signature", namespace = "urn:xmpp:signatures")?.raw
                // Placeholder for signature verification
                // errorMetadata = SignatureManager.shared.checkSignature(...)
            }

            val groupElement = xmppMessage.element("x", namespace = "https://xabber.com/protocol/groups")
            var originalOutgoing = false
            val userId = groupElement?.element("reference", namespace = "https://xabber.com/protocol/groups")
                ?.element("user", namespace = "https://xabber.com/protocol/groups")?.getAttribute("id")
            if (userId != null) {
                // Check if userId matches the current user's ID
                originalOutgoing = userId == owner
            } else {
                originalOutgoing = from == owner
            }

            val instance = MessageStorageItem()
            val conversationType = instance.conversationTypeByMessage(xmppMessage)
            var isRead = originalOutgoing
            val readDate: Date? = null // Placeholder: Implement readDate logic if needed
            if (readDate != null && delayedDate.time < readDate.time) {
                isRead = true
            }

            if (isSystemMessage(xmppMessage)) {
                instance.configureSystemMessage(
                    message = xmppMessage,
                    owner = owner,
                    opponent = opponent,
                    date = delayedDate
                )
            } else {
                instance.configureIncomingMessage(
                    message = xmppMessage,
                    owner = owner,
                    opponent = opponent,
                    outgoing = originalOutgoing,
                    isRead = isRead,
                    date = delayedDate,
                    isEncrypted = isEncrypted
                )
            }
            instance.envelopeContainer = envelopeContainer
            instance.afterburnInterval = afterburnInterval
            if (hasSignElement) instance.errorMetadata = errorMetadata
            if (isEncrypted && errorMetadata.isNotEmpty()) {
                instance.messageError = if (omemoError) "omemo" else if (hasSignElement) "cert_error" else null
            }
            if (afterburnInterval > 0 && isEncrypted && errorMetadata.isNotEmpty() && omemoError) {
                instance.isDeleted = true
            }
            if (afterburnInterval > 0 && readDate != null) {
                instance.isRead = true
                instance.readDate = readDate.time.toDouble()
                instance.burnDate = readDate.time.toDouble() + afterburnInterval
                if (instance.burnDate <= System.currentTimeMillis().toDouble()) {
                    instance.isDeleted = true
                    instance.body = ""
                    instance.legacyBody = ""
                }
            }
            instance.queryIds = instance.queryIds?.let { "$it,$queryId" } ?: queryId

            realm.write {
                copyToRealm(instance, UpdatePolicy.ALL)
            }
            temporaryMessageReceiver?.didReceiveMessage(instance, queryId)
            Log.d(TAG, "Processed message: queryId=$queryId, messageId=${instance.messageId}, opponent=$opponent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}", e)
            false
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
            var isInitialArchiveLoaded = false
            var isSynced = false
            var archiveStart: Date? = null
            val gaps = mutableListOf<GapInfo>()
            var messageId: String? = null
            var chat: LastChatsStorageItem? = null

            // Perform Realm read and write operations
            realm.write {
                chat = query<LastChatsStorageItem>(
                    "primary = $0",
                    LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                ).first().find()
                if (chat != null) {
                    isInitialArchiveLoaded = chat!!.isInitialArchiveLoaded
                    isSynced = chat!!.isSynced
                    val messages = query<MessageStorageItem>(
                        "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                        owner, jid, conversationType.rawValue
                    ).find().sortedByDescending { it.date }
                    if (messages.isNotEmpty()) {
                        val tempGaps = mutableListOf<HistoryGap>()
                        for (i in 0 until messages.size - 1) {
                            val current = messages[i]
                            val next = messages[i + 1]
                            val currentQueryIds = current.queryIds?.split(",")?.toSet() ?: emptySet()
                            val nextQueryIds = next.queryIds?.split(",")?.toSet() ?: emptySet()
                            if (currentQueryIds.intersect(nextQueryIds).isEmpty()) {
                                tempGaps.add(
                                    HistoryGap(
                                        newestMessageId = current.archivedId,
                                        oldestMessageId = next.archivedId,
                                        startDate = Date(current.date),
                                        endDate = Date(next.date)
                                    )
                                )
                            }
                        }
                        var optimizedGaps = tempGaps
                        var optimizationDone = false
                        while (!optimizationDone) {
                            val newGaps = mutableListOf<HistoryGap>()
                            val excluded = mutableSetOf<Int>()
                            optimizedGaps.forEachIndexed { index, gap ->
                                if (excluded.contains(index)) return@forEachIndexed
                                val nextIndex = index + 1
                                if (nextIndex < optimizedGaps.size && gap.oldestMessageId == optimizedGaps[nextIndex].newestMessageId) {
                                    excluded.add(nextIndex)
                                    newGaps.add(
                                        HistoryGap(
                                            newestMessageId = gap.newestMessageId,
                                            oldestMessageId = optimizedGaps[nextIndex].oldestMessageId,
                                            startDate = gap.startDate,
                                            endDate = optimizedGaps[nextIndex].endDate
                                        )
                                    )
                                } else {
                                    newGaps.add(gap)
                                }
                            }
                            optimizationDone = newGaps.size == optimizedGaps.size
                            optimizedGaps = newGaps
                        }
                        optimizedGaps.forEachIndexed { index, gap ->
                            gaps.add(
                                GapInfo(
                                    queryId = "MAM gap $index:${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                                    start = gap.endDate,
                                    end = gap.startDate
                                )
                            )
                        }
                    }
                    if (gaps.isEmpty()) {
                        val oldestMessage = messages.lastOrNull()
                        if (oldestMessage != null) {
                            archiveStart = Date(oldestMessage.date - 600_000)
                        }
                    }
                } else {
                    val instance = LastChatsStorageItem().apply {
                        owner = this@MessageArchiveManager.owner
                        this.jid = jid
                        conversationType_ = conversationType.rawValue
                        messageDate = System.currentTimeMillis()
                        primary = LastChatsStorageItem.genPrimary(jid, owner, conversationType)
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
                }
                if (listOf(ConversationType.Omemo, ConversationType.Omemo1, ConversationType.Axolotl).contains(conversationType)) {
                    archiveStart = query<AccountStorageItem>("jid = $0", owner).first().find()?.createdAt?.let { Date(it) }
                }
                messageId = query<MessageStorageItem>(
                    "opponent = $0 AND owner = $1 AND conversationType_ = $2",
                    jid, owner, conversationType.rawValue
                ).find().sortedByDescending { it.date }.lastOrNull()?.archivedId
            }

            // Perform requestArchive outside the write block
            val queryId = "MAM:${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
            Log.d(TAG, "Registering initial MAM query with queryId=$queryId for jid=$jid, start=$archiveStart")
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = true,
                conversationType = conversationType,
                queryId = queryId,
                start = archiveStart,
                max = pageSize,
                withCounter = true,
                isNormalSynchronousTask = true,
                callback = {
                    val callbackRealm = Realm.open(defaultRealmConfig())
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            callbackRealm.write {
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
                        }

                    } finally {
                        callbackRealm.close()
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        gaps.forEach { gap ->
                            requestArchive(
                                stream = stream,
                                jid = jid,
                                isContinues = true,
                                conversationType = conversationType,
                                queryId = gap.queryId,
                                start = gap.start,
                                end = gap.end,
                                max = pageSize,
                                withCounter = true
                            )
                        }
                    }
                    callback?.invoke()
                    Log.d(TAG, "Executed callback for queryId=$queryId")
                }
            )
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

            val callbackItem = callbacksQueue.find { it.elementId == iqElement.getAttribute("id") }
            if (callbackItem == null) {
                Log.w(TAG, "No callback found for queryId: $queryId")
                return@withContext false
            }

            realm.write {
                val chat = query<LastChatsStorageItem>(
                    "primary = $0",
                    LastChatsStorageItem.genPrimary(callbackItem.jid, owner, callbackItem.task.conversationType)
                ).first().find()
                if (chat != null) {
                    findLatest(chat)?.apply {
                        if (callbackItem.task.isNormalSynchronousTask) {
                            fullArchiveLoaded = complete
                        }
                        lastLoadedMessageHistoryId = last
                        Log.d(TAG, "Updated LastChatsStorageItem for jid=${callbackItem.jid}: fullArchiveLoaded=$complete, lastLoadedMessageHistoryId=$last")
                    }
                }
            }

            temporaryMessageReceiver?.didReceiveEndPage(queryId, complete, first, last, count)

            if (callbackItem.task.isContinues && count > 0) {
                continueLoadHistory(stream, callbackItem.task, last)
                Log.d(TAG, "Continuing MAM pagination for queryId=$queryId, last=$last, count=$count")
            } else {
                if (callbackItem.task.isContinues && count == 0) {
                    makeInitialMessageVisible(jid = callbackItem.jid, conversationType = callbackItem.task.conversationType, queryId = callbackItem.elementId)
                }
                callbackItem.callback?.invoke()
                callbacksQueue.remove(callbackItem)
                interactiveQueue.remove(queryId)
                Log.d(TAG, "Completed MAM query for queryId=$queryId, no further pagination needed")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse IQ: ${e.message}", e)
            false
        } finally {
            realm.close()
        }
    }



    suspend fun makeInitialMessageVisible(jid: String, conversationType: ConversationType, queryId: String) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            realm.write {
                val instance = query<LastChatsStorageItem>(
                    "primary = $0",
                    LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                ).first().find()
                if (instance != null) {
                    findLatest(instance)?.apply {
                        fullArchiveLoaded = true // Assuming 'isAllHistoryLoaded' is 'fullArchiveLoaded'
                    }
                }
            }
            true
        } finally {
            realm.close()
        }
    }


    private suspend fun continueLoadHistory(stream: Stream, task: MAMRequestItem, nextPage: String?) {
        if (nextPage == null || task.taskId != continuesTaskID) {
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
            before = task.messageId,
            afterId = nextPage,
            start = task.start,
            end = task.end,
            max = task.max,
            withCounter = true
        )
        Log.d(TAG, "Requested next MAM page for queryId=${task.queryId}, jid=${task.jid}, afterId=$nextPage")
    }

    private fun buildX(
        searchText: String?,
        beforeId: String?,
        afterId: String?,
        start: Date?,
        end: Date?,
        withCounter: Boolean,
        jid: String?,
        conversationType: ConversationType,
        isGroupchat: Boolean
    ): String = buildString {
        append("<x xmlns='jabber:x:data' type='submit'>")
        append("<field var='FORM_TYPE' type='hidden'><value>$namespace</value></field>")
        if (!beforeId.isNullOrEmpty()) append("<field var='before-id'><value>$beforeId</value></field>")
        if (!afterId.isNullOrEmpty()) append("<field var='after-id'><value>$afterId</value></field>")
        if (start != null) append("<field var='start'><value>${formatDate(start)}</value></field>")
        if (end != null) append("<field var='end'><value>${formatDate(end)}</value></field>")
        if (withCounter) append("<field var='rsm-counter'><value>1</value></field>")
        if (!isGroupchat && jid != null) append("<field var='with'><value>$jid</value></field>")
        if (!isGroupchat) append("<field var='conversation-type'><value>${conversationType.rawValue}</value></field>")
        if (searchText != null) append("<field var='withtext'><value>$searchText</value></field>")
        append("</x>")
    }

    private fun buildSet(max: Int, nextPage: String?, prevPage: String?): String = buildString {
        append("<set xmlns='http://jabber.org/protocol/rsm'>")
        append("<max>$max</max>")
        if (nextPage != null) {
            if (nextPage.isEmpty()) append("<before/>") else append("<before>$nextPage</before>")
        }
        if (prevPage != null) append("<after>$prevPage</after>")
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

    fun reset() {
        callbacksQueue.forEach { it.callback?.invoke() }
        callbacksQueue.clear()
        searchResultsQueries.clear()
        interactiveQueue.clear()
        continuesTaskID = null
        Log.d(TAG, "Reset and cleared callbacks for owner $owner")
    }

    fun didResetState() {
        callbacksQueue.forEach { it.callback?.invoke() }
        callbacksQueue.clear()
        searchResultsQueries.clear()
        interactiveQueue.clear()
        continuesTaskID = null
    }
}

