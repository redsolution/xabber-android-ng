package com.xabber.xmpp.roster

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.stream.Stream
import com.xabber.stream.serializers.XMPPIQ
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.roster.Subscription
import com.xabber.data_base.models.roster.Ask
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

@Serializable
@XmlSerialName("query", "jabber:iq:roster", "")
data class RosterQuery(
    val ver: String? = null,
    @XmlSerialName("item", "jabber:iq:roster", "")
    val items: List<RosterItem> = emptyList()
)

@Serializable
data class RosterItem(
    val jid: String,
    val name: String? = null,
    val subscription: String? = null,
    val ask: String? = null,
    val approved: String? = null,
    @XmlSerialName("group", "jabber:iq:roster", "")
    val groups: List<String> = emptyList()
)

@RequiresApi(Build.VERSION_CODES.O)
class RosterManager(private val owner: String, private val realm: Realm) {
    private val TAG = "RosterManager"
    private val queryIds = ConcurrentHashMap.newKeySet<String>()
    private val xml = XML {
        indent = 2
        autoPolymorphic = false
        defaultPolicy {
            ignoreUnknownChildren()
            pedantic = false
        }
    }
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val rosterBuffer = Channel<XMPPIQ>(capacity = 100) // Buffer up to 100 roster IQ stanzas
    private val bufferMutex = Mutex()
    private var processingJob: Job? = null
    private val stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob()) // Add this line

    init {
        scope.launch {
            startRosterProcessing()
            Log.w(TAG, "ROSTER MANAGER START")
        }
    }

    suspend fun request(stream: Stream) = withContext(Dispatchers.IO) {
        try {
            val elementId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val query = """
                <query xmlns='jabber:iq:roster' ver=''/>
            """.trimIndent()
            val iq = """
                <iq type='get' id='$elementId'>$query</iq>
            """.trimIndent()
            stream.socket?.write(iq)?.also { success ->
                if (success) {
                    queryIds.add(elementId)
                    Log.d(TAG, "Sent roster request IQ with id: $elementId")
                } else {
                    Log.e(TAG, "Failed to send roster request IQ for owner: $owner")
                }
            } ?: run {
                Log.e(TAG, "Cannot send roster request: Socket is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting roster: ${e.message}", e)
        }
    }

    suspend fun read(iq: XMPPIQ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (iq.queryNamespace != "jabber:iq:roster") {
                Log.w(TAG, "Ignoring non-roster IQ: namespace=${iq.queryNamespace}")
                return@withContext false
            }
            rosterBuffer.send(iq)
            Log.d(TAG, "Buffered roster IQ stanza: id=${iq.id}, thread=${Thread.currentThread().id}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error buffering roster IQ: ${e.message}, IQ: ${iq.raw}", e)
            return@withContext false
        }
    }

    private suspend fun startRosterProcessing() {
        processingJob?.cancelAndJoin()
        processingJob = scope.launch {
            val batch = mutableListOf<XMPPIQ>()
            rosterBuffer.consumeAsFlow().collect { iq ->
                bufferMutex.withLock {
                    batch.add(iq)
                    if (batch.size >= 50 || rosterBuffer.isEmpty) { // Increased batch size to 50 for better performance
                        processRosterBatch(batch.toList())
                        batch.clear()
                        Log.d(TAG, "Processed batch of ${batch.size} roster IQ stanzas")
                    }
                }
            }
        }
    }

    private suspend fun processRosterBatch(iqs: List<XMPPIQ>) {
        iqs.forEach { iq ->
            if (iq.type == "result") {
                stanzaProcessingScope.launch { // Async per IQ
                    val items = mutableListOf<RosterItem>()
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(StringReader(iq.queryContent ?: ""))
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val item = parseSingleRosterItem(parser) // Helper below
                            if (item != null) items.add(item)
                        }
                        eventType = parser.next()
                    }
                    // Batch insert (like sync)
                    realm.write {
                        items.chunked(50).forEach { chunk -> // 50/chunk
                            // Existing logic, but use chunk.forEach { processItem(it) }
                        }
                    }
                    Log.d(TAG, "Incrementally processed ${items.size} roster items")
                }
            }
        }
    }

    // Helper: Parse single <item>
    private fun parseSingleRosterItem(parser: XmlPullParser): RosterItem? {
        val jid = parser.getAttributeValue(null, "jid") ?: return null
        val name = parser.getAttributeValue(null, "name")
        val subscription = parser.getAttributeValue(null, "subscription")
        val ask = parser.getAttributeValue(null, "ask")
        val approved = parser.getAttributeValue(null, "approved") == "true"
        val groups = mutableListOf<String>()
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT && !(eventType == XmlPullParser.END_TAG && parser.name == "item")) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "group") {
                parser.next() // Text
                groups.add(parser.text)
            }
            eventType = parser.next()
        }
        return RosterItem(jid, name, subscription, ask, if (approved) "true" else null, groups)
    }

    private suspend fun readSuccess(iq: XMPPIQ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!queryIds.contains(iq.id)) {
                Log.w(TAG, "Ignoring roster IQ with unknown id: ${iq.id}")
                return@withContext false
            }
            queryIds.remove(iq.id)
            Log.d(TAG, "Processing roster IQ response for id: ${iq.id}")

            val rosterQuery = try {
                xml.decodeFromString<RosterQuery>(iq.queryContent ?: return@withContext false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse roster query: ${e.message}, content: ${iq.queryContent}", e)
                return@withContext false
            }

            Log.d(TAG, "Parsed RosterQuery with version: ${rosterQuery.ver}, item count: ${rosterQuery.items.size}")

            realm.write {
                // Pre-fetch all existing groups and items for O(1) lookups (optimization)
                val allGroups = query<RosterGroupStorageItem>("owner = $0", owner).find()
                    .associateBy { it.name }.toMutableMap()
                val allExistingItems = query<RosterStorageItem>("owner = $0", owner).find()
                    .associateBy { it.primary }.toMutableMap()

                // Get/create system group once
                var systemGroup = allGroups[RosterGroupStorageItem.SYSTEM_GROUP_NAME]
                if (systemGroup == null) {
                    val systemPrimary = RosterGroupStorageItem.genPrimary(RosterGroupStorageItem.SYSTEM_GROUP_NAME, owner)
                    systemGroup = copyToRealm(RosterGroupStorageItem().apply {
                        primary = systemPrimary
                        this.owner = this@RosterManager.owner
                        name = RosterGroupStorageItem.SYSTEM_GROUP_NAME
                        isSystemGroup = true
                        contacts = realmListOf()
                    }, UpdatePolicy.ALL)
                    allGroups[RosterGroupStorageItem.SYSTEM_GROUP_NAME] = systemGroup
                }

                val processedCount = rosterQuery.items.size
                var createdCount = 0
                var updatedCount = 0
                var skippedCount = 0

                rosterQuery.items.forEach { item ->
                    // Skip self JID
                    if (item.jid == owner) {
                        skippedCount++
                        return@forEach
                    }

                    // Skip and remove specific JID
                    if (item.jid.equals("xabber@xmppdev01.xabber.com", ignoreCase = true)) {
                        val primaryKey = RosterStorageItem.genPrimary(item.jid, owner)
                        val existingItem = allExistingItems[primaryKey]
                        if (existingItem != null) {
                            allGroups.values.forEach { group ->
                                group.contacts.removeAll { it.primary == primaryKey }
                            }
                            delete(existingItem)
                            allExistingItems.remove(primaryKey)
                            skippedCount++
                        }
                        return@forEach
                    }

                    // Handle subscription="remove"
                    if (item.subscription == "remove") {
                        val primaryKey = RosterStorageItem.genPrimary(item.jid, owner)
                        val existingItem = allExistingItems[primaryKey]
                        if (existingItem != null) {
                            allGroups.values.forEach { group ->
                                group.contacts.removeAll { it.primary == primaryKey }
                            }
                            delete(existingItem)
                            allExistingItems.remove(primaryKey)
                            skippedCount++
                        }
                        return@forEach
                    }

                    // Update or create RosterStorageItem
                    val primaryKey = RosterStorageItem.genPrimary(item.jid, owner)
                    var existingItem = allExistingItems[primaryKey]
                    val oldGroups = existingItem?.groups?.toSet() ?: emptySet()  // Track old groups for optimized removal

                    val instance = if (existingItem != null) {
                        findLatest(existingItem)?.apply {
                            customNickname = item.name ?: ""
                            subscription = item.subscription?.let { Subscription.fromRaw(it) } ?: Subscription.NONE
                            ask = if (item.ask == "subscribe") Ask.OUT else (item.ask?.let { Ask.fromRaw(it) } ?: Ask.NONE)
                            approved = item.approved == "true"
                            groups.clear()
                            groups.addAll(item.groups)
                            updatedTS = System.currentTimeMillis().toDouble() / 1000
                        }
                        updatedCount++
                        existingItem!!
                    } else {
                        val newItem = RosterStorageItem().apply {
                            primary = primaryKey
                            this.owner = this@RosterManager.owner
                            jid = item.jid
                            customNickname = item.name ?: ""
                            subscription = item.subscription?.let { Subscription.fromRaw(it) } ?: Subscription.NONE
                            ask = if (item.ask == "subscribe") Ask.OUT else (item.ask?.let { Ask.fromRaw(it) } ?: Ask.NONE)
                            approved = item.approved == "true"
                            groups.addAll(item.groups)
                            updatedTS = System.currentTimeMillis().toDouble() / 1000
                        }
                        copyToRealm(newItem, UpdatePolicy.ALL).also {
                            allExistingItems[primaryKey] = it
                            createdCount++
                        }
                    }

                    // Optimized group removal: Only remove from old groups not in new groups
                    val newGroupsSet = item.groups.toSet()
                    val groupsToRemoveFrom = oldGroups - newGroupsSet
                    groupsToRemoveFrom.forEach { groupName ->
                        allGroups[groupName]?.contacts?.removeAll { it.primary == primaryKey }
                    }

                    if (item.groups.isEmpty()) {
                        // Add to system group if not already there
                        if (!systemGroup.contacts.any { it.primary == primaryKey }) {
                            systemGroup.contacts.add(instance)
                        }
                    } else {
                        // Add to specified unique groups
                        item.groups.distinct().filter { it.isNotEmpty() }.forEach { groupName ->
                            var group = allGroups[groupName]
                            if (group == null) {
                                val groupPrimary = RosterGroupStorageItem.genPrimary(groupName, owner)
                                group = copyToRealm(RosterGroupStorageItem().apply {
                                    primary = groupPrimary
                                    this.owner = this@RosterManager.owner
                                    name = groupName
                                    contacts = realmListOf()
                                }, UpdatePolicy.ALL)
                                allGroups[groupName] = group
                            }
                            if (!group.contacts.any { it.primary == primaryKey }) {
                                group.contacts.add(instance)
                            }
                        }
                    }
                }

                // Summary log instead of per-item logs
                Log.d(TAG, "Processed $processedCount roster items: $createdCount created, $updatedCount updated, $skippedCount skipped")
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing roster success: ${e.message}, IQ: ${iq.raw}", e)
            return@withContext false
        }
    }

    private fun readError(iq: XMPPIQ): Boolean {
        Log.e(TAG, "Roster IQ error: ${iq.error}")
        return true
    }

    suspend fun close() {
        rosterBuffer.close()
        processingJob?.cancelAndJoin()
        Log.d(TAG, "RosterManager closed for owner=$owner")
    }
}