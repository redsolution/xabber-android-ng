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
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.Dispatchers
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
    private val nanoIdAlphabet = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val nanoIdMask = 63
    private val nanoIdStep = 16
    private val TAG = "Message Receiver"

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
        flipPage: Boolean = false,
        before: String? = null,
        beforeId: String? = null,
        afterId: String? = null,
        start: Date? = null,
        end: Date? = null,
        nextPage: String? = null,
        prevPage: String? = null,
        max: Int? = null,
        withCounter: Boolean = false,
        isNormalSynchronousTask: Boolean = false,
        callback: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val isGroupchat = listOf(ConversationType.Group, ConversationType.Channel).contains(conversationType)
        val elementId = queryId ?: "MAM: ${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
        val taskId = listOf(jid ?: "global_search", conversationType.rawValue).prp()

        var queryXml = "<query xmlns='$namespace' queryid='$elementId'>"
        val x = buildX(searchText, beforeId, afterId, start, end, withCounter, jid, conversationType, isGroupchat)
        val set = buildSet(max ?: pageSize, nextPage, prevPage)
        queryXml += x + set
        queryXml += "</query>"
        if (flipPage) {
            queryXml += "<flip-page/>"
        }

        val toAttr = if (isGroupchat && jid != null) " to='$jid'" else ""
        val iqXml = "<iq type='set' id='$elementId'$toAttr>$queryXml</iq>"

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
                        queryId = queryId,
                        afterId = afterId,
                        max = max ?: pageSize,
                        start = start,
                        end = end,
                        isNormalSynchronousTask = isNormalSynchronousTask
                    ),
                    callback = callback
                )
            )
            Log.d("MessageArchiveManager", "Sent MAM query: $iqXml")
        } else {
            Log.e("MessageArchiveManager", "Failed to send MAM query: $iqXml")
        }
    }

    suspend fun syncChat(stream: Stream, jid: String, conversationType: ConversationType, callback: (() -> Unit)?) = withContext(Dispatchers.IO) {
        // Collect data needed for archive requests
        data class GapInfo(val queryId: String, val start: Date, val end: Date)
        val realm = Realm.open(defaultRealmConfig())
        var isInitialArchiveLoaded = false
        var isSynced = false
        var archiveStart: Date? = null
        val gaps = mutableListOf<GapInfo>()
        try {
            realm.writeBlocking {
                val chat = query<LastChatsStorageItem>("primary = $0", LastChatsStorageItem.genPrimary(jid, owner, conversationType)).first().find()
                if (chat != null) {
                    isInitialArchiveLoaded = chat.isInitialArchiveLoaded
                    isSynced = chat.isSynced
                    if (isInitialArchiveLoaded && isSynced) {
                        // Handle history gaps
                        val messages = query<MessageStorageItem>("owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false", owner, jid, conversationType.rawValue)
                            .find()
                            .sortedByDescending { it.date }
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
                        // Optimize gaps (merge adjacent gaps)
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
                        // Convert gaps to GapInfo for archive requests
                        optimizedGaps.forEachIndexed { index, gap ->
                            gaps.add(
                                GapInfo(
                                    queryId = "MAM fix history $index: ${NanoId.generateOptimized(6, nanoIdAlphabet, nanoIdMask, nanoIdStep)}",
                                    start = gap.endDate,
                                    end = gap.startDate
                                )
                            )
                        }
                    } else if (listOf(ConversationType.Omemo, ConversationType.Omemo1, ConversationType.Axolotl).contains(conversationType)) {
                        val account = query<AccountStorageItem>("jid = $0", owner).first().find()
                        archiveStart = account?.createdAt?.let { Date(it) }
                    } else {
                        Log.d(TAG, "HA, DUMBASS")
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
            }

            val queryId = "MAM: ${NanoId.generateOptimized(8, nanoIdAlphabet, nanoIdMask, nanoIdStep)}"
            Log.d(TAG, "Registering MAM query with queryId=$queryId for jid=$jid")
            requestArchive(
                stream = stream,
                jid = jid,
                isContinues = false,
                conversationType = conversationType,
                queryId = queryId,
                start = archiveStart,
                nextPage = "",
                isNormalSynchronousTask = true,
                callback = {
                    val callbackRealm = Realm.open(defaultRealmConfig())
                    try {
                        callbackRealm.writeBlocking {
                            val instance = query<LastChatsStorageItem>("primary = $0", LastChatsStorageItem.genPrimary(jid, owner, conversationType)).first().find()
                            if (instance != null) {
                                findLatest(instance)?.apply {
                                    isSynced = true
                                    isInitialArchiveLoaded = true
                                }
                                Log.d(TAG, "Updated LastChatsStorageItem for jid=$jid: isSynced=true, isInitialArchiveLoaded=true")
                            }
                        }
                        callback?.invoke()
                        Log.d(TAG, "Executed callback for queryId=$queryId")
                    } finally {
                        callbackRealm.close()
                    }
                }
            )
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
            val document = builder.parse(iq.byteInputStream())
            val iqElement = document.documentElement
            if (iqElement.getAttribute("type") != "result") {
                Log.w("MessageArchiveManager", "Ignoring non-result IQ: $iq")
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
                Log.w("MessageArchiveManager", "No callback found for queryId: $queryId")
                return@withContext false
            }

            realm.write {
                val chat = query<LastChatsStorageItem>("primary = $0", LastChatsStorageItem.genPrimary(callbackItem.jid, owner, callbackItem.task.conversationType)).first().find()
                if (chat != null) {
                    findLatest(chat)?.apply {
                        if (callbackItem.task.isNormalSynchronousTask) {
                            fullArchiveLoaded = complete
                        }
                        lastLoadedMessageHistoryId = last
                    }
                }
            }

            temporaryMessageReceiver?.didReceiveEndPage(queryId, complete, first, last, count)

            if (callbackItem.task.isContinues && !complete && last.isNotEmpty()) {
                continueLoadHistory(stream, callbackItem.task, last)
            } else {
                callbackItem.callback?.invoke()
                callbacksQueue.remove(callbackItem)
            }

            true
        } catch (e: Exception) {
            Log.e("MessageArchiveManager", "Failed to parse IQ: ${e.message}", e)
            return@withContext false
        } finally {
            realm.close()
        }
    }

    private suspend fun continueLoadHistory(stream: Stream, task: MAMRequestItem, nextPage: String) {
        requestArchive(
            stream = stream,
            jid = task.jid,
            isContinues = true,
            conversationType = task.conversationType,
            queryId = task.queryId,
            searchText = task.searchText,
            before = task.messageId,
            afterId = task.afterId,
            start = task.start,
            end = task.end,
            nextPage = nextPage,
            max = task.max
        )
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
                set += "<before/>"
            } else {
                set += "<before>$nextPage</before>"
            }
        }
        if (prevPage != null) {
            set += "<after>$prevPage</after>"
        }
        set += "</set>"
        return set
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    data class HistoryGap(
        val newestMessageId: String,
        val oldestMessageId: String,
        val startDate: Date,
        val endDate: Date
    ) {
        init {
            // Adjust dates to avoid overlap (as in Swift)
            val adjustedStart = Date(startDate.time + 600_000) // +10 minutes
            val adjustedEnd = Date(endDate.time - 600_000) // -10 minutes
        }
    }

    fun reset() {
        callbacksQueue.forEach { it.callback?.invoke() }
        callbacksQueue.clear()
        Log.d("MessageArchiveManager", "Reset and cleared callbacks for owner $owner")
    }
}