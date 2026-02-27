package com.xabber.xmpp.presence

import android.util.Log
import com.xabber.stream.Socket
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.roster.Ask
import com.xabber.data_base.models.roster.Subscription
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader

@Serializable
@XmlSerialName("presence", "", "")
data class Presence(
    val type: String? = null,
    val from: String? = null,
    val to: String? = null,
    val show: String? = null,
    val status: String? = null,
    val priority: Int? = null
)

// Lightweight data for batched processing
data class ParsedPresence(
    val type: String?,
    val from: String?,
    val show: String?,
    val status: String?,
    val priority: Int?
)

data class ParsedGroupPresence(
    val from: String?,   // bare JID of the group
    val members: Int?,
    val present: Int?
)

class PresenceManager(private val owner: String, private val socket: Socket) {
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val xml = XML {
        indent = 2
        autoPolymorphic = false
        defaultPolicy {
            ignoreUnknownChildren()
            pedantic = false
        }
    }
    private val presenceBuffer = Channel<ParsedPresence>(capacity = 100)
    private val groupPresenceBuffer = Channel<ParsedGroupPresence>(capacity = 50)
    private val bufferMutex = Mutex()
    private var processingJob: Job? = null
    private var groupProcessingJob: Job? = null
    private val TAG = "PresenceManager"
    private val realm = Realm.open(defaultRealmConfig())

    init {
        scope.launch {
            startPresenceProcessing()
        }
        scope.launch {
            startGroupPresenceProcessing()
        }
    }

    fun sendInitialPresence() {
        val uid = getCurrentDeviceUid() ?: run {
            Log.w(TAG, "No valid device UID found for owner: $owner, using default-uid")
            "default-uid"
        }
        val stanza = """
            <presence>
                <status/>
                <device xmlns="https://xabber.com/protocol/devices" id="$uid"/>
                <priority>67</priority>
            </presence>
        """.trimIndent()
        scope.launch {
            val success = socket.write(stanza)
            Log.d(TAG, "Sent initial presence for owner $owner: success=$success")
            if (!success) {
                Log.e(TAG, "Failed to send initial presence for owner: $owner")
            }
        }
    }

    private fun getCurrentDeviceUid(): String? {
        return try {
            val currentDevice = realm.query<DeviceStorageItem>("owner = $0", owner).first().find()
            val uid = currentDevice?.uid
            if (uid.isNullOrEmpty()) {
                Log.w(TAG, "Device found but UID is empty or null for owner: $owner")
                null
            } else {
                uid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving device UID for owner: $owner: ${e.message}", e)
            null
        }
    }

    suspend fun processPresence(presenceXml: String): Boolean {
        try {
            // Group presence: parse members/present counts instead of skipping
            if (presenceXml.contains("https://xabber.com/protocol/groups")) {
                return parseAndEnqueueGroupPresence(presenceXml)
            }

            // Incremental parse with XmlPullParser
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(presenceXml))
            var eventType = parser.eventType
            var type: String? = null
            var from: String? = null
            var to: String? = null
            var show: String? = null
            var status: String? = null
            var priority: Int? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        if (tagName == "presence") {
                            type = parser.getAttributeValue(null, "type")
                            from = parser.getAttributeValue(null, "from")
                            to = parser.getAttributeValue(null, "to")
                        } else if (tagName == "show") {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                show = parser.text
                            }
                        } else if (tagName == "status") {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                status = parser.text
                            }
                        } else if (tagName == "priority") {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                priority = parser.text?.toIntOrNull()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            val parsedPresence = ParsedPresence(type, from, show, status, priority)
            presenceBuffer.send(parsedPresence)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse presence: ${e.message}, stanza: ${presenceXml.take(200)}", e)
            return false
        }
    }

    private suspend fun startPresenceProcessing() {
        processingJob?.cancelAndJoin()
        processingJob = scope.launch {
            val batch = mutableListOf<ParsedPresence>()
            presenceBuffer.consumeAsFlow().collect { presence ->
                bufferMutex.withLock {
                    batch.add(presence)
                    if (batch.size >= 10 || presenceBuffer.isEmpty) { // Reduced batch size from 50 to 10
                        processPresenceBatch(batch.toList())
                        batch.clear()
                        Log.d(TAG, "Processed batch of ${batch.size} presence stanzas")
                    }
                }
            }
        }
    }

    private suspend fun processPresenceBatch(presences: List<ParsedPresence>) {
        if (presences.isEmpty()) return
        val startTime = System.currentTimeMillis()
        var contactPresenceCount = 0
        try {
            val rosterItems = realm.query<RosterStorageItem>("owner = $0", owner).find().associateBy { RosterStorageItem.genPrimary(it.jid, owner) }
            val resourceItems = realm.query<ResourceStorageItem>("owner = $0", owner).find().associateBy { it.primary }

            // Process in smaller chunks to avoid blocking
            presences.chunked(20).forEach { chunk ->
                realm.write {
                    chunk.forEach { presence ->
                        // Skip group-related presence aggressively
                        if (presence.from?.contains("/Group") == true || presence.from?.contains("https://xabber.com/protocol/groups") == true) {
                            return@forEach
                        }
                        when (presence.type) {
                            "subscribe" -> didReceiveSubscribeRequest(presence, this, rosterItems)
                            "unsubscribed" -> didReceiveUnsubscribedRequest(presence, this, rosterItems)
                            "error" -> {} // Skip errors to reduce logging
                            null -> {
                                contactPresenceCount++
                                didReceiveContactPresence(presence, this, resourceItems)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing presence batch: ${e.message}")
        }
        Log.d(TAG, "Processed batch of ${presences.size} presence stanzas in ${System.currentTimeMillis() - startTime}ms: $contactPresenceCount contact presences")
    }

    private fun didReceiveSubscribeRequest(presence: ParsedPresence, realm: MutableRealm, rosterItems: Map<String, RosterStorageItem>) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        val primaryKey = RosterStorageItem.genPrimary(jid, owner)
        val rosterItem = rosterItems[primaryKey]
        if (rosterItem != null) {
            realm.findLatest(rosterItem)?.apply {
                ask_ = Ask.IN.rawValue
            }
        } else {
            realm.copyToRealm(RosterStorageItem().apply {
                this.primary = primaryKey
                this.owner = this@PresenceManager.owner
                this.jid = jid
                this.ask_ = Ask.IN.rawValue
            }, UpdatePolicy.ALL)
        }
    }

    private fun didReceiveUnsubscribedRequest(presence: ParsedPresence, realm: MutableRealm, rosterItems: Map<String, RosterStorageItem>) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        val primaryKey = RosterStorageItem.genPrimary(jid, owner)
        val rosterItem = rosterItems[primaryKey]
        if (rosterItem != null) {
            realm.findLatest(rosterItem)?.apply {
                subscription_ = Subscription.NONE.rawValue
                ask_ = Ask.NONE.rawValue
            }
        }
    }

    private fun didReceiveContactPresence(presence: ParsedPresence, realm: MutableRealm, resourceItems: Map<String, ResourceStorageItem>) {
        val fromJid = presence.from?.split("/")?.get(0) ?: return
        val resource = presence.from?.split("/")?.get(1) ?: ""
        val status = when (presence.show) {
            "xa" -> ResourceStatus.XA
            "away" -> ResourceStatus.AWAY
            "dnd" -> ResourceStatus.DND
            "chat" -> ResourceStatus.CHAT
            null -> if (presence.type == "unavailable") ResourceStatus.OFFLINE else ResourceStatus.ONLINE
            else -> ResourceStatus.OFFLINE
        }
        val statusMessage = presence.status ?: ""
        val priority = presence.priority ?: 0
        val primaryKey = ResourceStorageItem.genPrimary(fromJid, owner, resource)

        val resourceItem = resourceItems[primaryKey]
        if (resourceItem != null) {
            realm.findLatest(resourceItem)?.apply {
                this.status = status
                this.statusMessage = statusMessage
                this.priority = priority
                this.timestamp = System.currentTimeMillis()
            }
        } else {
            realm.copyToRealm(ResourceStorageItem().apply {
                this.primary = primaryKey
                this.owner = this@PresenceManager.owner
                this.jid = fromJid
                this.resource = resource
                this.status = status
                this.statusMessage = statusMessage
                this.priority = priority
                this.timestamp = System.currentTimeMillis()
            }, UpdatePolicy.ALL)
        }
    }

    private suspend fun parseAndEnqueueGroupPresence(presenceXml: String): Boolean {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(presenceXml))
            var eventType = parser.eventType
            var from: String? = null
            var members: Int? = null
            var present: Int? = null
            var inGroupsX = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "presence" -> from = parser.getAttributeValue(null, "from")?.split("/")?.get(0)
                        "x" -> if (parser.namespace == "https://xabber.com/protocol/groups") inGroupsX = true
                        "members" -> if (inGroupsX) {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) members = parser.text?.toIntOrNull()
                        }
                        "present" -> if (inGroupsX) {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) present = parser.text?.toIntOrNull()
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "x") inGroupsX = false
                }
                eventType = parser.next()
            }

            // Only enqueue if we got at least one useful field
            if (from != null && (members != null || present != null)) {
                groupPresenceBuffer.send(ParsedGroupPresence(from, members, present))
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse group presence: ${e.message}", e)
            return false
        }
    }

    private suspend fun startGroupPresenceProcessing() {
        groupProcessingJob?.cancelAndJoin()
        groupProcessingJob = scope.launch {
            val batch = mutableListOf<ParsedGroupPresence>()
            groupPresenceBuffer.consumeAsFlow().collect { presence ->
                batch.add(presence)
                if (batch.size >= 10 || groupPresenceBuffer.isEmpty) {
                    processGroupPresenceBatch(batch.toList())
                    batch.clear()
                }
            }
        }
    }

    private suspend fun processGroupPresenceBatch(presences: List<ParsedGroupPresence>) {
        if (presences.isEmpty()) return
        try {
            val groupItems = realm.query<GroupChatStorageItem>("owner = $0", owner).find()
                .associateBy { it.jid }
            realm.write {
                presences.forEach { presence ->
                    val from = presence.from ?: return@forEach
                    val groupItem = groupItems[from] ?: return@forEach
                    findLatest(groupItem)?.apply {
                        presence.members?.let { members = it }
                        presence.present?.let { present = it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing group presence batch: ${e.message}")
        }
    }

    suspend fun close() {
        presenceBuffer.close()
        groupPresenceBuffer.close()
        processingJob?.cancelAndJoin()
        groupProcessingJob?.cancelAndJoin()
        stanzaProcessingScope.cancel()
        realm.close()
        Log.d(TAG, "PresenceManager closed for owner=$owner")
    }
}