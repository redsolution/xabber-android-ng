package com.xabber.xmpp.groupchat

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterGroupStorageItem
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
import kotlinx.coroutines.sync.withLock
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
            USER_CARD, MEDIATED_INVITE, DECLINE_INVITE, LIST_INVITATIONS,
            REQUEST_INFO, REQUEST_BLOCK_LIST, DEFAULT_RIGHTS, USER_RIGHTS,
            CHANGE_NICKNAME, ADD_OWNER
        }
    }

    private val queueItems = mutableListOf<QueueItem>()
    private val mutex = Mutex()
    private val realm = Realm.open(defaultRealmConfig())

    private suspend fun findQueueItem(id: String): QueueItem? = mutex.withLock {
        queueItems.find { it.elementId == id }
    }
    private suspend fun removeQueueItem(item: QueueItem) = mutex.withLock {
        queueItems.remove(item)
    }
    private suspend fun addQueueItem(item: QueueItem) = mutex.withLock {
        queueItems.add(item)
    }

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
        status: String? = null,
        languages: List<String>? = null,
        contacts: List<String>? = null,
        domains: List<String>? = null,
        peerToPeerJid: String? = null,
        peerToPeerUserId: String? = null,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        // Per XEP-GROUPS V3 spec: <create xmlns='...'><group privacy='...'><info>...</info><settings>...</settings></group></create>
        val createXml = buildString {
            append("<create xmlns='$NAMESPACE'>")
            if (peerToPeerJid != null) {
                // Peer-to-peer creation uses a different element
                val withAttr = peerToPeerUserId?.let { " with='$it'" } ?: ""
                append("<peer-to-peer parent='$peerToPeerJid'$withAttr/>")
            } else {
                val privacyAttr = privacy?.let { " privacy='$it'" } ?: ""
                append("<group$privacyAttr>")
                localPart?.let { append("<localpart>$it</localpart>") }
                // Info block
                append("<info>")
                append("<name>$name</name>")
                description?.let { append("<description>$it</description>") }
                append("</info>")
                // Settings block
                val hasSettings = membership != null || index != null || status != null ||
                    !contacts.isNullOrEmpty() || !domains.isNullOrEmpty() || !languages.isNullOrEmpty()
                if (hasSettings) {
                    append("<settings>")
                    membership?.let { append("<membership>$it</membership>") }
                    index?.let { append("<index>$it</index>") }
                    status?.let { append("<status>$it</status>") }
                    languages?.takeIf { it.isNotEmpty() }?.let { langs ->
                        append("<languages>")
                        langs.forEach { append("<language>$it</language>") }
                        append("</languages>")
                    }
                    contacts?.takeIf { it.isNotEmpty() }?.let { c ->
                        append("<contacts>")
                        c.forEach { append("<contact>$it</contact>") }
                        append("</contacts>")
                    }
                    domains?.takeIf { it.isNotEmpty() }?.let { d ->
                        append("<domains>")
                        d.forEach { append("<domain>$it</domain>") }
                        append("</domains>")
                    }
                    append("</settings>")
                }
                append("</group>")
            }
            append("</create>")
        }
        val iq = """
            <iq type='set' to='$server' id='$elementId'>
                $createXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        val actionValue = if (peerToPeerJid != null) "peer-to-peer" else ""
        addQueueItem(QueueItem(QueueItem.Action.CREATE, elementId, callback, value = actionValue))
        scheduleTimeout(elementId)

        if (localPart != null) {
            val groupJid = "$localPart@$server"
            addQueueItem(QueueItem(QueueItem.Action.JOIN, "$groupJid:join", null))
        }
    }

    suspend fun delete(stream: Stream, groupchat: String, callback: ((String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val jidObj = try { XMPPJID(groupchat) } catch (e: Exception) { null }
        val bareJid = jidObj?.bare() ?: groupchat
        val domain = jidObj?.domainPart ?: groupchat
        // Per spec: <delete xmlns='https://xabber.com/protocol/groups'>jid</delete>
        val deleteXml = "<delete xmlns='$NAMESPACE'>$bareJid</delete>"
        val iq = """
            <iq type='set' to='$domain' id='$elementId'>
                $deleteXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.DELETE, elementId, callback))
        scheduleTimeout(elementId)
    }

    suspend fun join(stream: Stream, groupchat: String, callback: ((String?) -> Unit)? = null) {
        val presence = """
            <presence to='$groupchat' type='subscribe'/>
        """.trimIndent()
        stream.socket?.write(presence)

        val elementId = "$groupchat:join"
        addQueueItem(QueueItem(QueueItem.Action.JOIN, elementId, callback))
        scheduleTimeout(elementId)
    }

    suspend fun leave(stream: Stream, groupchat: String, callback: ((String?) -> Unit)? = null) {
        val presence = """
            <presence to='$groupchat' type='unsubscribe'/>
        """.trimIndent()
        stream.socket?.write(presence)

        val elementId = "$groupchat:leave"
        addQueueItem(QueueItem(QueueItem.Action.LEAVE, elementId, callback))
        scheduleTimeout(elementId)
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
            append("<invite xmlns='$NAMESPACE' jid='$groupchat'>")
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
        // Direct invitations are <message> stanzas — no server response expected
        callback?.invoke(jid, null)
    }

    suspend fun revokeInvite(
        stream: Stream,
        groupchat: String,
        jid: String,
        callback: ((String, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val revokeXml = """
            <revoke xmlns='$NAMESPACE'>
                <jid>$jid</jid>
            </revoke>
        """.trimIndent()
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $revokeXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.REVOKE_INVITE, elementId, inviteCallback = callback, value = jid))
        scheduleTimeout(elementId)
    }

    suspend fun requestUsers(stream: Stream, groupchat: String, userId: String? = null, version: String? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val membersAttrs = buildString {
            append("xmlns='$NAMESPACE'")
            userId?.let { append(" id='$it'") }
            version?.let { append(" version='$it'") }
        }
        val query = "<members $membersAttrs/>"
        // Per spec, member requests go to groupchat/Group resource
        val toJid = fullJid(groupchat) ?: return
        val iq = """
            <iq type='get' to='$toJid' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(if (userId != null) QueueItem.Action.USER_CARD else QueueItem.Action.REQUEST_USERS, elementId))
        scheduleTimeout(elementId)
    }

    suspend fun requestSelfIdsForAllGroups(stream: Stream) {
        val groups = realm.query<GroupChatStorageItem>(
            "owner = $0 AND myMemberId = ''", owner
        ).find()
        for (group in groups) {
            try {
                requestUsers(stream, group.jid, userId = "0")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request self member ID for ${group.jid}: ${e.message}")
            }
        }
    }

    suspend fun requestSettingsForm(stream: Stream, groupchat: String, callback: ((List<Map<String, Any>>?, String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='get' to='$bareJid' id='$elementId'>
                <query xmlns='$NAMESPACE'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.REQUEST_FORM, elementId, settingsCallback = callback))
        scheduleTimeout(elementId)
    }

    suspend fun updateForm(
        stream: Stream,
        groupchat: String,
        formData: List<Map<String, Any>>,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val x = buildXDataForm(formData, type = "submit")
        val query = "<query xmlns='$NAMESPACE'>$x</query>"
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.UPDATE_FORM, elementId, callback))
        scheduleTimeout(elementId)
    }

    suspend fun kickUser(
        stream: Stream,
        groupchat: String,
        userId: String? = null,
        jid: String? = null,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val kickContent = when {
            jid != null -> "<jid>$jid</jid>"
            userId != null -> "<id>$userId</id>"
            else -> return
        }
        val kickXml = "<kick xmlns='$NAMESPACE'>$kickContent</kick>"
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $kickXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.KICK, elementId, callback))
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
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        // Per spec: <block xmlns='https://xabber.com/protocol/groups'>
        val blockXml = buildString {
            append("<block xmlns='$NAMESPACE'>")
            userIds.forEach { append("<id>$it</id>") }
            jids.forEach { append("<jid>$it</jid>") }
            domains.forEach { append("<domain>$it</domain>") }
            append("</block>")
        }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $blockXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        val payload = mutableListOf<Map<String, String>>()
        userIds.forEach { payload.add(mapOf("type" to "id", "value" to it)) }
        jids.forEach { payload.add(mapOf("type" to "jid", "value" to it)) }
        domains.forEach { payload.add(mapOf("type" to "domain", "value" to it)) }
        addQueueItem(QueueItem(QueueItem.Action.BLOCK, elementId, callback, payload = payload))
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
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        // Per spec: <unblock xmlns='https://xabber.com/protocol/groups'/>
        val unblockXml = buildString {
            append("<unblock xmlns='$NAMESPACE'>")
            userIds.forEach { append("<id>$it</id>") }
            jids.forEach { append("<jid>$it</jid>") }
            domains.forEach { append("<domain>$it</domain>") }
            append("</unblock>")
        }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $unblockXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        val payload = mutableListOf<Map<String, String>>()
        userIds.forEach { payload.add(mapOf("type" to "id", "value" to it)) }
        jids.forEach { payload.add(mapOf("type" to "jid", "value" to it)) }
        domains.forEach { payload.add(mapOf("type" to "domain", "value" to it)) }
        addQueueItem(QueueItem(QueueItem.Action.UNBLOCK, elementId, callback, payload = payload))
        scheduleTimeout(elementId)
    }

    suspend fun pinMessage(stream: Stream, groupchat: String, stanzaId: String, callback: ((String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                <pinned-message id='$stanzaId' status='pinned'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.PIN, elementId, callback))
        scheduleTimeout(elementId)
    }

    suspend fun unpinMessage(stream: Stream, groupchat: String, stanzaId: String, callback: ((String?) -> Unit)? = null) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                <pinned-message id='$stanzaId' status='remove'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.PIN, elementId, callback))
        scheduleTimeout(elementId)
    }

    // --- 2.2 Mediated Invitation (IQ to group server) ---
    suspend fun mediatedInvite(
        stream: Stream,
        groupchat: String,
        jid: String,
        send: Boolean = true,
        reason: String? = null,
        callback: ((String, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val inviteXml = buildString {
            append("<invite xmlns='$NAMESPACE'>")
            append("<jid>$jid</jid>")
            append("<send>$send</send>")
            reason?.let { append("<reason>$it</reason>") }
            append("</invite>")
        }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $inviteXml
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.MEDIATED_INVITE, elementId, inviteCallback = callback, value = jid))
        scheduleTimeout(elementId)
    }

    // --- 2.3 Decline Invitation ---
    suspend fun declineInvitation(
        stream: Stream,
        groupchat: String,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                <decline xmlns='$NAMESPACE'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.DECLINE_INVITE, elementId, callback))
        scheduleTimeout(elementId)
    }

    // --- 2.4 List Invitations ---
    suspend fun requestInvitations(
        stream: Stream,
        groupchat: String,
        callback: ((List<Map<String, Any>>?, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='get' to='$bareJid' id='$elementId'>
                <invites xmlns='$NAMESPACE'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.LIST_INVITATIONS, elementId, settingsCallback = callback))
        scheduleTimeout(elementId)
    }

    // --- 2.5 Group Info Query ---
    suspend fun requestGroupInfo(
        stream: Stream,
        groupchat: String,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        // Per spec: <query xmlns="https://xabber.com/protocol/groups" />
        val iq = """
            <iq to='$bareJid' type='get' id='$elementId'>
                <query xmlns='$NAMESPACE'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.REQUEST_INFO, elementId, callback))
        scheduleTimeout(elementId)
    }

    // --- 1.7 / 2.6 Block List Request ---
    suspend fun requestBlockList(
        stream: Stream,
        groupchat: String,
        callback: ((List<Map<String, Any>>?, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        // Per spec: <block xmlns='https://xabber.com/protocol/groups'/>
        val iq = """
            <iq type='get' to='$bareJid' id='$elementId'>
                <block xmlns='$NAMESPACE'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.REQUEST_BLOCK_LIST, elementId, settingsCallback = callback))
        scheduleTimeout(elementId)
    }

    // --- 2.6 Default Restrictions (#default-rights) ---
    suspend fun requestDefaultRights(
        stream: Stream,
        groupchat: String,
        callback: ((List<Map<String, Any>>?, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='get' to='$bareJid' id='$elementId'>
                <query xmlns='${xmlns("default-rights")}'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.DEFAULT_RIGHTS, elementId, settingsCallback = callback))
        scheduleTimeout(elementId)
    }

    suspend fun updateDefaultRights(
        stream: Stream,
        groupchat: String,
        formData: List<Map<String, Any>>,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val x = buildXDataForm(formData, type = "submit")
        val query = "<query xmlns='${xmlns("default-rights")}'>$x</query>"
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.DEFAULT_RIGHTS, elementId, callback))
        scheduleTimeout(elementId)
    }

    // --- 2.7 User Rights (#rights) ---
    suspend fun requestUserRights(
        stream: Stream,
        groupchat: String,
        userId: String,
        callback: ((List<Map<String, Any>>?, String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='get' to='$bareJid' id='$elementId'>
                <query xmlns='${xmlns("rights")}'>
                    <user xmlns='$NAMESPACE' id='$userId'/>
                </query>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.USER_RIGHTS, elementId, settingsCallback = callback, value = userId))
        scheduleTimeout(elementId)
    }

    suspend fun updateUserRights(
        stream: Stream,
        groupchat: String,
        userId: String,
        formData: List<Map<String, Any>>,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val x = buildXDataForm(formData, type = "submit")
        val query = """
            <query xmlns='${xmlns("rights")}'>
                <user xmlns='$NAMESPACE' id='$userId'/>
                $x
            </query>
        """.trimIndent()
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.USER_RIGHTS, elementId, callback))
        scheduleTimeout(elementId)
    }

    // --- 2.8 Change Nickname/Badge ---
    suspend fun changeNickname(
        stream: Stream,
        groupchat: String,
        userId: String,
        nickname: String? = null,
        badge: String? = null,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val userContent = buildString {
            nickname?.let { append("<nickname>$it</nickname>") }
            badge?.let { append("<badge>$it</badge>") }
        }
        val query = """
            <members xmlns='$NAMESPACE' id='$userId'>
                <user xmlns='$NAMESPACE' id='$userId'>$userContent</user>
            </members>
        """.trimIndent()
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                $query
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.CHANGE_NICKNAME, elementId, callback))
        scheduleTimeout(elementId)
    }

    // --- 2.9 Add Owner ---
    suspend fun addOwner(
        stream: Stream,
        groupchat: String,
        memberId: String,
        callback: ((String?) -> Unit)? = null
    ) {
        val elementId = "GC:${NanoId.generate(6)}"
        val bareJid = try { XMPPJID(groupchat).bare() } catch (e: Exception) { groupchat }
        val iq = """
            <iq type='set' to='$bareJid' id='$elementId'>
                <owner xmlns='$NAMESPACE' id='$memberId'/>
            </iq>
        """.trimIndent()
        stream.socket?.write(iq)
        addQueryId(elementId)
        addQueueItem(QueueItem(QueueItem.Action.ADD_OWNER, elementId, callback))
        scheduleTimeout(elementId)
    }

    // --- 2.10 Presence via Chat State Notifications (XEP-0085) ---
    // Per spec: client MUST notify the server of viewing the group chat
    // by sending chat state notifications
    suspend fun sendPresent(stream: Stream, groupchat: String) {
        val message = """
            <message to='$groupchat' type='chat'>
                <active xmlns='http://jabber.org/protocol/chatstates'/>
            </message>
        """.trimIndent()
        stream.socket?.write(message)
    }

    suspend fun sendNotPresent(stream: Stream, groupchat: String) {
        val message = """
            <message to='$groupchat' type='chat'>
                <inactive xmlns='http://jabber.org/protocol/chatstates'/>
            </message>
        """.trimIndent()
        stream.socket?.write(message)
    }

    // --- 2.11 Peer-to-peer toggle in subscribe presence ---
    suspend fun sendPeerToPeerToggle(stream: Stream, groupchat: String, enabled: Boolean) {
        val presence = """
            <presence to='$groupchat' type='subscribe'>
                <peer-to-peer>$enabled</peer-to-peer>
            </presence>
        """.trimIndent()
        stream.socket?.write(presence)
    }

    // --- 2.12 Avatar collection toggle ---
    suspend fun sendAvatarCollectToggle(stream: Stream, groupchat: String, collect: Boolean) {
        val presence = """
            <presence to='$groupchat' type='subscribe'>
                <collect>$collect</collect>
            </presence>
        """.trimIndent()
        stream.socket?.write(presence)
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
                    // First, try to route by queued action (most reliable)
                    val queuedAction = findQueueItem(id)?.action
                    val members = iqElement.getElementsByTagNameNS(NAMESPACE, "members")?.item(0) as? Element
                    val invite = iqElement.getElementsByTagNameNS(NAMESPACE, "invite")?.item(0) as? Element
                    val revoke = iqElement.getElementsByTagNameNS(NAMESPACE, "revoke")?.item(0) as? Element
                    val decline = iqElement.getElementsByTagNameNS(NAMESPACE, "decline")?.item(0) as? Element

                    when {
                        // Route by queued action
                        queuedAction == QueueItem.Action.CREATE -> handleCreateResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.DELETE -> handleDeleteResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.REQUEST_USERS || queuedAction == QueueItem.Action.USER_CARD ||
                            members != null -> handleMembersResponse(iqElement, id, from)
                        queuedAction == QueueItem.Action.MEDIATED_INVITE || queuedAction == QueueItem.Action.REVOKE_INVITE ||
                            queuedAction == QueueItem.Action.DECLINE_INVITE ||
                            invite != null || revoke != null || decline != null -> handleInviteResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.BLOCK || queuedAction == QueueItem.Action.UNBLOCK ->
                            handleBlockResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.REQUEST_BLOCK_LIST -> handleBlockResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.REQUEST_INFO -> handleInfoResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.DEFAULT_RIGHTS -> handleDefaultRightsResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.USER_RIGHTS -> handleRightsResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.REQUEST_FORM -> handleSettingsFormResponse(iqElement, id, from, type)
                        queuedAction == QueueItem.Action.LIST_INVITATIONS -> handleInviteResponse(iqElement, id, from, type)
                        // Fallback: route by namespace in response
                        else -> {
                            val query = iqElement.getElementsByTagNameNS(NAMESPACE, "query")?.item(0) as? Element
                            val ns = query?.getAttribute("xmlns") ?: ""
                            when {
                                ns == xmlns("default-rights") -> handleDefaultRightsResponse(iqElement, id, from, type)
                                ns == xmlns("rights") -> handleRightsResponse(iqElement, id, from, type)
                                ns == xmlns("status") -> handleStatusFormResponse(iqElement, id, from, type)
                                ns == NAMESPACE -> handleSettingsFormResponse(iqElement, id, from, type)
                                else -> handleGenericSuccess(iqElement, id, type)
                            }
                        }
                    }
                    return@withContext true
                }
                if (type == "set") {
                    // Detect push elements by tag name under base namespace
                    val membersPush = iqElement.getElementsByTagNameNS(NAMESPACE, "members")?.item(0) as? Element
                    val invitePush = iqElement.getElementsByTagNameNS(NAMESPACE, "invite")?.item(0) as? Element
                    when {
                        membersPush != null -> handleMembersPush(membersPush, from)
                        invitePush != null -> handleInvitePush(invitePush, from)
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
                        findQueueItem(elementId)?.let { item ->
                            item.callback?.invoke(null)
                            removeQueueItem(item)
                        }
                        handleGroupInfoPresence(presence)
                        return@withContext true
                    }
                    "subscribed" -> {
                        createOrUpdateGroupChatFromPresence(presence)
                        return@withContext true
                    }
                    "unsubscribe" -> {
                        val elementId = "$jid:leave"
                        findQueueItem(elementId)?.let { item ->
                            item.callback?.invoke("error")
                            removeQueueItem(item)
                        }
                        return@withContext true
                    }
                    "unsubscribed" -> {
                        val elementId = "$jid:leave"
                        findQueueItem(elementId)?.let { item ->
                            item.callback?.invoke(null)
                            removeQueueItem(item)
                        }
                        afterLeave(jid)
                        return@withContext true
                    }
                }

                val groupEl = presence.getElementsByTagNameNS(NAMESPACE, "group")?.item(0) as? Element
                    ?: presence.getElementsByTagNameNS(NAMESPACE, "x")?.item(0) as? Element
                if (groupEl != null) {
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

        // Ensure GroupChatStorageItem exists so presence updates have somewhere to write
        val groupPrimary = GroupChatStorageItem.genPrimary(groupchat, owner)
        if (realm.query<GroupChatStorageItem>("primary = $0", groupPrimary).first().find() == null) {
            realm.write {
                copyToRealm(GroupChatStorageItem().apply {
                    primary = groupPrimary
                    jid = groupchat
                    this.owner = this@GroupchatManager.owner
                }, UpdatePolicy.ALL)
            }
        }

        // Collect all user elements from the message, then batch-write once
        val userElements = mutableListOf<Pair<XMLElement, String>>()

        message.elements("reference").forEach { ref ->
            if (ref.getAttribute("type") == "mutable") {
                ref.element("user")?.let { userElements.add(it to groupchat) }
            }
        }
        var pinnedId: String? = null
        message.element("x", NAMESPACE)?.let { x ->
            x.element("user")?.let { userElements.add(it to groupchat) }
            x.elements("reference").forEach { ref ->
                if (ref.getAttribute("type") == "mutable") {
                    ref.element("user")?.let { userElements.add(it to groupchat) }
                }
            }
            pinnedId = x.element("pinned-message")?.textContent
        }

        // Single batched realm.write for all user card updates + pinned message
        if (userElements.isNotEmpty() || pinnedId != null) {
            realm.write {
                for ((userEl, gc) in userElements) {
                    val id = userEl.getAttribute("id")
                    if (id.isNullOrEmpty()) continue
                    val (avatarUrl, avatarHash) = extractAvatarFromXmlElement(userEl)
                    updateUserCardInTransaction(
                        userId = id,
                        groupchat = gc,
                        trustedSource = true,
                        jidValue = userEl.element("jid")?.textContent,
                        nicknameValue = userEl.element("nickname")?.textContent,
                        roleValue = userEl.element("role")?.textContent,
                        subscriptionValue = userEl.element("subscription")?.textContent,
                        badgeValue = userEl.element("badge")?.textContent,
                        presentText = userEl.element("present")?.textContent,
                        avatarUrl = avatarUrl,
                        avatarHash = avatarHash
                    )
                }
                pinnedId?.let { pid ->
                    val groupItem = query<GroupChatStorageItem>(
                        "primary = $0",
                        GroupChatStorageItem.genPrimary(groupchat, owner)
                    ).first().find()
                    groupItem?.pinnedMessage = pid
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Internal handlers
    // ----------------------------------------------------------------------

    private suspend fun handleCreateResponse(iq: Element, id: String, from: String, type: String) {
        val item = findQueueItem(id) ?: return
        if (type == "result") {
            // Per spec, jid is an attribute on <group jid='...'>, fallback to <jid> child or <localpart>@from
            val groupEl = iq.getElementsByTagName("group")?.item(0) as? Element
            var jid = groupEl?.getAttribute("jid")?.takeIf { it.isNotEmpty() }
                ?: iq.getElementsByTagName("jid")?.item(0)?.textContent
            if (jid == null) {
                val localpart = iq.getElementsByTagName("localpart")?.item(0)?.textContent
                if (localpart != null && from.isNotEmpty()) {
                    jid = "$localpart@$from"
                }
            }
            if (jid != null) {
                // Parse returned settings from <group> element or fallback to <query>
                val dataEl = groupEl ?: iq.getElementsByTagName("query")?.item(0) as? Element
                if (dataEl != null) {
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
                            // Per spec, privacy is an attribute on <group privacy='...'>
                            groupEl?.getAttribute("privacy")?.takeIf { v -> v.isNotEmpty() }?.let { v -> it.privacy_ = v }
                            val infoEl = dataEl.getElementsByTagName("info")?.item(0) as? Element
                            val settingsEl = dataEl.getElementsByTagName("settings")?.item(0) as? Element
                            // Name and description are under <info>
                            (infoEl?.getElementsByTagName("name")?.item(0)?.textContent
                                ?: dataEl.getElementsByTagName("name")?.item(0)?.textContent)?.let { v -> it.name = v }
                            (infoEl?.getElementsByTagName("description")?.item(0)?.textContent
                                ?: dataEl.getElementsByTagName("description")?.item(0)?.textContent)?.let { v -> it.descr = v }
                            // Membership and index are under <settings>
                            (settingsEl?.getElementsByTagName("membership")?.item(0)?.textContent
                                ?: dataEl.getElementsByTagName("membership")?.item(0)?.textContent)?.let { v -> it.membership_ = v }
                            (settingsEl?.getElementsByTagName("index")?.item(0)?.textContent
                                ?: dataEl.getElementsByTagName("index")?.item(0)?.textContent)?.let { v -> it.index_ = v }
                            dataEl.getElementsByTagName("status")?.item(0)?.textContent?.let { v -> it.status = v }
                        }
                    }
                }

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
        removeQueueItem(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleDeleteResponse(iq: Element, id: String, from: String, type: String) {
        val item = findQueueItem(id) ?: return
        if (type == "result") {
            item.callback?.invoke(null)
            afterDelete(from)
        } else {
            item.callback?.invoke("error")
        }
        removeQueueItem(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleMembersResponse(iq: Element, id: String, from: String) {
        val item = findQueueItem(id)
        val membersEl = iq.getElementsByTagNameNS(NAMESPACE, "members")?.item(0) as? Element
            ?: iq.getElementsByTagNameNS(NAMESPACE, "query")?.item(0) as? Element
            ?: return
        val version = membersEl.getAttribute("version")
        val users = membersEl.getElementsByTagName("user")

        // Batch all user card updates into a single realm.write
        val userElements = (0 until users.length).map { users.item(it) as Element }
        updateUserCardsFromElements(userElements, groupchat = from, trustedSource = true)

        realm.write {
            val groupPrimary = GroupChatStorageItem.genPrimary(from, owner)
            val group = query<GroupChatStorageItem>("primary = $0", groupPrimary).first().find()
            group?.usersListVersion = version
        }

        if (item?.action == QueueItem.Action.USER_CARD) {
            // specific user card request, nothing else
        }
        if (item != null) {
            removeQueueItem(item)
        }
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleMembersPush(query: Element?, from: String) {
        // Unsolicited member list update — batch all into single realm.write
        query?.getElementsByTagName("user")?.let { users ->
            val userElements = (0 until users.length).map { users.item(it) as Element }
            updateUserCardsFromElements(userElements, groupchat = from, trustedSource = true)
        }
    }

    private suspend fun handleInviteResponse(iq: Element, id: String, from: String, type: String) {
        val item = findQueueItem(id) ?: return
        if (type == "result") {
            item.inviteCallback?.invoke(item.value, null)
        } else {
            val error = iq.getElementsByTagName("error")?.item(0)?.textContent ?: "error"
            item.inviteCallback?.invoke(item.value, error)
        }
        removeQueueItem(item)
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
        val item = findQueueItem(id) ?: return
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
        removeQueueItem(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleSettingsFormResponse(iq: Element, id: String, from: String, type: String) =
        handleFormResponse(iq, id, type)

    private suspend fun handleStatusFormResponse(iq: Element, id: String, from: String, type: String) =
        handleFormResponse(iq, id, type)

    private suspend fun handleGenericSuccess(iq: Element, id: String, type: String) {
        val item = findQueueItem(id) ?: return
        if (type == "result") {
            item.callback?.invoke(null)
        } else {
            item.callback?.invoke("error")
        }
        removeQueueItem(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleGroupInfoPresence(presence: Element) {
        val from = presence.getAttribute("from")
        val jid = try { XMPPJID(from).bare() } catch (e: Exception) { return }
        val x = presence.getElementsByTagNameNS(NAMESPACE, "group")?.item(0) as? Element
            ?: presence.getElementsByTagNameNS(NAMESPACE, "x")?.item(0) as? Element
            ?: return

        // Extract name from <info><name> or directly from <name>
        val infoElement = x.getElementsByTagName("info")?.item(0) as? Element
        val parsedName = infoElement?.getElementsByTagName("name")?.item(0)?.textContent
            ?: x.getElementsByTagName("name")?.item(0)?.textContent

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
                it.name = parsedName ?: it.name
                it.privacy_ = x.getAttribute("privacy")?.takeIf { v -> v.isNotEmpty() }
                    ?: x.getElementsByTagName("privacy")?.item(0)?.textContent ?: it.privacy_
                it.index_ = x.getElementsByTagName("index")?.item(0)?.textContent ?: it.index_
                val settingsEl = x.getElementsByTagName("settings")?.item(0) as? Element
                it.membership_ = settingsEl?.getElementsByTagName("membership")?.item(0)?.textContent
                    ?: x.getElementsByTagName("membership")?.item(0)?.textContent ?: it.membership_
                it.descr = x.getElementsByTagName("description")?.item(0)?.textContent ?: it.descr
                // Per spec, members is an attribute on <group members='N'>, fallback to child element
                it.members = x.getAttribute("members")?.takeIf { v -> v.isNotEmpty() }?.toIntOrNull()
                    ?: x.getElementsByTagName("members")?.item(0)?.textContent?.toIntOrNull()
                    ?: it.members
                it.present = x.getElementsByTagName("present")?.item(0)?.textContent?.toIntOrNull() ?: it.present
                it.status = infoElement?.getElementsByTagName("status")?.item(0)?.textContent
                    ?: x.getElementsByTagName("status")?.item(0)?.textContent ?: it.status
                x.getElementsByTagName("pinned-message")?.item(0)?.textContent?.let { pinned ->
                    it.pinnedMessage = pinned
                }
                x.getElementsByTagName("anonymous")?.item(0)?.textContent?.let { v ->
                    it.anonymous = v == "true"
                }
                x.getElementsByTagName("parent-chat")?.item(0)?.textContent?.let { v ->
                    it.parentChat = v
                }
                // Parse languages
                val langElements = x.getElementsByTagName("language")
                if (langElements.length > 0) {
                    it.languages.clear()
                    for (i in 0 until langElements.length) {
                        langElements.item(i)?.textContent?.let { lang -> it.languages.add(lang) }
                    }
                }
            }

            // Update RosterStorageItem.nickname so displayName flows to the UI
            if (!parsedName.isNullOrEmpty()) {
                val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find()
                val resolvedRosterItem = if (rosterItem != null) {
                    findLatest(rosterItem)?.apply { nickname = parsedName }
                } else {
                    val primary = RosterStorageItem.genPrimary(jid, owner)
                    copyToRealm(RosterStorageItem().apply {
                        this.primary = primary
                        this.jid = jid
                        this.owner = this@GroupchatManager.owner
                        this.nickname = parsedName
                    }, UpdatePolicy.ALL)
                }

                // Also save group properties into RosterGroupStorageItem
                val groupPrimaryRG = RosterGroupStorageItem.genPrimary(jid, owner)
                val existingRG = query<RosterGroupStorageItem>("primary = $0", groupPrimaryRG).first().find()
                if (existingRG != null) {
                    findLatest(existingRG)?.apply {
                        name = jid
                    }
                } else {
                    copyToRealm(RosterGroupStorageItem().apply {
                        this.primary = groupPrimaryRG
                        this.owner = this@GroupchatManager.owner
                        this.name = jid
                    }, UpdatePolicy.ALL)
                }
                // Link the roster item to this group
                if (resolvedRosterItem != null) {
                    val rg = query<RosterGroupStorageItem>("primary = $0", groupPrimaryRG).first().find()
                    rg?.let { findLatest(it) }?.let { latestRG ->
                        if (!latestRG.contacts.any { it.primary == resolvedRosterItem.primary }) {
                            latestRG.contacts.add(resolvedRosterItem)
                        }
                    }
                }
            }
        }
    }

    private suspend fun createOrUpdateGroupChatFromPresence(presence: Element) {
        val from = presence.getAttribute("from")
        val jid = try { XMPPJID(from).bare() } catch (e: Exception) { return }

        // Determine conversation type from privacy in presence
        val groupEl = presence.getElementsByTagNameNS(NAMESPACE, "group")?.item(0) as? Element
            ?: presence.getElementsByTagNameNS(NAMESPACE, "x")?.item(0) as? Element
        val privacy = groupEl?.getAttribute("privacy")?.takeIf { it.isNotEmpty() }
            ?: groupEl?.getElementsByTagName("privacy")?.item(0)?.textContent
        val parentChat = groupEl?.getAttribute("parent")?.takeIf { it.isNotEmpty() }
            ?: groupEl?.getElementsByTagName("parent-chat")?.item(0)?.textContent
        val conversationType = when (privacy) {
            "incognito" -> if (parentChat != null) ConversationType.Private else ConversationType.Incognito
            else -> ConversationType.Group
        }

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

        val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, conversationType)
        realm.write {
            if (query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find() == null) {
                val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find()
                val chat = LastChatsStorageItem().apply {
                    primary = chatPrimary
                    this.jid = jid
                    this.owner = this@GroupchatManager.owner
                    conversationType_ = conversationType.rawValue
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

    private suspend fun afterLeave(groupchat: String) {
        // Per spec, leaving only removes the user from participants — preserve message history
        realm.write {
            val groupPrimary = GroupChatStorageItem.genPrimary(groupchat, owner)
            // Mark group as left but don't delete it or its messages
            val group = query<GroupChatStorageItem>("primary = $0", groupPrimary).first().find()
            if (group != null) {
                findLatest(group)?.isDeleted = true
            }
            // Clean up member data
            query<GroupchatUserStorageItem>("groupchatId = $0", groupPrimary).find().forEach { delete(it) }
            query<GroupchatInvitedUsersStorageItem>("owner = $0 AND groupchatId = $1", owner, groupPrimary).find().forEach { delete(it) }
        }
    }

    private suspend fun handleInfoResponse(iq: Element, id: String, from: String, type: String) {
        val item = findQueueItem(id) ?: return
        if (type == "result") {
            // Per spec the response uses <x xmlns='NAMESPACE'>; fall back to <query> for safety
            val dataEl = iq.getElementsByTagNameNS(NAMESPACE, "x")?.item(0) as? Element
                ?: iq.getElementsByTagName("query")?.item(0) as? Element
            if (dataEl != null) {
                val jid = try { XMPPJID(from).bare() } catch (e: Exception) { from }
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
                        dataEl.getElementsByTagName("name")?.item(0)?.textContent?.let { v -> it.name = v }
                        dataEl.getElementsByTagName("description")?.item(0)?.textContent?.let { v -> it.descr = v }
                        dataEl.getElementsByTagName("privacy")?.item(0)?.textContent?.let { v -> it.privacy_ = v }
                        dataEl.getElementsByTagName("membership")?.item(0)?.textContent?.let { v -> it.membership_ = v }
                        dataEl.getElementsByTagName("index")?.item(0)?.textContent?.let { v -> it.index_ = v }
                        dataEl.getElementsByTagName("status")?.item(0)?.textContent?.let { v -> it.status = v }
                        dataEl.getElementsByTagName("members")?.item(0)?.textContent?.toIntOrNull()?.let { v -> it.members = v }
                        dataEl.getElementsByTagName("pinned-message")?.item(0)?.textContent?.let { v -> it.pinnedMessage = v }
                        dataEl.getElementsByTagName("parent-chat")?.item(0)?.textContent?.let { v -> it.parentChat = v }
                        dataEl.getElementsByTagName("anonymous")?.item(0)?.textContent?.let { v -> it.anonymous = v == "true" }
                        val langElements = dataEl.getElementsByTagName("language")
                        if (langElements.length > 0) {
                            it.languages.clear()
                            for (i in 0 until langElements.length) {
                                langElements.item(i)?.textContent?.let { lang -> it.languages.add(lang) }
                            }
                        }
                    }
                }
            }
            item.callback?.invoke(null)
        } else {
            item.callback?.invoke("error")
        }
        removeQueueItem(item)
        checkAndRemoveQueryId(id)
    }

    private suspend fun handleDefaultRightsResponse(iq: Element, id: String, from: String, type: String) =
        handleFormResponse(iq, id, type)

    private suspend fun handleRightsResponse(iq: Element, id: String, from: String, type: String) =
        handleFormResponse(iq, id, type)

    private suspend fun handleFormResponse(iq: Element, id: String, type: String) {
        val item = findQueueItem(id) ?: return
        if (type == "result") {
            val x = iq.getElementsByTagNameNS("jabber:x:data", "x")?.item(0) as? Element
            if (x != null) {
                item.settingsCallback?.invoke(parseDataForm(x), null)
            } else {
                item.settingsCallback?.invoke(null, "not a form")
            }
        } else {
            item.settingsCallback?.invoke(null, "error")
        }
        removeQueueItem(item)
        checkAndRemoveQueryId(id)
    }

    // ----------------------------------------------------------------------
    // Helper methods
    // ----------------------------------------------------------------------

    private fun xmlns(action: String): String = "$NAMESPACE#$action"

    private fun scheduleTimeout(elementId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(REQUEST_TIMEOUT_MS)
            findQueueItem(elementId)?.let { item ->
                when {
                    item.settingsCallback != null -> item.settingsCallback.invoke(null, "timeout")
                    item.formCallback != null -> item.formCallback.invoke(null, null, null, "timeout")
                    item.inviteCallback != null -> item.inviteCallback.invoke(item.value, "timeout")
                    else -> item.callback?.invoke("timeout")
                }
                removeQueueItem(item)
                checkAndRemoveQueryId(elementId)
            }
        }
    }

    // Inner helper: update a user card inside an existing MutableRealm transaction (no new realm.write)
    private fun io.realm.kotlin.MutableRealm.updateUserCardInTransaction(
        userId: String,
        groupchat: String,
        trustedSource: Boolean,
        jidValue: String?,
        nicknameValue: String?,
        roleValue: String?,
        subscriptionValue: String?,
        badgeValue: String?,
        presentText: String?,
        avatarUrl: String? = null,
        avatarHash: String? = null
    ) {
        val groupchatId = GroupChatStorageItem.genPrimary(groupchat, owner)
        val primary = GroupchatUserStorageItem.genPrimary(userId, groupchat, owner)

        var user = query<GroupchatUserStorageItem>("primary = $0", primary).first().find()
        if (user == null) {
            user = GroupchatUserStorageItem().apply {
                this.primary = primary
                this.userId = userId
                this.groupchatId = groupchatId
                this.owner = this@GroupchatManager.owner
            }
            copyToRealm(user, UpdatePolicy.ALL)
        } else {
            user = findLatest(user)
        }

        user?.apply {
            jidValue?.let { jid = it }
            nicknameValue?.let { nickname = it }
            roleValue?.let { role_ = it }
            subscriptionValue?.let { subscribtion_ = it }
            badge = badgeValue ?: ""
            avatarUrl?.let { avatarURI = it }
            avatarHash?.let { this.avatarHash = it }
            isOnline = presentText == "now"
            if (presentText != null && presentText != "now") {
                lastSeenIso = presentText
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    lastSeen = sdf.parse(presentText)?.time ?: 0L
                } catch (_: Exception) { }
            }
            updateTimestamp = System.currentTimeMillis()
            isTemporary = !trustedSource

            if (jid == this@GroupchatManager.owner) {
                isMe = true
                val group = query<GroupChatStorageItem>("primary = $0", groupchatId).first().find()
                if (group != null) {
                    findLatest(group)?.myMemberId = userId
                }
            }
        }
    }

    private suspend fun updateUserCardFromElement(
        userEl: Element,
        groupchat: String,
        trustedSource: Boolean,
        commitTransaction: Boolean = true
    ) {
        val id = userEl.getAttribute("id")
        if (id.isNullOrEmpty()) return

        val (avatarUrl, avatarHash) = extractAvatarFromDomElement(userEl)
        realm.write {
            updateUserCardInTransaction(
                userId = id,
                groupchat = groupchat,
                trustedSource = trustedSource,
                jidValue = userEl.getElementsByTagName("jid")?.item(0)?.textContent,
                nicknameValue = userEl.getElementsByTagName("nickname")?.item(0)?.textContent,
                roleValue = userEl.getElementsByTagName("role")?.item(0)?.textContent,
                subscriptionValue = userEl.getElementsByTagName("subscription")?.item(0)?.textContent,
                badgeValue = userEl.getElementsByTagName("badge")?.item(0)?.textContent,
                presentText = userEl.getElementsByTagName("present")?.item(0)?.textContent,
                avatarUrl = avatarUrl,
                avatarHash = avatarHash
            )
        }
    }

    // Batch version: updates multiple DOM Element user cards in a single realm.write
    private suspend fun updateUserCardsFromElements(
        users: List<Element>,
        groupchat: String,
        trustedSource: Boolean
    ) {
        if (users.isEmpty()) return
        realm.write {
            for (userEl in users) {
                val id = userEl.getAttribute("id")
                if (id.isNullOrEmpty()) continue
                val (avatarUrl, avatarHash) = extractAvatarFromDomElement(userEl)
                updateUserCardInTransaction(
                    userId = id,
                    groupchat = groupchat,
                    trustedSource = trustedSource,
                    jidValue = userEl.getElementsByTagName("jid")?.item(0)?.textContent,
                    nicknameValue = userEl.getElementsByTagName("nickname")?.item(0)?.textContent,
                    roleValue = userEl.getElementsByTagName("role")?.item(0)?.textContent,
                    subscriptionValue = userEl.getElementsByTagName("subscription")?.item(0)?.textContent,
                    badgeValue = userEl.getElementsByTagName("badge")?.item(0)?.textContent,
                    presentText = userEl.getElementsByTagName("present")?.item(0)?.textContent,
                    avatarUrl = avatarUrl,
                    avatarHash = avatarHash
                )
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

        val (avatarUrl, avatarHash) = extractAvatarFromXmlElement(userEl)
        realm.write {
            updateUserCardInTransaction(
                userId = id,
                groupchat = groupchat,
                trustedSource = trustedSource,
                jidValue = userEl.element("jid")?.textContent,
                nicknameValue = userEl.element("nickname")?.textContent,
                roleValue = userEl.element("role")?.textContent,
                subscriptionValue = userEl.element("subscription")?.textContent,
                badgeValue = userEl.element("badge")?.textContent,
                presentText = userEl.element("present")?.textContent,
                avatarUrl = avatarUrl,
                avatarHash = avatarHash
            )
        }
    }

    // Batch version: updates multiple XMLElement user cards in a single realm.write
    private suspend fun updateUserCards(
        users: List<Pair<XMLElement, String>>, // pair of (userElement, groupchat)
        trustedSource: Boolean
    ) {
        if (users.isEmpty()) return
        realm.write {
            for ((userEl, groupchat) in users) {
                val id = userEl.getAttribute("id")
                if (id.isNullOrEmpty()) continue
                val (avatarUrl, avatarHash) = extractAvatarFromXmlElement(userEl)
                updateUserCardInTransaction(
                    userId = id,
                    groupchat = groupchat,
                    trustedSource = trustedSource,
                    jidValue = userEl.element("jid")?.textContent,
                    nicknameValue = userEl.element("nickname")?.textContent,
                    roleValue = userEl.element("role")?.textContent,
                    subscriptionValue = userEl.element("subscription")?.textContent,
                    badgeValue = userEl.element("badge")?.textContent,
                    presentText = userEl.element("present")?.textContent,
                    avatarUrl = avatarUrl,
                    avatarHash = avatarHash
                )
            }
        }
    }

    // Extract avatar URL and hash from a DOM user element's <avatar>/<info> or <avatar>/<metadata>/<info>
    private fun extractAvatarFromDomElement(userEl: Element): Pair<String?, String?> {
        val avatarEl = userEl.getElementsByTagName("avatar")?.item(0) as? Element ?: return null to null
        // New format: <avatar><info xmlns='urn:xmpp:avatar:metadata' url='...' id='...'/>
        val infoEl = avatarEl.getElementsByTagName("info")?.item(0) as? Element
        // Legacy format: <avatar><metadata xmlns='urn:xmpp:avatar:metadata'><info .../>
            ?: (avatarEl.getElementsByTagName("metadata")?.item(0) as? Element)
                ?.let { it.getElementsByTagName("info")?.item(0) as? Element }
        val url = infoEl?.getAttribute("url")?.takeIf { it.isNotEmpty() }
        val hash = infoEl?.getAttribute("id")?.takeIf { it.isNotEmpty() }
        return url to hash
    }

    // Extract avatar URL and hash from an XMLElement user element
    private fun extractAvatarFromXmlElement(userEl: XMLElement): Pair<String?, String?> {
        val avatarEl = userEl.element("avatar") ?: return null to null
        val infoEl = avatarEl.element("info")
            ?: avatarEl.element("metadata")?.element("info")
        val url = infoEl?.getAttribute("url")?.takeIf { it.isNotEmpty() }
        val hash = infoEl?.getAttribute("id")?.takeIf { it.isNotEmpty() }
        return url to hash
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
        mutex.withLock { queueItems.clear() }
    }

    fun close() {
        realm.close()
    }
}