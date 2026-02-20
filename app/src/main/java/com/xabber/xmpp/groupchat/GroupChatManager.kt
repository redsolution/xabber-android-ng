package com.xabber.xmpp.groupchat

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.stream.Stream
import com.xabber.xmpp.abstractClass.AbstractXMPPManager
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.XMPPMessage
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

@RequiresApi(Build.VERSION_CODES.O)
class GroupchatManager(owner: String) : AbstractXMPPManager(owner) {

    companion object {
        private const val TAG = "GroupchatManager"
        private const val NAMESPACE = "https://xabber.com/protocol/groups"
        private const val REQUEST_TIMEOUT_MS = 15000L

        suspend fun remove(owner: String) {
            val realm = Realm.open(defaultRealmConfig())
            realm.write {
                delete(query<GroupChatStorageItem>("owner = $0", owner).find())
                delete(query<GroupchatInvitesStorageItem>("owner = $0", owner).find())
                delete(query<GroupchatUserStorageItem>("owner = $0", owner).find())
                delete(query<GroupchatInvitedUsersStorageItem>("owner = $0", owner).find())
            }
            realm.close()
        }
    }

    private data class QueueItem(
        val action: Action,
        val elementId: String,
        val callback: ((String?) -> Unit)? = null,
        val settingsCallback: ((List<Map<String, Any>>?, String?) -> Unit)? = null,
        val formCallback: ((List<Map<String, Any>>?, List<Map<String, Any>>?, List<Map<String, Any>>?, String?) -> Unit)? = null,
        val inviteCallback: ((String, String?) -> Unit)? = null,
        val payload: List<Map<String, String>> = emptyList(),
        val value: String = "",
        val values: List<String> = emptyList()
    ) {
        enum class Action {
            CREATE, DELETE, JOIN, LEAVE, INVITE, REVOKE_INVITE,
            REQUEST_USERS, REQUEST_FORM, UPDATE_FORM, BLOCK, UNBLOCK,
            KICK, PIN, PUBLISH_AVATAR, RESET_AVATAR, CHANGE_DATA,
            USER_CARD
        }
    }

    private val queueItems = Collections.synchronizedList(mutableListOf<QueueItem>())
    private val mutex = Mutex()
    private val realm = Realm.open(defaultRealmConfig())

    override fun namespaces(): List<String> = listOf(NAMESPACE)
    override fun getPrimaryNamespace(): String = NAMESPACE

    private fun fullJid(bareJid: String): XMPPJID? =
        try { XMPPJID("$bareJid/Group") } catch (e: Exception) { null }

    // ----------------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------------

    suspend fun create(
        stream: Stream,
        server: String,
        name: String,
        localPart: String? = null,
        privacy: String? = null,
        membership: String? = null,
        index: String? = null,
        description: String? = null,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val query = buildString {
            append("<query xmlns='${xmlns("create")}'>")
            append("<name>$name</name>")
            localPart?.let { append("<localpart>$it</localpart>") }
            privacy?.let { append("<privacy>$it</privacy>") }
            membership?.let { append("<membership>$it</membership>") }
            index?.let { append("<index>$it</index>") }
            description?.let { append("<description>$it</description>") }
            append("</query>")
        }
        val iq = """
            <iq type='set' to='$server' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.CREATE, elementId, callback))

        if (localPart != null) {
            val groupJid = "$localPart@$server"
            queueItems.add(QueueItem(QueueItem.Action.JOIN, "$groupJid:join", null))
        }
    }

    suspend fun delete(stream: Stream, groupchat: String, callback: ((String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val jidObj = try { XMPPJID(groupchat) } catch (e: Exception) { null }
        val localPart = jidObj?.localPart ?: groupchat
        val domain = jidObj?.domainPart ?: groupchat
        val query = """
            <query xmlns='${xmlns("delete")}'>
                <localpart>$localPart</localpart>
            </query>
        """.trimIndent()
        val iq = """
            <iq type='set' to='$domain' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.DELETE, elementId, callback))
    }

    suspend fun join(stream: Stream, groupchat: String, callback: ((String?) -> Unit)? = null) {
        val presence = """
            <presence to='$groupchat' type='subscribe'/>
        """.trimIndent()
        stream.socket?.write(presence)

        val elementId = "$groupchat:join"
        queueItems.add(QueueItem(QueueItem.Action.JOIN, elementId, callback))
        scheduleTimeout(elementId)
    }

    suspend fun leave(stream: Stream, groupchat: String, callback: ((String?) -> Unit)? = null) {
        val presence = """
            <presence to='$groupchat' type='unsubscribe'/>
        """.trimIndent()
        stream.socket?.write(presence)

        val elementId = "$groupchat:leave"
        queueItems.add(QueueItem(QueueItem.Action.LEAVE, elementId, callback))
    }

    suspend fun invite(
        stream: Stream,
        groupchat: String,
        jid: String,
        reason: String? = null,
        callback: ((String, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val inviteXml = buildString {
            append("<invite xmlns='${xmlns("invite")}' jid='$groupchat'>")
            reason?.let { append("<reason>$it</reason>") }
            append("</invite>")
        }
        val message = """
            <message to='$jid' id='$elementId' type='chat'>
                $inviteXml
                <body>Join $groupchat</body>
            </message>
        """.trimIndent()
        stream.socket?.write(message)

        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.INVITE, elementId, inviteCallback = callback, value = jid))
        scheduleTimeout(elementId)
    }

    suspend fun revokeInvite(
        stream: Stream,
        groupchat: String,
        jid: String,
        callback: ((String, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val query = """
            <query xmlns='${xmlns("invite")}'>
                <revoke>
                    <jid>$jid</jid>
                </revoke>
            </query>
        """.trimIndent()
        val iq = """
            <iq type='set' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.REVOKE_INVITE, elementId, inviteCallback = callback, value = jid))
        scheduleTimeout(elementId)
    }

    suspend fun requestUsers(stream: Stream, groupchat: String, userId: String? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val queryAttrs = buildString {
            append("xmlns='${xmlns("members")}'")
            userId?.let { append(" id='$it'") }
        }
        val query = "<query $queryAttrs/>"
        val iq = """
            <iq type='get' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(if (userId != null) QueueItem.Action.USER_CARD else QueueItem.Action.REQUEST_USERS, elementId))
    }

    suspend fun requestSettingsForm(stream: Stream, groupchat: String, callback: ((List<Map<String, Any>>?, String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val iq = """
            <iq type='get' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                <query xmlns='$NAMESPACE'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.REQUEST_FORM, elementId, settingsCallback = callback))
        scheduleTimeout(elementId)
    }

    suspend fun updateForm(
        stream: Stream,
        groupchat: String,
        formData: List<Map<String, Any>>,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val x = buildXDataForm(formData, type = "submit")
        val query = "<query xmlns='$NAMESPACE'>$x</query>"
        val iq = """
            <iq type='set' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.UPDATE_FORM, elementId, callback))
        scheduleTimeout(elementId)
    }

    suspend fun kickUser(stream: Stream, groupchat: String, userId: String, callback: ((String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val query = """
            <kick xmlns='$NAMESPACE'>
                <id>$userId</id>
            </kick>
        """.trimIndent()
        val iq = """
            <iq type='set' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.KICK, elementId, callback))
        scheduleTimeout(elementId)
    }

    suspend fun blockUser(
        stream: Stream,
        groupchat: String,
        userIds: List<String> = emptyList(),
        jids: List<String> = emptyList(),
        domains: List<String> = emptyList(),
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val blockXml = buildString {
            append("<block xmlns='${xmlns("block")}'>")
            userIds.forEach { append("<id>$it</id>") }
            jids.forEach { append("<jid>$it</jid>") }
            domains.forEach { append("<domain>$it</domain>") }
            append("</block>")
        }
        val iq = """
            <iq type='set' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                $blockXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        val payload = mutableListOf<Map<String, String>>()
        userIds.forEach { payload.add(mapOf("type" to "id", "value" to it)) }
        jids.forEach { payload.add(mapOf("type" to "jid", "value" to it)) }
        domains.forEach { payload.add(mapOf("type" to "domain", "value" to it)) }
        queueItems.add(QueueItem(QueueItem.Action.BLOCK, elementId, callback, payload = payload))
        scheduleTimeout(elementId)
    }

    suspend fun unblockUser(
        stream: Stream,
        groupchat: String,
        userIds: List<String> = emptyList(),
        jids: List<String> = emptyList(),
        domains: List<String> = emptyList(),
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val unblockXml = buildString {
            append("<unblock xmlns='${xmlns("block")}'>")
            userIds.forEach { append("<id>$it</id>") }
            jids.forEach { append("<jid>$it</jid>") }
            domains.forEach { append("<domain>$it</domain>") }
            append("</unblock>")
        }
        val iq = """
            <iq type='set' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                $unblockXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        val payload = mutableListOf<Map<String, String>>()
        userIds.forEach { payload.add(mapOf("type" to "id", "value" to it)) }
        jids.forEach { payload.add(mapOf("type" to "jid", "value" to it)) }
        domains.forEach { payload.add(mapOf("type" to "domain", "value" to it)) }
        queueItems.add(QueueItem(QueueItem.Action.UNBLOCK, elementId, callback, payload = payload))
        scheduleTimeout(elementId)
    }

    suspend fun pinMessage(stream: Stream, groupchat: String, stanzaId: String, callback: ((String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val query = """
            <update xmlns='$NAMESPACE'>
                <pinned-message>$stanzaId</pinned-message>
            </update>
        """.trimIndent()
        val iq = """
            <iq type='set' to='${fullJid(groupchat)?.bare()}' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        queueItems.add(QueueItem(QueueItem.Action.PIN, elementId, callback))
        scheduleTimeout(elementId)
    }

    // ----------------------------------------------------------------------
    // Incoming stanza handling
    // ----------------------------------------------------------------------

    override suspend fun read(iq: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(iq.byteInputStream())
                val iqElement = doc.documentElement
                val id = iqElement.getAttribute("id")
                val from = iqElement.getAttribute("from")
                val type = iqElement.getAttribute("type")

                if (queryIds.contains(id)) {
                    val query = iqElement.getElementsByTagNameNS(NAMESPACE, "query")?.item(0) as? Element
                    val ns = query?.getAttribute("xmlns") ?: ""
                    when {
                        ns == xmlns("create") -> handleCreateResponse(iqElement, id, from, type)
                        ns == xmlns("delete") -> handleDeleteResponse(iqElement, id, from, type)
                        ns == xmlns("members") -> handleMembersResponse(iqElement, id, from)
                        ns == xmlns("invite") -> handleInviteResponse(iqElement, id, from, type)
                        ns == xmlns("block") -> handleBlockResponse(iqElement, id, from, type)
                        ns == NAMESPACE -> handleSettingsFormResponse(iqElement, id, from, type)
                        ns == xmlns("status") -> handleStatusFormResponse(iqElement, id, from, type)
                        else -> handleGenericSuccess(iqElement, id, type)
                    }
                    return@withContext true
                }
                if (type == "set") {
                    val query = iqElement.getElementsByTagNameNS(NAMESPACE, "query")?.item(0) as? Element
                    val ns = query?.getAttribute("xmlns") ?: ""
                    when (ns) {
                        xmlns("members") -> handleMembersPush(query, from)
                        xmlns("invite") -> handleInvitePush(query, from)
                        else -> { /* ignore */ }
                    }
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing groupchat IQ: ${e.message}")
                false
            }
        }
    }

    suspend fun handlePresence(presenceXml: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(presenceXml.byteInputStream())
                val presence = doc.documentElement
                val from = presence.getAttribute("from")
                val type = presence.getAttribute("type")
                val jid = try { XMPPJID(from).bare() } catch (e: Exception) { return@withContext false }

                when (type) {
                    "subscribe" -> {
                        val elementId = "$jid:join"
                        queueItems.find { it.elementId == elementId }?.callback?.invoke(null)
                        queueItems.removeAll { it.elementId == elementId }
                        handleGroupInfoPresence(presence)
                        return@withContext true
                    }
                    "subscribed" -> {
                        createOrUpdateGroupChatFromPresence(presence)
                        return@withContext true
                    }
                    "unsubscribe" -> {
                        val elementId = "$jid:leave"
                        queueItems.find { it.elementId == elementId }?.callback?.invoke("error")
                        queueItems.removeAll { it.elementId == elementId }
                        return@withContext true
                    }
                    "unsubscribed" -> {
                        val elementId = "$jid:leave"
                        queueItems.find { it.elementId == elementId }?.callback?.invoke(null)
                        queueItems.removeAll { it.elementId == elementId }
                        afterLeave(jid)
                        return@withContext true
                    }
                }

                val x = presence.getElementsByTagNameNS(NAMESPACE, "x")?.item(0) as? Element
                if (x != null) {
                    handleGroupInfoPresence(presence)
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error handling groupchat presence: ${e.message}")
                false
            }
        }
    }

    suspend fun handleMessage(message: XMPPMessage) {
        val groupchat = message.from?.bare() ?: return
        message.elements("reference").forEach { ref ->
            if (ref.getAttribute("type") == "mutable") {
                ref.element("user")?.let { userElement ->
                    updateUserCard(userElement, groupchat = groupchat, trustedSource = true)
                }
            }
        }
        message.element("x", NAMESPACE)?.let { x ->
            x.element("user")?.let { userElement ->
                updateUserCard(userElement, groupchat = groupchat, trustedSource = true)
            }
            x.element("pinned-message")?.textContent?.let { pinnedId ->
                realm.write {
                    val groupItem = query<GroupChatStorageItem>(
                        "primary = $0",
                        GroupChatStorageItem.genPrimary(groupchat, owner)
                    ).first().find()
                    groupItem?.pinnedMessage = pinnedId
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Internal handlers
    // ----------------------------------------------------------------------

    private suspend fun handleCreateResponse(iq: Element, id: String, from: String, type: String) {
        val item = queueItems.find { it.elementId == id } ?: return
        if (type == "result") {
            val jid = iq.getElementsByTagName("jid")?.item(0)?.textContent
            if (jid != null) {
                if (item.value == "peer-to-peer") {
                    val stream = AccountManager.find(owner)?.stream
                    stream?.let { join(it, jid) }
                }
                item.callback?.invoke("success")
            } else {
                item.callback?.invoke("error")
            }
        } else {
            item.callback?.invoke("error")
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleDeleteResponse(iq: Element, id: String, from: String, type: String) {
        val item = queueItems.find { it.elementId == id } ?: return
        if (type == "result") {
            item.callback?.invoke(null)
            afterDelete(from)
        } else {
            item.callback?.invoke("error")
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleMembersResponse(iq: Element, id: String, from: String) {
        val item = queueItems.find { it.elementId == id }
        val query = iq.getElementsByTagNameNS(NAMESPACE, "query")?.item(0) as? Element ?: return
        val version = query.getAttribute("version")
        val users = query.getElementsByTagName("user")

        for (i in 0 until users.length) {
            val userEl = users.item(i) as Element
            updateUserCardFromElement(userEl, groupchat = from, trustedSource = true)
        }

        realm.write {
            val groupPrimary = GroupChatStorageItem.genPrimary(from, owner)
            val group = query<GroupChatStorageItem>("primary = $0", groupPrimary).first().find()
            group?.usersListVersion = version
        }

        if (item?.action == QueueItem.Action.USER_CARD) {
            // specific user card request, nothing else
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleMembersPush(query: Element?, from: String) {
        // Unsolicited member list update
        query?.getElementsByTagName("user")?.let { users ->
            for (i in 0 until users.length) {
                val userEl = users.item(i) as Element
                updateUserCardFromElement(userEl, groupchat = from, trustedSource = true)
            }
        }
    }

    private suspend fun handleInviteResponse(iq: Element, id: String, from: String, type: String) {
        val item = queueItems.find { it.elementId == id } ?: return
        if (type == "result") {
            item.inviteCallback?.invoke(item.value, null)
        } else {
            val error = iq.getElementsByTagName("error")?.item(0)?.textContent ?: "error"
            item.inviteCallback?.invoke(item.value, error)
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleInvitePush(query: Element?, from: String) {
        // Push of invited users list
        query?.getElementsByTagName("user")?.let { nodes ->
            val groupchatId = GroupChatStorageItem.genPrimary(from, owner)
            realm.write {
                for (i in 0 until nodes.length) {
                    val userEl = nodes.item(i) as Element
                    val jid = userEl.getAttribute("jid")
                    if (jid.isNotEmpty()) {
                        val primary = GroupchatInvitedUsersStorageItem.genPrimary(jid, from, owner)
                        var invited = query<GroupchatInvitedUsersStorageItem>("primary = $0", primary).first().find()
                        if (invited == null) {
                            invited = GroupchatInvitedUsersStorageItem().apply {
                                this.primary = primary
                                this.owner = this@GroupchatManager.owner
                                this.jid = jid
                                this.groupchatId = groupchatId
                            }
                            copyToRealm(invited, UpdatePolicy.ALL)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleBlockResponse(iq: Element, id: String, from: String, type: String) {
        val item = queueItems.find { it.elementId == id } ?: return
        if (type == "result") {
            realm.write {
                val groupchatId = GroupChatStorageItem.genPrimary(from, owner)
                item.payload.forEach { p ->
                    val value = p["value"] ?: return@forEach
                    val users = when (p["type"]) {
                        "id" -> query<GroupchatUserStorageItem>("groupchatId = $0 AND userId = $1", groupchatId, value)
                        "jid" -> query<GroupchatUserStorageItem>("groupchatId = $0 AND jid = $1", groupchatId, value)
                        else -> null
                    }
                    users?.find()?.forEach { user ->
                        user.isBlocked = (item.action == QueueItem.Action.BLOCK)
                    }
                }
            }
            item.callback?.invoke(null)
        } else {
            item.callback?.invoke("error")
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleSettingsFormResponse(iq: Element, id: String, from: String, type: String) {
        val item = queueItems.find { it.elementId == id } ?: return
        if (type == "result") {
            val x = iq.getElementsByTagNameNS("jabber:x:data", "x")?.item(0) as? Element
            if (x != null && x.getAttribute("type") == "form") {
                val fields = parseDataForm(x)
                item.settingsCallback?.invoke(fields, null)
            } else {
                item.settingsCallback?.invoke(null, "not a form")
            }
        } else {
            item.settingsCallback?.invoke(null, "error")
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleStatusFormResponse(iq: Element, id: String, from: String, type: String) {
        // Simplified – similar to settings
        val item = queueItems.find { it.elementId == id } ?: return
        if (type == "result") {
            val x = iq.getElementsByTagNameNS("jabber:x:data", "x")?.item(0) as? Element
            if (x != null && x.getAttribute("type") == "form") {
                val fields = parseDataForm(x)
                item.settingsCallback?.invoke(fields, null)
            } else {
                item.settingsCallback?.invoke(null, "not a form")
            }
        } else {
            item.settingsCallback?.invoke(null, "error")
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleGenericSuccess(iq: Element, id: String, type: String) {
        val item = queueItems.find { it.elementId == id } ?: return
        if (type == "result") {
            item.callback?.invoke(null)
        } else {
            item.callback?.invoke("error")
        }
        queueItems.remove(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleGroupInfoPresence(presence: Element) {
        val from = presence.getAttribute("from")
        val jid = try { XMPPJID(from).bare() } catch (e: Exception) { return }
        val x = presence.getElementsByTagNameNS(NAMESPACE, "x")?.item(0) as? Element ?: return

        realm.write {
            val groupPrimary = GroupChatStorageItem.genPrimary(jid, owner)
            var group = query<GroupChatStorageItem>("primary = $0", groupPrimary).first().find()
            if (group == null) {
                group = GroupChatStorageItem().apply {
                    primary = groupPrimary
                    this.jid = jid
                    this.owner = this@GroupchatManager.owner
                }
                copyToRealm(group, UpdatePolicy.ALL)
            } else {
                group = findLatest(group)
            }

            group?.let {
                it.name = x.getElementsByTagName("name")?.item(0)?.textContent ?: it.name
                it.privacy_ = x.getElementsByTagName("privacy")?.item(0)?.textContent ?: it.privacy_
                it.index_ = x.getElementsByTagName("index")?.item(0)?.textContent ?: it.index_
                it.membership_ = x.getElementsByTagName("membership")?.item(0)?.textContent ?: it.membership_
                it.descr = x.getElementsByTagName("description")?.item(0)?.textContent ?: it.descr
                it.members = x.getAttribute("members")?.toIntOrNull() ?: it.members
                it.present = x.getAttribute("present")?.toIntOrNull() ?: it.present
                it.status = x.getElementsByTagName("status")?.item(0)?.textContent ?: it.status
                x.getElementsByTagName("pinned-message")?.item(0)?.textContent?.let { pinned ->
                    it.pinnedMessage = pinned
                }
            }
        }
    }

    private suspend fun createOrUpdateGroupChatFromPresence(presence: Element) {
        val from = presence.getAttribute("from")
        val jid = try { XMPPJID(from).bare() } catch (e: Exception) { return }

        realm.write {
            val groupPrimary = GroupChatStorageItem.genPrimary(jid, owner)
            if (query<GroupChatStorageItem>("primary = $0", groupPrimary).first().find() == null) {
                val group = GroupChatStorageItem().apply {
                    primary = groupPrimary
                    this.jid = jid
                    this.owner = this@GroupchatManager.owner
                    name = jid
                }
                copyToRealm(group, UpdatePolicy.ALL)
            }
        }

        val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Group)
        realm.write {
            if (query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find() == null) {
                val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find()
                val chat = LastChatsStorageItem().apply {
                    primary = chatPrimary
                    this.jid = jid
                    this.owner = this@GroupchatManager.owner
                    conversationType_ = ConversationType.Group.rawValue
                    this.rosterItem = rosterItem
                    messageDate = System.currentTimeMillis()
                }
                copyToRealm(chat, UpdatePolicy.ALL)
            }
        }
    }

    private suspend fun afterDelete(groupchat: String) {
        realm.write {
            val groupPrimary = GroupChatStorageItem.genPrimary(groupchat, owner)
            query<GroupChatStorageItem>("primary = $0", groupPrimary).find().forEach { delete(it) }

            val chatPrimary = LastChatsStorageItem.genPrimary(groupchat, owner, ConversationType.Group)
            query<LastChatsStorageItem>("primary = $0", chatPrimary).find().forEach { delete(it) }

            query<MessageStorageItem>("owner = $0 AND opponent = $1", owner, groupchat).find().forEach { delete(it) }
            query<GroupchatUserStorageItem>("groupchatId = $0", groupPrimary).find().forEach { delete(it) }
            query<GroupchatInvitedUsersStorageItem>("owner = $0 AND groupchatId = $1", owner, groupPrimary).find().forEach { delete(it) }
        }
    }

    private suspend fun afterLeave(groupchat: String) = afterDelete(groupchat)

    // ----------------------------------------------------------------------
    // Helper methods
    // ----------------------------------------------------------------------

    private fun xmlns(action: String): String = "$NAMESPACE#$action"

    private fun scheduleTimeout(elementId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(REQUEST_TIMEOUT_MS)
            queueItems.find { it.elementId == elementId }?.let { item ->
                when {
                    item.settingsCallback != null -> item.settingsCallback.invoke(null, "timeout")
                    item.formCallback != null -> item.formCallback.invoke(null, null, null, "timeout")
                    item.inviteCallback != null -> item.inviteCallback.invoke(item.value, "timeout")
                    else -> item.callback?.invoke("timeout")
                }
                queueItems.remove(item)
                checkAndRemoveQueryId(elementId)
            }
        }
    }

    private suspend fun updateUserCardFromElement(
        userEl: Element,
        groupchat: String,
        trustedSource: Boolean,
        commitTransaction: Boolean = true
    ) {
        // Convert DOM Element to our XMLElement structure (simplified)
        val id = userEl.getAttribute("id")
        if (id.isNullOrEmpty()) return

        val groupchatId = GroupChatStorageItem.genPrimary(groupchat, owner)
        val primary = GroupchatUserStorageItem.genPrimary(id, groupchat, owner)

        realm.write {
            var user = query<GroupchatUserStorageItem>("primary = $0", primary).first().find()
            if (user == null) {
                user = GroupchatUserStorageItem().apply {
                    this.primary = primary
                    this.userId = id
                    this.groupchatId = groupchatId
                    this.owner = this@GroupchatManager.owner
                }
                copyToRealm(user, UpdatePolicy.ALL)
            } else {
                user = findLatest(user)
            }

            user?.apply {
                jid = userEl.getElementsByTagName("jid")?.item(0)?.textContent ?: jid
                nickname = userEl.getElementsByTagName("nickname")?.item(0)?.textContent ?: nickname
                role_ = userEl.getElementsByTagName("role")?.item(0)?.textContent ?: role_
                subscribtion_ = userEl.getElementsByTagName("subscription")?.item(0)?.textContent ?: subscribtion_
                badge = userEl.getElementsByTagName("badge")?.item(0)?.textContent ?: ""
                isOnline = userEl.getElementsByTagName("present")?.item(0)?.textContent == "now"
                updateTimestamp = System.currentTimeMillis()
                isTemporary = !trustedSource
                // Permissions/restrictions could be parsed from <permission> and <restriction> if needed
            }
        }
    }

    private suspend fun updateUserCard(
        userEl: XMLElement,
        groupchat: String,
        trustedSource: Boolean,
        commitTransaction: Boolean = true
    ) {
        val id = userEl.getAttribute("id")
        if (id.isNullOrEmpty()) return

        val groupchatId = GroupChatStorageItem.genPrimary(groupchat, owner)
        val primary = GroupchatUserStorageItem.genPrimary(id, groupchat, owner)

        realm.write {
            var user = query<GroupchatUserStorageItem>("primary = $0", primary).first().find()
            if (user == null) {
                user = GroupchatUserStorageItem().apply {
                    this.primary = primary
                    this.userId = id
                    this.groupchatId = groupchatId
                    this.owner = this@GroupchatManager.owner
                }
                copyToRealm(user, UpdatePolicy.ALL)
            } else {
                user = findLatest(user)
            }

            user?.apply {
                jid = userEl.element("jid")?.textContent ?: jid
                nickname = userEl.element("nickname")?.textContent ?: nickname
                role_ = userEl.element("role")?.textContent ?: role_
                subscribtion_ = userEl.element("subscription")?.textContent ?: subscribtion_
                badge = userEl.element("badge")?.textContent ?: ""
                isOnline = userEl.element("present")?.textContent == "now"
                updateTimestamp = System.currentTimeMillis()
                isTemporary = !trustedSource
            }
        }
    }

    private fun parseDataForm(x: Element): List<Map<String, Any>> {
        val fields = mutableListOf<Map<String, Any>>()
        val fieldNodes = x.getElementsByTagName("field")
        for (i in 0 until fieldNodes.length) {
            val f = fieldNodes.item(i) as Element
            val field = mutableMapOf<String, Any>()
            f.getAttribute("var")?.takeIf { it.isNotEmpty() }?.let { field["var"] = it }
            f.getAttribute("type")?.takeIf { it.isNotEmpty() }?.let { field["type"] = it }
            f.getAttribute("label")?.takeIf { it.isNotEmpty() }?.let { field["label"] = it }

            val values = f.getElementsByTagName("value")
            if (values.length == 1) {
                field["value"] = values.item(0).textContent
            } else if (values.length > 1) {
                field["values"] = (0 until values.length).map { values.item(it).textContent }
            }

            val options = f.getElementsByTagName("option")
            if (options.length > 0) {
                val opts = (0 until options.length).map { idx ->
                    val opt = options.item(idx) as Element
                    mapOf(
                        "label" to (opt.getAttribute("label") ?: ""),
                        "value" to (opt.getElementsByTagName("value")?.item(0)?.textContent ?: "")
                    )
                }
                field["options"] = opts
            }
            fields.add(field)
        }
        return fields
    }

    private fun buildXDataForm(fields: List<Map<String, Any>>, type: String): String {
        val sb = StringBuilder()
        sb.append("<x xmlns='jabber:x:data' type='$type'>")
        fields.forEach { field ->
            sb.append("<field var='${field["var"]}' type='${field["type"] ?: "text-single"}' label='${field["label"] ?: ""}'>")
            (field["values"] as? List<String>)?.forEach { value ->
                sb.append("<value>$value</value>")
            }
            (field["value"] as? String)?.let { value ->
                sb.append("<value>$value</value>")
            }
            sb.append("</field>")
        }
        sb.append("</x>")
        return sb.toString()
    }

    override suspend fun clearSession() {
        super.clearSession()
        queueItems.clear()
    }

    fun close() {
        realm.close()
    }
}