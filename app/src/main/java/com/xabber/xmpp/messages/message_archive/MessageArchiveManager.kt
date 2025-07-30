// MessageArchiveManager.kt
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
import com.xabber.utils.prp
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
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

@RequiresApi(Build.VERSION_CODES.O)
class MessageArchiveManager(private val owner: String) {
    private val realm = Realm.open(defaultRealmConfig())
    private val namespace = "urn:xmpp:mam:2"
    private val pageSize = 50 // Matches Swift's page size
    private val callbacksQueue = mutableSetOf<CallbackQueueItem>()
    private val nanoIdAlphabet = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val nanoIdMask = 63
    private val nanoIdStep = 16
    private val TAG = "MessageArchiveManager"
    private var continuesTaskID: String? = null

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
        val beforeId: String?,
        val max: Int,
        val start: Date?,
        val end: Date?,
        val isNormalSynchronousTask: Boolean,
        val flipPage: Boolean = false // Added to match Swift
    )

    data class CallbackQueueItem(
        val jid: String,
        val elementId: String,
        val task: MAMRequestItem,
        val callback: (() -> Unit)?
    )

    data class HistoryGap(
        val newestMessageId: String,
        val oldestMessageId: String,
        val startDate: Date,
        val endDate: Date
    ) {
        init {
            // Adjust dates as in Swift (+10/-10 minutes)
            val adjustedStart = Date(startDate.time + 600_000)
            val adjustedEnd = Date(endDate.time - 600_000)
        }
    }

    interface TemporaryMessageReceiver {
        fun didReceiveMessage(item: MessageStorageItem, queryId: String)
        fun didReceiveEndPage(queryId: String, fin: Boolean, first: String, last: String, count: Int)
    }

    var temporaryMessageReceiver: TemporaryMessageReceiver? = null

    suspend fun requestArchive(
        stream: Stream,
        jid: String?,
        isContinues: Boolean,
        conversationType: ConversationType,
        queryId: String? = null,
        searchText: String? = null,
        before: String? = null,
        beforeId: String? = null,
        afterId: String? = null,
        start: Date? = null,
        end: Date? = null,
        nextPage: String? = null,
        prevPage: String? = null,
        max: Int? = null,
        withCounter: Boolean = false, // Matches Swift's default
        isNormalSynchronousTask: Boolean = false,
        flipPage: Boolean = false, // Added to match Swift
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val isGroupchat = listOf(ConversationType.Group, ConversationType.Channel).contains(conversationType)
        val elementId = queryId ?: "MAM: ${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        val taskId = listOf(jid ?: "global_search", conversationType.rawValue).prp()

        var queryXml = "<query xmlns='$namespace' queryid='$elementId'>"
        val x = buildX(searchText, beforeId, afterId, start, end, withCounter, jid, conversationType, isGroupchat)
        val set = buildSet(max ?: pageSize, nextPage, prevPage)
        queryXml += x + set
        if (flipPage) queryXml += "<flip-page/>"
        queryXml += "</query>"

        val toAttr = if (isGroupchat && jid != null) " to='$jid'" else ""
        val iqXml = "<iq type='set' id='$elementId'$toAttr>$queryXml</iq>"

        try {
            val success = stream.socket?.write(iqXml) == true
            if (success) {
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
                            beforeId = beforeId,
                            max = max ?: pageSize,
                            start = start,
                            end = end,
                            isNormalSynchronousTask = isNormalSynchronousTask,
                            flipPage = flipPage
                        ),
                        callback = callback
                    )
                )
                Log.d(TAG, "Sent MAM query: id=$elementId, jid=$jid, conversationType=${conversationType.rawValue}, flipPage=$flipPage")
            } else {
                Log.e(TAG, "Failed to send MAM query: $iqXml")
                callback?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending MAM query: ${e.message}", e)
            callback?.invoke()
        }
    }

    suspend fun syncChat(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        callback: (() -> Unit)?
    ) = withContext(Dispatchers.IO) {
        var isInitialArchiveLoaded = false
        var isSynced = false
        var archiveStart: Date? = null
        val gaps = mutableListOf<GapInfo>()
        realm.writeBlocking {
            val chat = query<LastChatsStorageItem>("primary = $0", LastChatsStorageItem.genPrimary(jid, owner, conversationType)).first().find()
            if (chat != null) {
                isInitialArchiveLoaded = chat.isInitialArchiveLoaded
                isSynced = chat.isSynced
                val messages = query<MessageStorageItem>("owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false", owner, jid, conversationType.rawValue)
                    .find()
                    .sortedByDescending { it.date }
                if (messages.isNotEmpty()) {
                    val tempGaps = mutableListOf<HistoryGap>()
                    for (i in 0 until messages.size - 1) {
                        val current = messages[i]
                        val next = messages[i + 1]
                        val currentQueryIds = (current.queryIds?.split(",")?.toSet() ?: emptySet())
                        val nextQueryIds = (next.queryIds?.split(",")?.toSet() ?: emptySet())
                        if (currentQueryIds.intersect(nextQueryIds).isEmpty()) {
                            tempGaps.add(
                                HistoryGap(
                                    newestMessageId = current.archivedId ?: "",
                                    oldestMessageId = next.archivedId ?: "",
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
                                queryId = "MAM gap $index: ${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                                start = gap.endDate,
                                end = gap.startDate
                            )
                        )
                    }
                }
                if (gaps.isEmpty()) {
                    val oldestMessage = messages.lastOrNull()
                    if (oldestMessage != null) {
                        archiveStart = Date(oldestMessage.date - 600_000) // 10 minutes before oldest
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
            }
            if (listOf(ConversationType.Omemo, ConversationType.Omemo1, ConversationType.Axolotl).contains(conversationType)) {
                val account = query<AccountStorageItem>("jid = $0", owner).first().find()
                archiveStart = account?.createdAt?.let { Date(it) } ?: Date(0)
            }
        }

        if (isSynced) {
            Log.d(TAG, "Chat already synced for jid=$jid, skipping sync")
            callback?.invoke()
            return@withContext
        }

        val taskId = listOf(jid, conversationType.rawValue).prp()
        if (continuesTaskID != null && continuesTaskID != taskId) {
            callbacksQueue.find { it.task.taskId == continuesTaskID }?.let { item ->
                item.callback?.invoke()
                callbacksQueue.remove(item)
            }
        }
        continuesTaskID = taskId

        val queryId = "MAM: ${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        Log.d(TAG, "Registering initial MAM query with queryId=$queryId for jid=$jid, start=$archiveStart")
        requestArchive(
            stream = stream,
            jid = jid,
            isContinues = true,
            conversationType = conversationType,
            queryId = queryId,
            start = archiveStart,
            max = pageSize,
            withCounter = false,
            isNormalSynchronousTask = true,
            flipPage = true, // Added to match Swift
            callback = {
                realm.writeBlocking {
                    val instance = query<LastChatsStorageItem>("primary = $0", LastChatsStorageItem.genPrimary(jid, owner, conversationType)).first().find()
                    if (instance != null) {
                        findLatest(instance)?.apply {
                            isSynced = true
                            isInitialArchiveLoaded = true
                        }
                        Log.d(TAG, "Updated LastChatsStorageItem for jid=$jid: isSynced=true, isInitialArchiveLoaded=true")
                    }
                }
                // Load previous history if no gaps or oldest message exists
                val oldestMessage = realm.query<MessageStorageItem>("owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false", owner, jid, conversationType.rawValue)
                    .find()
                    .sortedByDescending { it.date }
                    .lastOrNull()
                if (oldestMessage != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        getPrevHistory(
                            stream = stream,
                            jid = jid,
                            conversationType = conversationType,
                            messageId = oldestMessage.archivedId ?: "",
                            callback = {
                                Log.d(TAG, "Completed prev history load for jid=$jid, messageId=${oldestMessage.archivedId}")
                            }
                        )
                    }
                }
                // Load gap queries
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
                            withCounter = false,
                            flipPage = true, // Added to match Swift
                            callback = {
                                Log.d(TAG, "Completed gap query for jid=$jid, queryId=${gap.queryId}, start=${gap.start}, end=${gap.end}")
                            }
                        )
                    }
                }
                callback?.invoke()
                Log.d(TAG, "Executed callback for queryId=$queryId")
            }
        )
    }

    suspend fun getPrevHistory(
        stream: Stream,
        jid: String,
        conversationType: ConversationType,
        messageId: String,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val message = realm.query<MessageStorageItem>("primary = $0", messageId).first().find()
            val timestamp = message?.sentDate?.let {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.format(Date(it))
            } ?: ""
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = false,
                conversationType = conversationType,
                queryId = "MAM prev history: ${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                beforeId = messageId,
                end = timestamp.takeIf { it.isNotEmpty() }?.let { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(it) },
                max = pageSize,
                withCounter = false,
                flipPage = true, // Added to match Swift
                callback = callback
            )
            Log.d(TAG, "Requested prev history for jid=$jid, messageId=$messageId, end=$timestamp")
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
        val realm = Realm.open(defaultRealmConfig())
        try {
            val message = realm.query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND archivedId = $2",
                owner, jid, messageId ?: ""
            ).first().find()
            val messageTimestamp = message?.date ?: System.currentTimeMillis()
            val startDate = Date(messageTimestamp)
            val endDate = Calendar.getInstance().apply {
                time = Date(messageTimestamp)
                add(Calendar.MINUTE, 20) // Matches Swift's +20 minutes
            }.time
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = false,
                conversationType = conversationType,
                queryId = "MAM next history: ${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                afterId = messageId,
                start = startDate,
                end = endDate,
                max = pageSize,
                withCounter = false,
                flipPage = true, // Added to match Swift
                callback = callback
            )
            Log.d(TAG, "Requested next history for jid=$jid, messageId=$messageId, start=$startDate, end=$endDate")
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
        if (continuesTaskID != null && continuesTaskID != taskId) {
            callbacksQueue.find { it.task.taskId == continuesTaskID }?.let { item ->
                item.callback?.invoke()
                callbacksQueue.remove(item)
            }
        }
        continuesTaskID = taskId

        val realm = Realm.open(defaultRealmConfig())
        try {
            val messageId = realm.query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND conversationType_ = $2",
                owner, jid, conversationType.rawValue
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
                max = pageSize,
                withCounter = false,
                flipPage = true // Added to match Swift
            )
            Log.d(TAG, "Started full history load for jid=$jid, before=$messageId, start=$archiveStart")
        } finally {
            realm.close()
        }
    }

    suspend fun checkShouldLoadFullHistory(jid: String, conversationType: ConversationType): Boolean = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val instance = realm.query<LastChatsStorageItem>("primary = $0", LastChatsStorageItem.genPrimary(jid, owner, conversationType)).first().find()
            if (instance != null) {
                val taskId = listOf(jid, conversationType.rawValue).prp()
                if (instance.isAllHistoryLoaded) {
                    return@withContext false
                }
                if (continuesTaskID == null) {
                    return@withContext true
                }
                if (continuesTaskID == taskId) {
                    return@withContext false
                }
                if (!instance.fullArchiveLoaded) {
                    return@withContext true
                }
            }
            return@withContext false
        } finally {
            realm.close()
        }
    }

    suspend fun read(iq: String, stream: Stream): Boolean = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val sanitizedIq = iq.replace(Regex("r\\.boldin='modify'"), "")
            val document = builder.parse(sanitizedIq.byteInputStream())
            val iqElement = document.documentElement
            if (iqElement.getAttribute("type") != "result") {
                Log.w(TAG, "Ignoring non-result IQ: $sanitizedIq")
                return@withContext false
            }
            val finElement = iqElement.getElementsByTagNameNS(namespace, "fin").item(0) as? Element
            val queryId = finElement?.getAttribute("queryid") ?: return@withContext false
            val complete = finElement?.getAttribute("complete")?.toBoolean() ?: false
            val setElement = finElement?.getElementsByTagNameNS("http://jabber.org/protocol/rsm", "set")?.item(0) as? Element
            val first = setElement?.getElementsByTagName("first")?.item(0)?.textContent ?: ""
            val last = setElement?.getElementsByTagName("last")?.item(0)?.textContent ?: ""
            val count = setElement?.getElementsByTagName("count")?.item(0)?.textContent?.toIntOrNull() ?: 0

            val callbackItem = callbacksQueue.find { it.elementId == iqElement.getAttribute("id") }
            if (callbackItem == null) {
                Log.w(TAG, "No callback found for queryId: $queryId")
                return@withContext false
            }

            realm.write {
                val chat = query<LastChatsStorageItem>("primary = $0", LastChatsStorageItem.genPrimary(callbackItem.jid, owner, callbackItem.task.conversationType)).first().find()
                if (chat != null) {
                    findLatest(chat)?.apply {
                        if (callbackItem.task.isNormalSynchronousTask) {
                            fullArchiveLoaded = complete && count == 0
                            isAllHistoryLoaded = complete && count == 0
                        }
                        lastLoadedMessageHistoryId = last
                        Log.d(TAG, "Updated LastChatsStorageItem for jid=${callbackItem.jid}: fullArchiveLoaded=${complete && count == 0}, lastLoadedMessageHistoryId=$last")
                    }
                }
            }

            temporaryMessageReceiver?.didReceiveEndPage(queryId, complete && count == 0, first, last, count)

            if (callbackItem.task.isContinues && count > 0) {
                continueLoadHistory(stream, callbackItem.task, last)
                Log.d(TAG, "Continuing MAM pagination for queryId=$queryId, last=$last, count=$count")
            } else {
                callbackItem.callback?.invoke()
                callbacksQueue.remove(callbackItem)
                Log.d(TAG, "Completed MAM query for queryId=$queryId, no further pagination needed")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse IQ: ${e.message}", e)
            return@withContext false
        } finally {
            realm.close()
        }
    }

    private suspend fun continueLoadHistory(stream: Stream, task: MAMRequestItem, nextPage: String?) {
        if (task.taskId != continuesTaskID || nextPage == null) {
            Log.d(TAG, "Skipping continueLoadHistory for taskId=${task.taskId}, expected=$continuesTaskID")
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
            afterId = nextPage,
            start = task.start,
            end = task.end,
            max = task.max,
            withCounter = false,
            flipPage = task.flipPage // Preserve flip-page setting
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
    ): String {
        var x = "<x xmlns='jabber:x:data' type='submit'>"
        x += "<field var='FORM_TYPE' type='hidden'><value>$namespace</value></field>"
        if (!beforeId.isNullOrEmpty()) {
            x += "<field var='before-id'><value>$beforeId</value></field>"
        }
        if (!afterId.isNullOrEmpty()) {
            x += "<field var='after-id'><value>$afterId</value></field>"
        }
        if (start != null) {
            x += "<field var='start'><value>${formatDate(start)}</value></field>"
        }
        if (end != null) {
            x += "<field var='end'><value>${formatDate(end)}</value></field>"
        }
        if (withCounter) {
            x += "<field var='rsm-counter'><value>1</value></field>"
        }
        if (!isGroupchat && jid != null) {
            x += "<field var='with'><value>$jid</value></field>"
        }
        if (!isGroupchat) {
            x += "<field var='conversation-type'><value>${conversationType.rawValue}</value></field>"
        }
        if (searchText != null) {
            x += "<field var='withtext'><value>$searchText</value></field>"
        }
        x += "</x>"
        return x
    }

    private fun buildSet(max: Int, nextPage: String?, prevPage: String?): String {
        var set = "<set xmlns='http://jabber.org/protocol/rsm'>"
        set += "<max>$max</max>"
        if (nextPage != null) {
            if (nextPage.isEmpty()) {
                set += "<after/>"
            } else {
                set += "<after>$nextPage</after>"
            }
        }
        if (prevPage != null) {
            set += "<before>$prevPage</before>"
        }
        set += "</set>"
        return set
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    data class GapInfo(val queryId: String, val start: Date, val end: Date)

    fun reset() {
        callbacksQueue.forEach { it.callback?.invoke() }
        callbacksQueue.clear()
        continuesTaskID = null
        Log.d(TAG, "Reset and cleared callbacks for owner $owner")
    }
}