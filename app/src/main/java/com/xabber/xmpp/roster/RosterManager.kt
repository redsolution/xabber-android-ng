package com.xabber.xmpp.roster

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.Account
import com.xabber.common.AccountManager
import com.xabber.common.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.avatar.AvatarStorageItem
import com.xabber.data_base.models.last_chats.ConversationType
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.utils.prp
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
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
class RosterManager(private val owner: String) {
    private val TAG = "RosterManager"
    private val realm = Realm.open(defaultRealmConfig())
    private var version: String? = null
    private val queueItems = SynchronizedArray<QueueItem>()
    private var isInitialRosterReceived = false
    private val queryIds = ConcurrentHashMap.newKeySet<String>()
    private val xml = XML {
        indent = 2
        autoPolymorphic = false
        defaultPolicy {
            ignoreUnknownChildren()
            pedantic = false
        }
    }

    data class QueueItem(
        val action: Action,
        val elementId: String,
        val value: String,
        val callback: ((value: String?, error: String?, success: Boolean) -> Unit)? = null
    ) {
        enum class Action { ADD, DELETE }

        override fun equals(other: Any?): Boolean = other is QueueItem && elementId == other.elementId
        override fun hashCode(): Int = elementId.hashCode()
    }

    init {
        // Load roster version from storage (equivalent to SettingManager in Swift)
        version = SettingManager.getKey(owner, scope = "roster", key = "version")
        Log.d(TAG, "Initialized RosterManager for owner: $owner, version: $version")
    }

    suspend fun request(stream: Stream) = withContext(Dispatchers.IO) {
        try {
            isInitialRosterReceived = false
            val elementId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val query = """
                <query xmlns='jabber:iq:roster' ver='${version ?: ""}'/>
            """.trimIndent()
            val iq = """
                <iq type='get' id='$elementId'>$query</iq>
            """.trimIndent()
            stream.getSocket()?.write(iq)?.also { success ->
                if (success) {
                    queryIds.add(elementId)
                    Log.d(TAG, "Sent roster request IQ with id: $elementId, version: $version")
                    AccountManager.find(owner)?.didReceiveRoster()
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

    suspend fun setContact(
        stream: Stream,
        jid: String,
        getNickFromVCard: Boolean = false,
        preferredNickname: String? = null,
        groups: List<String> = emptyList(),
        shouldAddSystemMessage: Boolean = false,
        callback: ((value: String?, error: String?, success: Boolean) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val elementId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            var nickname = preferredNickname
            if (getNickFromVCard) {
                realm.query<AvatarStorageItem>("primary = $0", jid).first().find()?.let { vcard ->
                    nickname = vcard.nickname // Assuming AvatarStorageItem has a nickname field
                }
            }
            val groupXml = groups.filter { it.isNotEmpty() }.joinToString("") { "<group>$it</group>" }
            val nameAttr = if (nickname != null) " name='$nickname'" else ""
            val query = """
                <query xmlns='jabber:iq:roster'>
                    <item jid='$jid'$nameAttr>$groupXml</item>
                </query>
            """.trimIndent()
            val iq = """
                <iq type='set' id='$elementId'>$query</iq>
            """.trimIndent()
            if (stream.getSocket()?.write(iq) == true) {
                queryIds.add(elementId)
                queueItems.insert(QueueItem(QueueItem.Action.ADD, elementId, jid, callback))
                Log.d(TAG, "Sent set contact IQ for jid: $jid, id: $elementId")

                realm.write {
                    val primary = RosterStorageItem.genPrimary(jid, owner)
                    val instance = query<RosterStorageItem>("primary = $0", primary).first().find()
                    if (instance != null) {
                        copyToRealm(findLatest(instance)!!.apply {
                            subscription = com.xabber.data_base.models.roster.Subscription.NONE
                        })
                    } else {
                        copyToRealm(RosterStorageItem().apply {
                            this.jid = jid
                            this.owner = owner
                            this.primary = primary
                            this.subscription = com.xabber.data_base.models.roster.Subscription.NONE
                            this.associatedLastChat = query<LastChatsStorageItem>(
                                "primary = $0",
                                LastChatsStorageItem.genPrimary(jid, owner, ConversationType.REGULAR)
                            ).first().find()
                        })
                    }
                }
            } else {
                Log.e(TAG, "Failed to send set contact IQ for jid: $jid")
                callback?.invoke(jid, "Failed to send IQ", false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting contact $jid: ${e.message}", e)
            callback?.invoke(jid, e.message, false)
        }
    }

    suspend fun removeContact(
        stream: Stream,
        jid: String,
        callback: ((value: String?, error: String?, success: Boolean) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val elementId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val query = """
                <query xmlns='jabber:iq:roster'>
                    <item jid='$jid' subscription='remove'/>
                </query>
            """.trimIndent()
            val iq = """
                <iq type='set' id='$elementId'>$query</iq>
            """.trimIndent()
            if (stream.getSocket()?.write(iq) == true) {
                queryIds.add(elementId)
                queueItems.insert(QueueItem(QueueItem.Action.DELETE, elementId, jid, callback))
                Log.d(TAG, "Sent remove contact IQ for jid: $jid, id: $elementId")

                realm.write {
                    val primary = RosterStorageItem.genPrimary(jid, owner)
                    val instance = query<RosterStorageItem>("primary = $0", primary).first().find()
                    instance?.let {
                        copyToRealm(findLatest(it)!!.apply {
                            subscription = com.xabber.data_base.models.roster.Subscription.UNDEFINED
                            ask = com.xabber.data_base.models.roster.Ask.NONE
                        })
                    }
                }
            } else {
                Log.e(TAG, "Failed to send remove contact IQ for jid: $jid")
                callback?.invoke(jid, "Failed to send IQ", false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing contact $jid: ${e.message}", e)
            callback?.invoke(jid, e.message, false)
        }
    }

    suspend fun read(iq: String): Boolean {
        try {
            when {
                readError(iq) -> return true
                readSuccess(iq) -> return true
                readResponse(iq) -> return true
                else -> return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading IQ: ${e.message}, IQ: $iq", e)
            return false
        }
    }

    private suspend fun readSuccess(iq: String): Boolean {
        val elementIdMatch = Regex("""id=['"]([^'"]+)['"]""").find(iq)
        val elementId = elementIdMatch?.groupValues?.get(1) ?: return false
        if (!queryIds.contains(elementId)) return false

        val queryMatch = Regex("""<query[^>]*xmlns=['"]jabber:iq:roster['"][^>]*>(.*?)</query>""", RegexOption.DOT_MATCHES_ALL).find(iq)
        val queryContent = queryMatch?.groupValues?.get(1) ?: return false
        val typeMatch = Regex("""type=['"]([^'"]+)['"]""").find(iq)
        val iqType = typeMatch?.groupValues?.get(1)

        queryIds.remove(elementId)

//        if (iqType == "set") {
//            AccountManager.find(owner)?.action { user, stream ->
//                user.presence() // Trigger presence update
//            }
//        }

        realm.write {
            val rosterQuery = try {
                xml.decodeFromString<RosterQuery>("<query xmlns='jabber:iq:roster'>$queryContent</query>")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse roster query: ${e.message}, query: $queryContent", e)
                return@write
            }

            rosterQuery.items.forEach { item ->
                var jid = item.jid
                var subscription = item.subscription ?: "none"
                if (subscription == "remove") {
                    val primary = RosterStorageItem.genPrimary(jid, owner)
                    val instance = query<RosterStorageItem>("primary = $0", primary).first().find()
                    instance?.let { rosterItem ->
                        findLatest(rosterItem)?.let { latest ->
                            query<RosterGroupStorageItem>("owner = $0 AND name IN $1", owner, latest.groups.toRealmList())
                                .find().forEach { group ->
                                    val index = group.contacts.indexOfFirst { it.primary == primary }
                                    if (index >= 0) group.contacts.removeAt(index)
                                }
                            query<RosterGroupStorageItem>("primary = $0", RosterGroupStorageItem.genPrimary(RosterGroupStorageItem.SYSTEM_GROUP_NAME, owner))
                                .first().find()?.let { systemGroup ->
                                    val index = systemGroup.contacts.indexOfFirst { it.primary == primary }
                                    if (index >= 0) systemGroup.contacts.removeAt(index)
                                }
                            latest.associatedLastChat?.rosterItem = null
                            delete(latest)
                        }
                    }
                    // Handle group chat deletion or marking as deleted
                    query<GroupChatStorageItem>("primary = $0", listOf(jid, owner).prp()).first().find()?.let { groupChat ->
                        copyToRealm(findLatest(groupChat)!!.apply { isDeleted = true })
                    }
                    // Clean up authorization message
                    val authMessageId = MessageStorageItem.genPrimary(
                        messageId = MessageStorageItem.messageIdForAuthRequest(jid),
                        owner = owner
                    )
                    query<MessageStorageItem>("primary = $0", authMessageId).first().find()?.let { authMessage ->
                        val lastMessage = query<MessageStorageItem>(
                            "owner = $0 AND opponent = $1 AND isDeleted = false AND conversationType_ = $2",
                            owner, jid, "omemo"
                        ).find().sortedBy { it.date }.lastOrNull()
                        delete(authMessage)
                        query<LastChatsStorageItem>(
                            "primary = $0",
                            LastChatsStorageItem.genPrimary(jid, owner, ConversationType.OMEMO)
                        ).first().find()?.let { lastChat ->
                            copyToRealm(findLatest(lastChat)!!.apply { this.lastMessage = lastMessage })
                        }
                    }
                } else {
                    val primary = RosterStorageItem.genPrimary(jid, owner)
                    val instance = query<RosterStorageItem>("primary = $0", primary).first().find()
                    val groups = item.groups
                    if (instance != null) {
                        copyToRealm(findLatest(instance)!!.apply {
                            owner = this@RosterManager.owner
                            customNickname = item.name ?: ""
                            subscription = com.xabber.data_base.models.roster.Subscription.fromRaw(subscription)
                                .toString()
                            ask = if (item.ask == "subscribe") com.xabber.data_base.models.roster.Ask.OUT else com.xabber.data_base.models.roster.Ask.NONE
                            approved = item.approved == "true"
                            this.groups.clear()
                            this.groups.addAll(groups.toRealmList())
                        })
                    } else {
                        copyToRealm(RosterStorageItem().apply {
                            owner = this@RosterManager.owner
                            jid = jid
                            this.primary = primary
                            customNickname = item.name ?: ""
                            subscription = com.xabber.data_base.models.roster.Subscription.fromRaw(subscription)
                                .toString()
                            ask = if (item.ask == "subscribe") com.xabber.data_base.models.roster.Ask.OUT else com.xabber.data_base.models.roster.Ask.NONE
                            approved = item.approved == "true"
                            this.groups.addAll(groups.toRealmList())
                        })
                    }
                    updateGroups(instance ?: query<RosterStorageItem>("primary = $0", primary).first().find()!!, groups)
                    val isGroupchat = query<GroupChatStorageItem>("primary = $0", listOf(jid, owner).prp()).first().find() != null
                    if (isGroupchat) {
                        query<GroupChatStorageItem>("primary = $0", listOf(jid, owner).prp()).first().find()?.let { groupChat ->
                            copyToRealm(findLatest(groupChat)!!.apply { isDeleted = false })
                        }
                    }
                    query<LastChatsStorageItem>("owner = $0 AND jid = $1", owner, jid).find().forEach { lastChat ->
                        copyToRealm(findLatest(lastChat)!!.apply { rosterItem = instance })
                    }
                    // Update contact metadata (simulating CommonContactsMetadataManager)
                    CommonContactsMetadataManager.update(owner, jid, instance?.displayName, instance?.avatarUrl)
                }
            }

            queueItems.firstOrNull { it.elementId == elementId }?.let { item ->
                item.callback?.invoke(item.value, null, true)
                queueItems.remove(item)
            }

            if (iqType == "set") return@write
            rosterQuery.ver?.let { newVersion ->
                version = newVersion
                SettingManager.saveItem(owner, scope = "roster", key = "version", value = newVersion)
            }
        }

        return true
    }

    private suspend fun updateGroups(instance: RosterStorageItem, groups: List<String>) {
        realm.write {
            // Remove from not-in-roster group
            query<RosterGroupStorageItem>("primary = $0", RosterGroupStorageItem.genPrimary(RosterGroupStorageItem.NOT_IN_ROSTER_GROUP_NAME, owner))
                .first().find()?.let { notInGroup ->
                    val index = notInGroup.contacts.indexOfFirst { it.primary == instance.primary }
                    if (index >= 0) notInGroup.contacts.removeAt(index)
                }

            if (groups.isEmpty()) {
                // Remove from all groups
                query<RosterGroupStorageItem>("owner = $0", owner).find().forEach { group ->
                    val index = group.contacts.indexOfFirst { it.primary == instance.primary }
                    if (index >= 0) group.contacts.removeAt(index)
                }
                // Add to system group
                val systemGroupPrimary = RosterGroupStorageItem.genPrimary(RosterGroupStorageItem.SYSTEM_GROUP_NAME, owner)
                query<RosterGroupStorageItem>("primary = $0", systemGroupPrimary).first().find()?.let { group ->
                    if (!group.contacts.contains(instance)) {
                        copyToRealm(findLatest(group)!!.apply { contacts.add(instance) })
                    }
                } ?: run {
                    copyToRealm(RosterGroupStorageItem().apply {
                        isSystemGroup = true
                        name = RosterGroupStorageItem.SYSTEM_GROUP_NAME
                        owner = this@RosterManager.owner
                        primary = systemGroupPrimary
                        contacts.add(instance)
                    })
                }
            } else {
                // Remove from system group
                query<RosterGroupStorageItem>("primary = $0", RosterGroupStorageItem.genPrimary(RosterGroupStorageItem.SYSTEM_GROUP_NAME, owner))
                    .first().find()?.let { systemGroup ->
                        val index = systemGroup.contacts.indexOfFirst { it.primary == instance.primary }
                        if (index >= 0) systemGroup.contacts.removeAt(index)
                    }
                groups.forEach { groupName ->
                    // Remove from other groups
                    query<RosterGroupStorageItem>("owner = $0", owner).find().forEach { group ->
                        if (group.name != groupName) {
                            val index = group.contacts.indexOfFirst { it.primary == instance.primary }
                            if (index >= 0) group.contacts.removeAt(index)
                        }
                    }
                    // Add to specified group
                    val groupPrimary = RosterGroupStorageItem.genPrimary(groupName, owner)
                    query<RosterGroupStorageItem>("primary = $0", groupPrimary).first().find()?.let { group ->
                        if (!group.contacts.contains(instance)) {
                            copyToRealm(findLatest(group)!!.apply { contacts.add(instance) })
                        }
                    } ?: run {
                        copyToRealm(RosterGroupStorageItem().apply {
                            name = groupName
                            owner = this@RosterManager.owner
                            primary = groupPrimary
                            contacts.add(instance)
                        })
                    }
                }
            }
        }
    }

    private fun readError(iq: String): Boolean {
        val errorMatch = Regex("""<error[^>]*>""").find(iq) ?: return false
        val elementIdMatch = Regex("""id=['"]([^'"]+)['"]""").find(iq)
        val elementId = elementIdMatch?.groupValues?.get(1) ?: return false
        if (!queryIds.contains(elementId)) return false

        queryIds.remove(elementId)
        queueItems.firstOrNull { it.elementId == elementId }?.let { item ->
            item.callback?.invoke(item.value, errorMatch.value, false)
            queueItems.remove(item)
        }
        return true
    }

    private fun readResponse(iq: String): Boolean {
        val elementIdMatch = Regex("""id=['"]([^'"]+)['"]""").find(iq)
        val elementId = elementIdMatch?.groupValues?.get(1) ?: return false
        if (!queryIds.contains(elementId)) return false

        queueItems.firstOrNull { it.elementId == elementId }?.let { item ->
            item.callback?.invoke(item.value, null, true)
            queueItems.remove(item)
        }
        return true
    }

    suspend fun removeForOwner(owner: String, commitTransaction: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            SettingManager.removeItem(owner, scope = "roster", key = "version")
            realm.write {
                val items = query<RosterStorageItem>("owner = $0", owner).find()
                val groups = query<RosterGroupStorageItem>("owner = $0", owner).find()
                delete(items)
                delete(groups)
            }
            Log.d(TAG, "Removed roster data for owner: $owner")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete roster for $owner: ${e.message}", e)
        }
    }

    // Placeholder for SettingManager (not provided in context, assumed similar functionality)
    object SettingManager {
        fun getKey(owner: String, scope: String, key: String): String? {
            // Simulate fetching from a key-value store
            return null // Replace with actual implementation
        }

        fun saveItem(owner: String, scope: String, key: String, value: String) {
            // Simulate saving to a key-value store
            Log.d(TAG, "Saved $key=$value for $owner in scope $scope")
        }

        fun removeItem(owner: String, scope: String, key: String) {
            // Simulate removing from a key-value store
            Log.d(TAG, "Removed $key for $owner in scope $scope")
        }
    }

    // Placeholder for CommonContactsMetadataManager
    object CommonContactsMetadataManager {
        fun update(owner: String, jid: String, username: String?, avatarUrl: String?) {
            Log.d(TAG, "Updated metadata for jid: $jid, owner: $owner, username: $username, avatarUrl: $avatarUrl")
        }
    }

    // Extensions to Account for roster-specific actions (assumed from Swift context)
    fun Account.didReceiveRoster() {
        Log.d(TAG, "Account $jid received roster")
    }

    fun Account.presence() {
        Log.d(TAG, "Sending presence for account $jid")
        // Implement presence sending via Stream if needed
    }

    // SynchronizedArray implementation (simplified, thread-safe list)
    class SynchronizedArray<T> {
        private val list = mutableListOf<T>()
        private val lock = Any()

        fun insert(item: T) {
            synchronized(lock) { list.add(item) }
        }

        fun remove(item: T) {
            synchronized(lock) { list.remove(item) }
        }

        fun firstOrNull(predicate: (T) -> Boolean): T? {
            synchronized(lock) { return list.firstOrNull(predicate) }
        }
    }
}