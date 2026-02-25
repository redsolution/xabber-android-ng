package com.xabber.data_base.models.messages

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.CommonConfigManager
import com.xabber.common.SettingManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.roster.Subscription
import com.xabber.utils.prp
import com.xabber.utils.toMap
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.presentation.XabberApplication.Companion.applicationContext
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import com.xabber.xmpp.notifications.NotifyManager
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import java.util.Date

class MessageStorageItem : RealmObject {

    companion object {
        private const val TAG = "MessageStorageItem"

        const val ADD_CONTACT_LOCAL_ARCHIVED_ID = "add-contact-local-archived-id"

        fun messageIdForAuthRequest(jid: String): String {
            return listOf("subscribtion", jid).prp()
        }

        fun messageIdForContact(owner: String, jid: String, ts: String): String {
            return listOf("contact", ts, jid, owner).prp()
        }

        fun messageIdForVoIPCall(owner: String, jid: String, callId: String): String {
            return listOf("voip", owner, jid, callId).prp()
        }

        fun messageIdForInitial(jid: String, conversationType: ConversationType): String {
            return listOf(jid, conversationType.rawValue, "initial_message").prp()
        }

        fun genPrimary(messageId: String, owner: String): String {
            return "${messageId}_$owner"
        }

        /** Strips "nickname:\n    text" → "text" for group messages */
        fun stripGroupNicknamePrefix(body: String): String {
            val colonNewline = body.indexOf(":\n")
            if (colonNewline < 0) return body
            return body.substring(colonNewline + 2).trimStart()
        }
    }

    @PrimaryKey
    var primary: String = ""
    @Index
    var owner: String = ""
    @Index
    var opponent: String = ""
    var body: String = ""
    var legacyBody: String = ""
    var date: Long = 0
    @Index
    var sentDate: Long = 0L
    var editDate: Long = 0L
    var readDate: Long? = null
    var outgoing: Boolean = false
    var isRead: Boolean = false
    var displayAs_: String = ""
    var messageId: String = ""
    var trustedSource: Boolean = false
    var previousId: String? = null
    var archivedId: String = ""
    @Index
    var isDeleted: Boolean = false
    var state_: Int = MessageSendingState.None.rawValue
    var systemMetadata_: String? = null
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var messageError: String? = null
    var messageErrorCode: String? = null
    @Index
    var conversationType_: String = ConversationType.Regular.rawValue
    var inlineForwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()
    var errorMetadata_: String? = null
    var afterburnInterval: Long = -1
    var burnDate: Long = -1
    var forceUnreadState: Boolean? = null
    var queryIds: String? = null
    var envelopeContainer: String? = null

    var errorMetadata: Map<String, Any>?
        get() = errorMetadata_?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse error metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            errorMetadata_ = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode error metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }

    var systemMetadata: Map<String, Any>?
        get() = systemMetadata_?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse system metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            systemMetadata_ = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode system metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }

    var state: MessageSendingState
        get() = com.xabber.data_base.models.messages.MessageSendingState.entries.firstOrNull { it.rawValue == state_ } ?: com.xabber.data_base.models.messages.MessageSendingState.None
        set(newValue) {
            state_ = newValue.rawValue
        }

    var conversationType: ConversationType
        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
        set(newValue) {
            conversationType_ = newValue.rawValue
        }

    var displayAs: String
        get() = displayAs_
        set(newValue) {
            displayAs_ = newValue
        }

    // Cached groupchat reference fields — avoids repeated RealmList traversal + JSON parsing during scroll
    @Ignore
    private var _groupchatRefCached = false
    @Ignore
    private var _groupchatAuthorId: String? = null
    @Ignore
    private var _groupchatAuthorNickname: String? = null
    @Ignore
    private var _groupchatAuthorBadge: String? = null
    @Ignore
    private var _groupchatAuthorJid: String? = null

    private fun ensureGroupchatRefCached() {
        if (_groupchatRefCached) return
        val ref = references.firstOrNull { it.kind_ == "groupchat" }
        val meta = ref?.metadata
        _groupchatAuthorId = meta?.get("id") as? String
        _groupchatAuthorNickname = meta?.get("nickname") as? String
        _groupchatAuthorBadge = meta?.get("badge") as? String
        _groupchatAuthorJid = meta?.get("jid") as? String
        _groupchatRefCached = true
    }

    val groupchatAuthorNickname: String?
        get() { ensureGroupchatRefCached(); return _groupchatAuthorNickname }

    val groupchatAuthorBadge: String?
        get() { ensureGroupchatRefCached(); return _groupchatAuthorBadge }

    val groupchatAuthorJid: String?
        get() { ensureGroupchatRefCached(); return _groupchatAuthorJid }

    val groupchatAuthorId: String?
        get() { ensureGroupchatRefCached(); return _groupchatAuthorId }

    val groupchatDisplayedNickname: String?
        get() {
            if (displayAs == "system") return null
            val nick = groupchatAuthorNickname
            return if (outgoing) "You:" else nick
        }

    fun configureSystemMessage(message: XMPPMessage, owner: String, opponent: String, date: Date) {
        this.owner = owner
        this.opponent = opponent
        this.body = message.body ?: ""
        this.date = date.time
        this.sentDate = date.time
        this.conversationType = conversationTypeByMessage(message)
        this.messageId = message.id ?: ""
        this.archivedId = message.element("archived", "urn:xmpp:mam:tmp")?.getAttribute("id")
            ?: message.element("stanza-id")?.getAttribute("id")
                    ?: message.element("archived")?.getAttribute("id")
                    ?: archivedId
        this.displayAs = "system"
        this.state = MessageSendingState.None
        // Parse #system-message type and version (XEP-0GGG spec)
        val systemX = message.element("x", "https://xabber.com/protocol/groups#system-message")
        if (systemX != null) {
            val sysType = systemX.getAttribute("type") ?: ""
            val sysVersion = systemX.getAttribute("version") ?: ""
            this.systemMetadata_ = "{\"type\":\"$sysType\",\"version\":\"$sysVersion\"}"
        }
        updatePrimary()
        Log.d(TAG, "Configured system message: primary=$primary, messageId=$messageId")
    }

    fun configureIncomingMessage(
        message: XMPPMessage,
        owner: String,
        opponent: String,
        outgoing: Boolean,
        isRead: Boolean,
        date: Date,
        isEncrypted: Boolean
    ) {
        this.owner = owner
        this.opponent = opponent
        // For group headline messages, body is inside <x>/<forwarded>/<message>
        val effectiveBody = message.body ?: run {
            val xEl = message.element("x", "https://xabber.com/protocol/groups")
            val fwd = xEl?.element("forwarded", "urn:xmpp:forward:0")
            val innerMsg = fwd?.element("message")
            innerMsg?.element("body")?.textContent
        } ?: ""
        val isGroupMessage = message.hasElement("x", "https://xabber.com/protocol/groups")
        val cleanBody = if (outgoing && isGroupMessage) {
            stripGroupNicknamePrefix(effectiveBody)
        } else {
            effectiveBody
        }
        this.body = cleanBody
        this.legacyBody = cleanBody
        this.date = date.time
        this.sentDate = date.time
        this.outgoing = outgoing
        this.isRead = isRead
        this.messageId = message.id ?: ""
        this.conversationType = conversationTypeByMessage(message)
        this.archivedId = message.element("archived", "urn:xmpp:mam:tmp")?.getAttribute("id")
            ?: message.element("stanza-id")?.getAttribute("id")
                    ?: message.element("archived")?.getAttribute("id")
                    ?: archivedId
        if (isEncrypted) {
            this.body = "Processing encrypted message..."
            this.legacyBody = this.body
            this.conversationType = ConversationType.Omemo
        }
        updatePrimary()
    }

    fun configureOutgoingMessage(
        body: String,
        legacyBody: String,
        messageId: String,
        owner: String,
        opponent: String,
        references: RealmList<MessageReferenceStorageItem>,
        inlineForwards: RealmList<MessageForwardsInlineStorageItem>
    ) {
        this.body = body
        this.legacyBody = legacyBody
        this.messageId = messageId
        this.owner = owner
        this.opponent = opponent
        this.outgoing = true
        this.isRead = true
        this.date = System.currentTimeMillis()
        this.sentDate = this.date
        this.state = com.xabber.data_base.models.messages.MessageSendingState.NotSent
        this.conversationType = ConversationType.Regular // Default; updated by caller if needed
        this.references = references
        this.inlineForwards = inlineForwards
        this.queryIds = "runtime_send"
        updatePrimary()

        // Update references with messageId and sentDate
        references.forEach {
            it.messageId = this.primary
            it.sentDate = this.date.toDouble()
            // Encryption metadata skipped (not implemented in Kotlin codebase)
        }

    }

    // Save message inside an existing MutableRealm transaction (for batching multiple saves)
    @RequiresApi(Build.VERSION_CODES.O)
    fun saveInTransaction(mutableRealm: io.realm.kotlin.MutableRealm) {
        if (opponent.isBlank() || owner.isBlank()) return
        if (primary.isBlank()) updatePrimary()

        with(mutableRealm) {
            val existing = query<MessageStorageItem>("primary == $0", primary).first().find()

            if (existing != null) {
                var updated = false
                if (trustedSource && !existing.trustedSource) {
                    existing.trustedSource = true
                    existing.previousId = previousId
                    existing.archivedId = archivedId
                    updated = true
                }
                queryIds?.let { newIds ->
                    val old = existing.queryIds.orEmpty()
                    val combined = if (old.isNotEmpty() && newIds.isNotEmpty()) "$old,$newIds" else old + newIds
                    if (combined != existing.queryIds) {
                        existing.queryIds = combined
                        updated = true
                    }
                }
                return
            }

            val managedMessage = copyToRealm(this@MessageStorageItem, UpdatePolicy.ALL)

            val lastChatPrimary = LastChatsStorageItem.genPrimary(opponent, owner, conversationType)
            val lastChat = query<LastChatsStorageItem>("primary == $0", lastChatPrimary).first().find()
                ?: LastChatsStorageItem().apply {
                    jid = opponent
                    owner = managedMessage.owner
                    conversationType = managedMessage.conversationType
                    primary = lastChatPrimary
                }.also { copyToRealm(it) }

            val lastMessageDate = lastChat.lastMessage?.date ?: 0L
            if (lastMessageDate <= managedMessage.date) {
                lastChat.apply {
                    messageDate = managedMessage.sentDate
                    if (!managedMessage.isDeleted) this.lastMessage = managedMessage
                    lastMessageId = managedMessage.messageId

                    val timer = managedMessage.references.firstOrNull()?.metadata?.get("ephemeral-timer") as? Int
                    if (timer != null) {
                        afterburnIntervalLastUpdate = managedMessage.date / 1000.0
                        afterburnInterval = timer.toDouble()
                    } else if (managedMessage.afterburnInterval > -1 &&
                        afterburnIntervalLastUpdate < managedMessage.date / 1000.0
                    ) {
                        afterburnIntervalLastUpdate = managedMessage.date / 1000.0
                        afterburnInterval = managedMessage.afterburnInterval.toDouble()
                    }

                    if (!managedMessage.isRead && !managedMessage.outgoing && forceUnreadState != true) {
                        unread += 1
                    } else if (managedMessage.outgoing) {
                        unread = 0
                    }

                    if (isArchived && !isMuted) isArchived = false

                    val rosterPrimary = RosterStorageItem.genPrimary(opponent, owner)
                    val rosterItem = query<RosterStorageItem>("primary == $0", rosterPrimary).first().find()
                    if (rosterItem != null) this.rosterItem = rosterItem
                }
            } else {
                managedMessage.isRead = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun save(
        realm: Realm,
        silentNotifications: Boolean = false
    ): Boolean {
        if (opponent.isBlank() || owner.isBlank()) return false
        if (primary.isBlank()) updatePrimary()

        return try {
            realm.write {
                // 1. Ищем существующее сообщение по primary
                val existing = query<MessageStorageItem>("primary == $0", primary).first().find()

                if (existing != null) {
                    // Сообщение уже есть — обновляем только важные поля
                    var updated = false

                    if (trustedSource && !existing.trustedSource) {
                        existing.trustedSource = true
                        existing.previousId = previousId
                        existing.archivedId = archivedId
                        updated = true
                    }
                    queryIds?.let { newIds ->
                        val old = existing.queryIds.orEmpty()
                        val combined = if (old.isNotEmpty() && newIds.isNotEmpty()) {
                            "$old,$newIds"
                        } else {
                            old + newIds
                        }
                        if (combined != existing.queryIds) {
                            existing.queryIds = combined
                            updated = true
                        }
                    }

                    if (updated) {
                        Log.d(TAG, "Updated existing message: primary=$primary, trusted=$trustedSource, queryIds=${existing.queryIds}")
                    }
                    return@write
                }

                // 2. Сообщения нет — создаём новое
                val managedMessage = copyToRealm(this@MessageStorageItem, UpdatePolicy.ALL)

                val lastChatPrimary = LastChatsStorageItem.genPrimary(opponent, owner, conversationType)
                var lastChat = query<LastChatsStorageItem>("primary == $0", lastChatPrimary).first().find()
                    ?: LastChatsStorageItem().apply {
                        jid = opponent
                        owner = owner
                        conversationType = managedMessage.conversationType
                        primary = lastChatPrimary
                    }.also { copyToRealm(it) } // сразу сохраняем в Realm!

                val isNewChat = false
                if (isNewChat) {
                    lastChat = LastChatsStorageItem().apply {
                        jid = opponent
                        owner = owner
                        conversationType = managedMessage.conversationType
                        primary = lastChatPrimary
                    }
                }

                var shouldNotify = false

                val lastMessageDate = lastChat?.lastMessage?.date ?: 0L
                if (lastMessageDate <= managedMessage.date) {
                    shouldNotify = true

                    lastChat?.apply {
                        messageDate = managedMessage.sentDate
                        if (!managedMessage.isDeleted) this.lastMessage = managedMessage
                        lastMessageId = managedMessage.messageId

                        val timer = managedMessage.references.firstOrNull()?.metadata?.get("ephemeral-timer") as? Int
                        if (timer != null) {
                            afterburnIntervalLastUpdate = managedMessage.date / 1000.0
                            afterburnInterval = timer.toDouble()
                        } else if (managedMessage.afterburnInterval > -1 &&
                            afterburnIntervalLastUpdate < managedMessage.date / 1000.0
                        ) {
                            afterburnIntervalLastUpdate = managedMessage.date / 1000.0
                            afterburnInterval = managedMessage.afterburnInterval.toDouble()
                        }

                        if (!managedMessage.isRead && !managedMessage.outgoing && forceUnreadState != true) {
                            unread += 1
                        } else if (managedMessage.outgoing) {
                            unread = 0
                        }

                        if (isArchived && !isMuted) isArchived = false

                        val rosterPrimary = RosterStorageItem.genPrimary(opponent, owner)
                        val rosterItem = query<RosterStorageItem>("primary == $0", rosterPrimary).first().find()
                        if (rosterItem != null) this.rosterItem = rosterItem
                    }
                } else {
                    managedMessage.isRead = true
                }

                // Создаём roster item если новый чат
                if (isNewChat) {
                    val rosterPrimary = RosterStorageItem.genPrimary(opponent, owner)
                    var rosterItem = query<RosterStorageItem>("primary == $0", rosterPrimary).first().find()
                    if (rosterItem == null) {
                        rosterItem = RosterStorageItem().apply {
                            jid = opponent
                            this.owner = owner
                            subscription = Subscription.UNDEFINED
                            primary = rosterPrimary
                        }
                        copyToRealm(rosterItem)
                    }
                    if (lastChat != null) {
                        lastChat.rosterItem = rosterItem
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message $primary", e)
            false
        }
    }


    fun storeStanza(realm: MutableRealm) {
        try {
            val stanza = MessageStanzaStorageItem().apply {
                set(
                    id = messageId,
                    owner = owner,
                    stanza = body, // Using body as stanza; adjust if raw XML is needed
                    date = Date(date),
                    primary = primary
                )
            }
            realm.copyToRealm(stanza, UpdatePolicy.ALL)
        } catch (e: Exception) {
            Log.e(TAG, "Error storing stanza for message $primary: ${e.message}")
        }
    }

    fun updatePrimary() {
        this.primary = genPrimary(this.messageId, this.owner)
    }

    fun conversationTypeByMessage(message: XMPPMessage): ConversationType {
        val to = message.to?.bare()
        return when {
            to == "favorites.redsolution.com" -> ConversationType.Favorites
            message.element("x", namespace = "https://xabber.com/protocol/groups") != null -> ConversationType.Group
            message.element("channel", namespace = "https://xabber.com/protocol/channels") != null -> ConversationType.Channel
            message.element("omemo", namespace = "urn:xmpp:omemo:2") != null -> ConversationType.Omemo
            message.element("omemo", namespace = "urn:xmpp:omemo:1") != null -> ConversationType.Omemo1
            message.element("axolotl", namespace = "eu.siacs.conversations.axolotl") != null -> ConversationType.Axolotl
            message.element("xen", namespace = "urn:xabber:xen:0") != null -> ConversationType.Notifications
            else -> ConversationType.Regular
        }.also {
            Log.d(TAG, "Determined conversationType=${it.rawValue} for messageId=${message.id}, to=$to")
        }
    }
}