package com.xabber.data_base.models.messages

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.CommonConfigManager
import com.xabber.common.SettingManager
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.roster.Subscription
import com.xabber.utils.prp
import com.xabber.utils.toMap
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
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
import io.realm.kotlin.types.annotations.PrimaryKey
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
    }

    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var opponent: String = ""
    var body: String = ""
    var legacyBody: String = ""
    var date: Long = 0
    var sentDate: Long = 0L
    var editDate: Long = 0L
    var readDate: Long? = null
    var outgoing: Boolean = false
    var isRead: Boolean = outgoing
    var displayAs_: String = ""
    var messageId: String = ""
    var trustedSource: Boolean = false
    var previousId: String? = null
    var archivedId: String = ""
    var isDeleted: Boolean = false
    var state_: Int = MessageSendingState.None.rawValue
    var systemMetadata_: String? = null
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var messageError: String? = null
    var messageErrorCode: String? = null
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

    var state: com.xabber.data_base.models.messages.MessageSendingState
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

    fun configureSystemMessage(message: XMPPMessage, owner: String, opponent: String, date: Date) {
        this.owner = owner
        this.opponent = opponent
        this.body = message.body ?: ""
        this.date = date.time
        this.sentDate = date.time
        this.conversationType = conversationTypeByMessage(message)
        this.messageId = message.id ?: ""
        this.archivedId = message.element("archived", namespace = "urn:xmpp:mam:tmp")?.getAttribute("id") ?: ""
        this.displayAs = "system"
        this.state = MessageSendingState.None
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
        this.body = message.body ?: ""
        this.legacyBody = message.body ?: ""
        this.date = date.time
        this.sentDate = date.time
        this.outgoing = outgoing
        this.isRead = isRead
        this.messageId = message.id ?: ""
        this.conversationType = conversationTypeByMessage(message)
        this.archivedId = message.element("archived", namespace = "urn:xmpp:mam:tmp")?.getAttribute("id") ?: ""
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun save(
        realm: Realm,
        commitTransaction: Boolean = true,
        silentNotifications: Boolean = false
    ): Boolean {
        if (opponent.isBlank()) return false

//        val autoDeleteInterval = CommonConfigManager.config.auto_delete_messages_interval
//        if (autoDeleteInterval > 0 && date < System.currentTimeMillis() - autoDeleteInterval * 1000L) {
//            return false
//        }

        if (primary.isBlank()) updatePrimary()

        return try {
            val existing = realm.query<MessageStorageItem>("primary == $0", primary).first().find()
            if (existing != null) {
                if (trustedSource && !existing.trustedSource) {
                    if (commitTransaction) {
                        realm.writeBlocking {
                            if (archivedId.isNotBlank()) existing.archivedId = this@MessageStorageItem.archivedId
                            existing.trustedSource = true
                            existing.previousId = this@MessageStorageItem.previousId
                        }
                    } else {
                        if (archivedId.isNotBlank()) existing.archivedId = archivedId
                        existing.trustedSource = true
                        existing.previousId = previousId
                    }
                }

                if (queryIds?.contains("history") == true) {
                    if (commitTransaction) {
                        realm.writeBlocking {
                            val old = existing.queryIds.orEmpty()
                            val new = this@MessageStorageItem.queryIds.orEmpty()
                            existing.queryIds = if (old.isNotEmpty() && new.isNotEmpty()) "$old,$new" else old + new
                        }
                    } else {
                        val old = existing.queryIds.orEmpty()
                        val new = queryIds.orEmpty()
                        existing.queryIds = if (old.isNotEmpty() && new.isNotEmpty()) "$old,$new" else old + new
                    }
                }
                return false
            }

            val lastChatPrimary = LastChatsStorageItem.genPrimary(opponent, owner, conversationType)
            var lastChat = realm.query<LastChatsStorageItem>("primary == $0", lastChatPrimary).first().find()
            val isNewChat = lastChat == null
            if (isNewChat) {
                lastChat = LastChatsStorageItem().apply {
                    jid = opponent
                    this.owner = this@MessageStorageItem.owner
                    conversationType = this@MessageStorageItem.conversationType
                    primary = lastChatPrimary
                }
            }

            var shouldNotify = false

            if (commitTransaction) {
                realm.writeBlocking {
                    val message = copyToRealm(this@MessageStorageItem, UpdatePolicy.ALL)

                    val lastMessageDate = lastChat!!.lastMessage?.date ?: 0L
                    if (lastMessageDate > message.date) {
                        message.isRead = true
                        if (message.outgoing && message.archivedId.isNotBlank()) {
                            val archivedTime = message.archivedId.toLongOrNull() ?: 0L
                            lastChat!!.deliveredId?.toLongOrNull()?.let { if (it > archivedTime) message.state = MessageSendingState.Deliver }
                            lastChat!!.displayedId?.toLongOrNull()?.let { if (it > archivedTime) message.state = MessageSendingState.Read }
                        }
                    } else {
                        shouldNotify = true
                        lastChat!!.apply {
                            messageDate = message.sentDate
                            if (!message.isDeleted) lastMessage = message
                            lastMessageId = message.messageId

                            val timer = message.references.firstOrNull()?.metadata?.get("ephemeral-timer") as? Int
                            if (timer != null) {
                                afterburnIntervalLastUpdate = message.date / 1000.0
                                afterburnInterval = timer.toDouble()
                            } else if (message.afterburnInterval > -1 && afterburnIntervalLastUpdate < message.date / 1000.0) {
                                afterburnIntervalLastUpdate = message.date / 1000.0
                                afterburnInterval = message.afterburnInterval.toDouble()
                            }

                            if (!message.isRead && !message.outgoing && message.forceUnreadState != true) unread += 1
                            else if (message.outgoing) unread = 0

                            if (isArchived && !isMuted) isArchived = false

                            val rosterPrimary = RosterStorageItem.genPrimary(message.opponent, message.owner)
                            val rosterItem = query<RosterStorageItem>("primary == $0", rosterPrimary).first().find()
                            if (rosterItem != null) this.rosterItem = rosterItem
                        }
                    }

                    if (isNewChat) {
                        val rosterPrimary = RosterStorageItem.genPrimary(opponent, owner)
                        var rosterItem = query<RosterStorageItem>("primary == $0", rosterPrimary).first().find()
                        if (rosterItem == null) {
                            rosterItem = RosterStorageItem().apply {
                                jid = opponent
                                this.owner = this@MessageStorageItem.owner
                                subscription = Subscription.UNDEFINED
                                primary = rosterPrimary
                            }
                            copyToRealm(rosterItem, UpdatePolicy.ALL)
                        }
                        lastChat!!.rosterItem = rosterItem
                    }

                    copyToRealm(lastChat!!, UpdatePolicy.ALL)
                }
            } else {
                realm.writeBlocking {
                    shouldNotify = true
                    copyToRealm(this as RealmObject, UpdatePolicy.ALL)
                    lastChat!!.apply {
                        messageDate = sentDate
                        if (!isDeleted) lastMessage = this@MessageStorageItem
                        lastMessageId = messageId

                        val timer =
                            references.firstOrNull()?.metadata?.get("ephemeral-timer") as? Int
                        if (timer != null) {
                            afterburnIntervalLastUpdate = date / 1000.0
                            afterburnInterval = timer.toDouble()
                        } else if (afterburnInterval > -1 && afterburnIntervalLastUpdate < date / 1000.0) {
                            afterburnIntervalLastUpdate = date / 1000.0
                            afterburnInterval = this@MessageStorageItem.afterburnInterval.toDouble()
                        }

                        if (!isRead && !outgoing && forceUnreadState != true) unread += 1
                        else if (outgoing) unread = 0

                        if (isArchived && !isMuted) isArchived = false

                        val rosterPrimary = RosterStorageItem.genPrimary(opponent, owner)
                        val rosterItem =
                            realm.query<RosterStorageItem>("primary == $0", rosterPrimary).first()
                                .find()
                        if (rosterItem != null) this.rosterItem = rosterItem
                    }

                    if (isNewChat) {
                        val rosterPrimary = RosterStorageItem.genPrimary(opponent, owner)
                        var rosterItem =
                            realm.query<RosterStorageItem>("primary == $0", rosterPrimary).first()
                                .find()
                        if (rosterItem == null) {
                            rosterItem = RosterStorageItem().apply {
                                jid = opponent
                                owner = this@MessageStorageItem.owner
                                subscription = Subscription.UNDEFINED
                                primary = rosterPrimary
                            }
                            copyToRealm(rosterItem!!, UpdatePolicy.ALL)
                        }
                        lastChat!!.rosterItem = rosterItem
                    }

                    copyToRealm(lastChat!!, UpdatePolicy.ALL)
                }
            }

            if (!silentNotifications && shouldNotify && !isRead && !outgoing && archivedId.isNotBlank()) {
                if (date >= System.currentTimeMillis() - 10000) {
                    NotifyManager.shared.update(
                        context = applicationContext(),
                        message = body.takeIf { it.isNotBlank() } ?: "New message",
                        messageId = archivedId,
                        username = null,
                        opponent = opponent,
                        owner = owner,
                        date = Date(date),
                        displayName = "",      // или имя контакта, если доступно
                        imageUrl = null,       // или URL аватарки
                        conversationType = conversationType
                    )
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message $primary", e)
            false
        }
    }

    // Вынесена только одна внутренняя функция — чтобы не дублировать код
    private fun doSaveInsideTransaction(
        realm: MutableRealm,
        lastChat: LastChatsStorageItem,
        isNewChat: Boolean,
        shouldNotify: Boolean
    ) {
        realm.copyToRealm(this@MessageStorageItem, UpdatePolicy.ALL)

        val lastMessageDate = lastChat.lastMessage?.date ?: 0L
        if (lastMessageDate > this@MessageStorageItem.date) {
            this@MessageStorageItem.isRead = true
            if (outgoing && archivedId.isNotBlank()) {
                val archivedTime = archivedId.toLongOrNull() ?: 0L
                lastChat.deliveredId?.toLongOrNull()?.let { if (it > archivedTime) state = MessageSendingState.Deliver }
                lastChat.displayedId?.toLongOrNull()?.let { if (it > archivedTime) state = MessageSendingState.Read }
            }
        } else {
            lastChat.apply {
                messageDate = this@MessageStorageItem.sentDate
                if (!this@MessageStorageItem.isDeleted) lastMessage = this@MessageStorageItem
                lastMessageId = this@MessageStorageItem.messageId

                val timer = this@MessageStorageItem.references.firstOrNull()?.metadata?.get("ephemeral-timer") as? Int
                if (timer != null) {
                    afterburnIntervalLastUpdate = this@MessageStorageItem.date / 1000.0
                    afterburnInterval = timer.toDouble()
                } else if (this@MessageStorageItem.afterburnInterval > -1 &&
                    afterburnIntervalLastUpdate < this@MessageStorageItem.date / 1000.0
                ) {
                    afterburnIntervalLastUpdate = this@MessageStorageItem.date / 1000.0
                    afterburnInterval = this@MessageStorageItem.afterburnInterval.toDouble()
                }

                if (!this@MessageStorageItem.isRead && !this@MessageStorageItem.outgoing && this@MessageStorageItem.forceUnreadState != true) {
                    unread += 1
                } else if (this@MessageStorageItem.outgoing) {
                    unread = 0
                }

                if (isArchived && !isMuted) isArchived = false

                val rosterPrimary = RosterStorageItem.genPrimary(opponent, this@MessageStorageItem.owner)
                val rosterItem = realm.query<RosterStorageItem>("primary == $0", rosterPrimary).first().find()
                if (rosterItem != null) this.rosterItem = rosterItem
            }
        }

        if (isNewChat) {
            val rosterPrimary = RosterStorageItem.genPrimary(opponent, owner)
            var rosterItem = realm.query<RosterStorageItem>("primary == $0", rosterPrimary).first().find()
            if (rosterItem == null) {
                rosterItem = RosterStorageItem().apply {
                    jid = opponent
                    this.owner = this@MessageStorageItem.owner
//                    subscribtion = Subscription.undefined
                    primary = rosterPrimary
                }
                realm.copyToRealm(rosterItem, UpdatePolicy.ALL)
            }
            lastChat.rosterItem = rosterItem
        }

        realm.copyToRealm(lastChat, UpdatePolicy.ALL)
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

    fun toMessageDto(): MessageDto? = try {
        val sentTimestamp = sentDate
        Log.d(TAG, "toMessageDto: primary=$primary, sentDate=$sentDate, sentTimestamp=$sentTimestamp, formatted=${Date(sentTimestamp)}")

        MessageDto(
            primary = primary,
            isOutgoing = outgoing,
            owner = owner,
            opponentJid = opponent,
            messageBody = body ?: "",
            messageSendingState = when {
                isRead -> MessageSendingState.Read
                outgoing -> MessageSendingState.Deliver
                else -> MessageSendingState.Sent
            },
            sentTimestamp = sentTimestamp,
            editTimestamp = editDate,
            displayType = when {
                conversationType_ == "https://xabber.com/protocol/groups#system-message" -> MessageDisplayType.System
                body.isNullOrEmpty() && references.isNotEmpty() -> MessageDisplayType.Images
                else -> MessageDisplayType.Text
            },
            canEditMessage = outgoing,
            canDeleteMessage = outgoing,
            urlAvatar = null,
            isGroup = conversationType_ == "https://xabber.com/protocol/groups",
            kind = null,
            isSelected = false,
            references = references.mapNotNull { ref ->
                try {
                    MessageReferenceDto(
                        id = ref.primary,
                        uri = ref.uri,
                        mimeType = ref.mimeType,
                        isGeo = ref.isGeo,
                        latitude = ref.latitude,
                        longitude = ref.longitude,
                        isVoiceMessage = ref.isAudioMessage,
                        fileName = ref.fileName,
                        size = ref.fileSize
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to map reference: ${e.message}")
                    null
                }
            } as ArrayList<MessageReferenceDto>,
            isUnread = !isRead,
            isChecked = false,
            archivedId = archivedId
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create MessageDto: ${e.message}")
        null
    }
}