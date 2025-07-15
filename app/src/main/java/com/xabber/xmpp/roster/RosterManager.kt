package com.xabber.xmpp.roster

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.Stream
import com.xabber.common.XMPPIQ
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.roster.Subscription
import com.xabber.data_base.models.roster.Ask
import com.xabber.data_base.models.presences.ResourceStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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

    suspend fun read(iq: XMPPIQ): Boolean {
        try {
            if (iq.queryNamespace != "jabber:iq:roster") return false  // Quick check

            Log.d(TAG, "Processing complete roster IQ: ${iq.raw}")
            return when {
                iq.error != null -> readError(iq)  // Adapt readError to use iq.error
                iq.type == "result" -> readSuccess(iq)  // Adapt to use iq
                else -> {
                    Log.w(TAG, "Unhandled roster IQ type: ${iq.type}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading IQ: ${e.message}, IQ: ${iq.raw}", e)
            return false
        }
    }

    private suspend fun readSuccess(iq: XMPPIQ): Boolean {
        val rosterQuery = try {
            xml.decodeFromString<RosterQuery>(iq.queryContent ?: return false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse roster query: ${e.message}, content: ${iq.queryContent}", e)
            return false
        }

        Log.d(TAG, "Parsed RosterQuery with version: ${rosterQuery.ver}")
        realm.write {
            // Fetch all groups once and map by name for O(1) lookups
            val allGroups = query<RosterGroupStorageItem>("owner = $0", owner).find()
                .associateBy { it.name }.toMutableMap()

            // Get/create system group
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

            rosterQuery.items.forEach { item ->
                // Skip self JID
                if (item.jid == owner) {
                    Log.d(TAG, "Skipping self JID: ${item.jid}")
                    return@forEach
                }

                // Skip specific JID and remove if exists
                if (item.jid.equals("xabber@xmppdev01.xabber.com", ignoreCase = true)) {
                    val primaryKey = RosterStorageItem.genPrimary(item.jid, owner)
                    val existingItem = query<RosterStorageItem>("primary = $0", primaryKey).first().find()
                    if (existingItem != null) {
                        allGroups.values.forEach { group ->
                            group.contacts.removeAll { it.primary == existingItem.primary }
                        }
                        delete(existingItem)
                        Log.d(TAG, "Removed skipped RosterStorageItem for JID: ${item.jid}")
                    }
                    return@forEach
                }

                // Handle subscription="remove"
                if (item.subscription == "remove") {
                    val primaryKey = RosterStorageItem.genPrimary(item.jid, owner)
                    val existingItem = query<RosterStorageItem>("primary = $0", primaryKey).first().find()
                    if (existingItem != null) {
                        allGroups.values.forEach { group ->
                            group.contacts.removeAll { it.primary == existingItem.primary }
                        }
                        delete(existingItem)
                        Log.d(TAG, "Deleted RosterStorageItem for JID: ${item.jid} due to subscription='remove'")
                    }
                    return@forEach
                }

                // Update or create RosterStorageItem
                val primaryKey = RosterStorageItem.genPrimary(item.jid, owner)
                val existingItem = query<RosterStorageItem>("primary = $0", primaryKey).first().find()
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
                    Log.d(TAG, "Updated RosterStorageItem for JID: ${item.jid}")
                    existingItem!!
                } else {
                    copyToRealm(RosterStorageItem().apply {
                        primary = primaryKey
                        this.owner = this@RosterManager.owner
                        jid = item.jid
                        customNickname = item.name ?: ""
                        subscription = item.subscription?.let { Subscription.fromRaw(it) } ?: Subscription.NONE
                        ask = if (item.ask == "subscribe") Ask.OUT else (item.ask?.let { Ask.fromRaw(it) } ?: Ask.NONE)
                        approved = item.approved == "true"
                        groups.addAll(item.groups)
                        updatedTS = System.currentTimeMillis().toDouble() / 1000
                    }, UpdatePolicy.ALL).also {
                        Log.d(TAG, "Created new RosterStorageItem for JID: ${item.jid}")
                    }
                }

                // Remove from all groups first (including system)
                allGroups.values.forEach { group ->
                    group.contacts.removeAll { it.primary == instance.primary }
                }

                if (item.groups.isEmpty()) {
                    // Add to system group if not already there
                    if (!systemGroup.contacts.any { it.primary == instance.primary }) {
                        systemGroup.contacts.add(instance)
                    }
                    Log.d(TAG, "Added JID ${instance.jid} to system group")
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
                            Log.d(TAG, "Created new group: $groupName")
                        }
                        if (!group.contacts.any { it.primary == instance.primary }) {
                            group.contacts.add(instance)
                        }
                        Log.d(TAG, "Added JID ${instance.jid} to group: $groupName")
                    }
                }
            }
        }
        return true
    }

    private fun readError(iq: XMPPIQ): Boolean {
        // Adapt to use iq.error or iq.raw
        Log.e(TAG, "Roster IQ error: ${iq.error}")
        return true
    }

    private fun readResponse(iq: String): Boolean {
        val elementIdMatch = Regex("""id=['"]([^'"]+)['"]""").find(iq)
        val elementId = elementIdMatch?.groupValues?.get(1) ?: return false
        if (!queryIds.contains(elementId)) return false

        queryIds.remove(elementId)
        Log.d(TAG, "Roster IQ response processed for id: $elementId")
        return true
    }
}