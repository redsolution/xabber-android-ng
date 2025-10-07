package com.xabber.xmpp.presence

import android.util.Log
import com.xabber.common.Socket
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.roster.Ask
import com.xabber.data_base.models.roster.Subscription
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.xmpp.device.DeviceStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
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
import kotlinx.serialization.*
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName

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

class PresenceManager(private val owner: String, private val socket: Socket) {
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val xml = XML {
        indent = 2
        autoPolymorphic = false
        defaultPolicy {
            ignoreUnknownChildren()
            pedantic = false
        }
    }
    private val presenceBuffer = Channel<Presence>(capacity = 100) // Buffer up to 100 presence stanzas
    private val bufferMutex = Mutex()
    private var processingJob: Job? = null
    private val TAG = "PresenceManager"

    init {
        scope.launch {
            startPresenceProcessing()
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
            val realm = Realm.open(defaultRealmConfig())
            val currentDevice = realm.query<DeviceStorageItem>("owner = $0", owner).first().find()
            val uid = currentDevice?.uid
            realm.close()
            if (uid.isNullOrEmpty()) {
                Log.w(TAG, "Device found but UID is empty or null for owner: $owner")
                null
            } else {
                Log.d(TAG, "Retrieved device UID: $uid for owner: $owner")
                uid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving device UID for owner: $owner: ${e.message}", e)
            null
        }
    }

    suspend fun processPresence(presenceXml: String): Boolean {
        try {
            val presence = xml.decodeFromString<Presence>(presenceXml)
            presenceBuffer.send(presence)
            Log.d(TAG, "Buffered presence stanza from ${presence.from}, thread=${Thread.currentThread().id}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse presence: ${e.message}, stanza: ${presenceXml.take(200)}", e)
            return false
        }
    }

    private suspend fun startPresenceProcessing() {
        processingJob?.cancelAndJoin()
        processingJob = scope.launch {
            val batch = mutableListOf<Presence>()
            presenceBuffer.consumeAsFlow().collect { presence ->
                bufferMutex.withLock {
                    batch.add(presence)
                    if (batch.size >= 10 || presenceBuffer.isEmpty) { // Process in batches of 10 or when buffer is empty
                        processPresenceBatch(batch.toList())
                        batch.clear()
                        Log.d(TAG, "Processed batch of ${batch.size} presence stanzas")
                    }
                }
            }
        }
    }

    private suspend fun processPresenceBatch(presences: List<Presence>) {
        if (presences.isEmpty()) return
        Log.d(TAG, "Processing batch of ${presences.size} presence stanzas, thread=${Thread.currentThread().id}")
        val realm = Realm.open(defaultRealmConfig())
        try {
            realm.writeBlocking {
                presences.forEach { presence ->
                    when (presence.type) {
                        "error" -> receiveError(presence)
                        "subscribe" -> didReceiveSubscribeRequest(presence, this)
                        "unsubscribed" -> didReceiveUnsubscribedRequest(presence, this)
                        null -> didReceiveContactPresence(presence, this)
                        else -> Log.d(TAG, "Unhandled presence type: ${presence.type}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing presence batch: ${e.message}", e)
        } finally {
            realm.close()
        }
    }

    private fun receiveError(presence: Presence) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        Log.d(TAG, "Received error presence from $jid")
    }

    private fun didReceiveSubscribeRequest(presence: Presence, realm: MutableRealm) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        Log.d(TAG, "Received subscribe request from $jid")
        val rosterItem = realm.query<RosterStorageItem>("primary = $0", RosterStorageItem.genPrimary(jid, owner)).first().find()
        if (rosterItem != null) {
            realm.findLatest(rosterItem)?.apply {
                ask_ = Ask.IN.rawValue // Use backing field
            }
        } else {
            realm.copyToRealm(RosterStorageItem().apply {
                this.primary = RosterStorageItem.genPrimary(jid, owner)
                this.owner = this@PresenceManager.owner
                this.jid = jid
                this.ask_ = Ask.IN.rawValue // Use backing field
            }, UpdatePolicy.ALL)
        }
    }

    private fun didReceiveUnsubscribedRequest(presence: Presence, realm: MutableRealm) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        Log.d(TAG, "Received unsubscribed from $jid")
        val rosterItem = realm.query<RosterStorageItem>("primary = $0", RosterStorageItem.genPrimary(jid, owner)).first().find()
        if (rosterItem != null) {
            realm.findLatest(rosterItem)?.apply {
                subscription_ = Subscription.NONE.rawValue // Use backing field
                ask_ = Ask.NONE.rawValue // Use backing field
            }
        }
    }

    private fun didReceiveContactPresence(presence: Presence, realm: MutableRealm) {
        val fromJid = presence.from?.split("/")?.get(0) ?: return
        val resource = presence.from?.split("/")?.get(1) ?: ""
        Log.d(TAG, "Received contact presence from $fromJid/$resource")
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

        val resourceItem = realm.query<ResourceStorageItem>("primary = $0", ResourceStorageItem.genPrimary(fromJid, owner, resource)).first().find()
        if (resourceItem != null) {
            realm.findLatest(resourceItem)?.apply {
                this.status = status // Use property setter
                this.statusMessage = statusMessage
                this.priority = priority
                this.timestamp = System.currentTimeMillis()
            }
        } else {
            realm.copyToRealm(ResourceStorageItem().apply {
                this.primary = ResourceStorageItem.genPrimary(fromJid, owner, resource)
                this.owner = this@PresenceManager.owner
                this.jid = fromJid
                this.resource = resource
                this.status = status // Use property setter
                this.statusMessage = statusMessage
                this.priority = priority
                this.timestamp = System.currentTimeMillis()
            }, UpdatePolicy.ALL)
        }
    }

    suspend fun close() {
        presenceBuffer.close()
        processingJob?.cancelAndJoin()
        Log.d(TAG, "PresenceManager closed for owner=$owner")
    }
}