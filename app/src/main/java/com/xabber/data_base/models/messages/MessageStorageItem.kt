package com.xabber.data_base.models.messages

import android.util.Log
import com.xabber.utils.prp
import com.xabber.utils.toMap
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
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
    var sentDate: Long = 0
    var editDate: Long = 0
    var readDate: Double? = null
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
    var afterburnInterval: Double = -1.0
    var burnDate: Double = -1.0
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
        Log.d(TAG, "Configured incoming message: primary=$primary, messageId=$messageId, sentDate=$sentDate, date=${Date(sentDate)}")
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

        Log.d(TAG, "Configured outgoing message: primary=$primary, messageId=$messageId, body=$body, opponent=$opponent")
    }

    fun save(realm: MutableRealm, silentNotifications: Boolean): Boolean {
        return try {
            realm.copyToRealm(this, UpdatePolicy.ALL)
            Log.d(TAG, "Successfully saved message: primary=$primary, messageId=$messageId, owner=$owner, opponent=$opponent, body=$body, state=$state, isRead=$isRead")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving message $primary: ${e.message}", e)
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
            Log.d(TAG, "Successfully stored stanza for message: primary=$primary, messageId=$messageId")
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
        val TAG = "TO MSI"
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
            sentTimestamp = sentDate,
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
            isSelected = false, // Managed separately
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
            isChecked = false, // Managed separately
            archivedId = archivedId
        ).also {
            Log.d(TAG, "Mapped storage item to DTO: primary=${it.primary}, archivedId=${archivedId}, isUnread=${it.isUnread}")
        }
    } catch (e: Exception) {
        Log.e("TO MSI", "Failed to map MessageStorageItem to MessageDto: primary=$primary, error=${e.message}")
        null
    }
}