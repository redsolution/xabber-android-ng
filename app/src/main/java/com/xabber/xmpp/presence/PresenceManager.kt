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
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val scope = CoroutineScope(Dispatchers.IO)
    private val xml = XML {
        indent = 2
        autoPolymorphic = false
        defaultPolicy {
            ignoreUnknownChildren()
            pedantic = false
        }
    }

    fun sendInitialPresence() {
        val uid = getCurrentDeviceUid() ?: run {
            Log.w("PresenceManager", "No valid device UID found for owner: $owner, using default-uid")
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
            Log.d("PresenceManager", "Sent initial presence for owner $owner: success=$success")
            if (!success) {
                Log.e("PresenceManager", "Failed to send initial presence for owner: $owner")
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
                Log.w("PresenceManager", "Device found but UID is empty or null for owner: $owner")
                null
            } else {
                Log.d("PresenceManager", "Retrieved device UID: $uid for owner: $owner")
                uid
            }
        } catch (e: Exception) {
            Log.e("PresenceManager", "Error retrieving device UID for owner: $owner: ${e.message}", e)
            null
        }
    }

    fun processPresence(presenceXml: String): Boolean {
        try {
            val presence = xml.decodeFromString<Presence>(presenceXml)
            when (presence.type) {
                "error" -> receiveError(presence)
                "subscribe" -> didReceiveSubscribeRequest(presence)
                "unsubscribed" -> didReceiveUnsubscribedRequest(presence)
                null -> didReceiveContactPresence(presence)
                else -> Log.d("PresenceManager", "Unhandled presence type: ${presence.type}")
            }
            return true
        } catch (e: Exception) {
            Log.e("PresenceManager", "Failed to parse/process presence: ${e.message}, stanza: ${presenceXml.take(200)}", e)
            return false
        }
    }

    private fun receiveError(presence: Presence) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        Log.d("PresenceManager", "Received error presence from $jid")
    }

    private fun didReceiveSubscribeRequest(presence: Presence) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        Log.d("PresenceManager", "Received subscribe request from $jid")
        val realm = Realm.open(defaultRealmConfig())
        try {
            val rosterItem = realm.query<RosterStorageItem>("primary = $0", RosterStorageItem.genPrimary(jid, owner)).first().find()
            realm.writeBlocking {
                if (rosterItem != null) {
                    findLatest(rosterItem)?.ask = Ask.IN
                } else {
                    val newItem = RosterStorageItem().apply {
                        this.primary = RosterStorageItem.genPrimary(jid, owner)
                        this.owner = this@PresenceManager.owner
                        this.jid = jid
                        this.ask = Ask.IN
                    }
                    copyToRealm(newItem, UpdatePolicy.ALL)
                }
            }
        } catch (e: Exception) {
            Log.e("PresenceManager", "Error processing subscribe request for $jid: ${e.message}", e)
        } finally {
            realm.close()
        }
    }

    private fun didReceiveUnsubscribedRequest(presence: Presence) {
        val jid = presence.from?.split("/")?.get(0) ?: return
        Log.d("PresenceManager", "Received unsubscribed from $jid")
        val realm = Realm.open(defaultRealmConfig())
        try {
            val rosterItem = realm.query<RosterStorageItem>("primary = $0", RosterStorageItem.genPrimary(jid, owner)).first().find()
            realm.writeBlocking {
                if (rosterItem != null) {
                    findLatest(rosterItem)?.apply {
                        subscription = Subscription.NONE
                        ask = Ask.NONE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PresenceManager", "Error processing unsubscribed request for $jid: ${e.message}", e)
        } finally {
            realm.close()
        }
    }

    private fun didReceiveContactPresence(presence: Presence) {
        val fromJid = presence.from?.split("/")?.get(0) ?: return
        val resource = presence.from?.split("/")?.get(1) ?: ""
        Log.d("PresenceManager", "Received contact presence from $fromJid/$resource")
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

        val realm = Realm.open(defaultRealmConfig())
        try {
            val resourceItem = realm.query<ResourceStorageItem>("primary = $0", ResourceStorageItem.genPrimary(fromJid, owner, resource)).first().find()
            realm.writeBlocking {
                if (resourceItem != null) {
                    findLatest(resourceItem)?.apply {
                        this.status = status
                        this.statusMessage = statusMessage
                        this.priority = priority
                        this.timestamp = System.currentTimeMillis()
                    }
                } else {
                    val newItem = ResourceStorageItem().apply {
                        this.primary = ResourceStorageItem.genPrimary(fromJid, owner, resource)
                        this.owner = this@PresenceManager.owner
                        this.jid = fromJid
                        this.resource = resource
                        this.status = status
                        this.statusMessage = statusMessage
                        this.priority = priority
                        this.timestamp = System.currentTimeMillis()
                    }
                    copyToRealm(newItem, UpdatePolicy.ALL)
                }
            }
        } catch (e: Exception) {
            Log.e("PresenceManager", "Error processing contact presence for $fromJid/$resource: ${e.message}", e)
        } finally {
            realm.close()
        }
    }
}