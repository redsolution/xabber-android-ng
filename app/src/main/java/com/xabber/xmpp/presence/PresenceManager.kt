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
import com.xabber.common.XMPPPresence
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PresenceManager(private val owner: String, private val socket: Socket) {
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val presenceBuffer = Channel<XMPPPresence>(capacity = 100)
    private val bufferMutex = Mutex()
    private var processingJob: Job? = null
    private val TAG = "PresenceManager"
    private val realm = Realm.open(defaultRealmConfig())

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
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sent initial presence for owner $owner: success=$success")
            }
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

    suspend fun processPresence(presence: XMPPPresence): Boolean {
        try {
            if (presence.from?.contains("/Group") == true) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Skipping group-related presence stanza: id=${presence.id}, from=${presence.from}")
                }
                return true
            }

            presenceBuffer.send(presence)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sent presence to buffer: id=${presence.id}, from=${presence.from}")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process presence: ${e.message}, id=${presence.id}, from=${presence.from}", e)
            return false
        }
    }

    private suspend fun startPresenceProcessing() {
        processingJob?.cancelAndJoin()
        processingJob = scope.launch {
            val batch = mutableListOf<XMPPPresence>()
            presenceBuffer.consumeAsFlow().collect { presence ->
                bufferMutex.withLock {
                    batch.add(presence)
                    if (batch.size >= 10 || presenceBuffer.isEmpty) {
                        processPresenceBatch(batch.toList())
                        batch.clear()
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Processed batch of ${batch.size} presence stanzas")
                        }
                        delay(20) // Debounce to prevent rapid processing
                    }
                }
            }
        }
    }

    private suspend fun processPresenceBatch(presences: List<XMPPPresence>) {
        if (presences.isEmpty()) return
        val startTime = System.currentTimeMillis()
        var contactPresenceCount = 0
        try {
            val rosterItems = realm.query<RosterStorageItem>("owner = $0", owner).find().associateBy { RosterStorageItem.genPrimary(it.jid, owner) }
            val resourceItems = realm.query<ResourceStorageItem>("owner = $0", owner).find().associateBy { it.primary }

            presences.chunked(20).forEach { chunk ->
                realm.writeBlocking {
                    chunk.forEach { presence ->
                        if (presence.from?.contains("/Group") == true) {
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
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Processed batch of ${presences.size} presence stanzas in ${System.currentTimeMillis() - startTime}ms: $contactPresenceCount contact presences")
        }
    }

    private fun didReceiveSubscribeRequest(presence: XMPPPresence, realm: MutableRealm, rosterItems: Map<String, RosterStorageItem>) {
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

    private fun didReceiveUnsubscribedRequest(presence: XMPPPresence, realm: MutableRealm, rosterItems: Map<String, RosterStorageItem>) {
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

    private fun didReceiveContactPresence(presence: XMPPPresence, realm: MutableRealm, resourceItems: Map<String, ResourceStorageItem>) {
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
                this.deviceId = presence.deviceId.toString() // Nullable, safe to assign
                this.timestamp = presence.timestamp // Store presence-specific timestamp
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
                this.deviceId = presence.deviceId.toString()
                this.timestamp = presence.timestamp
            }, UpdatePolicy.ALL)
        }
    }

    suspend fun close() {
        presenceBuffer.close()
        processingJob?.cancelAndJoin()
        stanzaProcessingScope.cancel()
        realm.close()
        Log.d(TAG, "PresenceManager closed for owner=$owner")
    }
}