package com.xabber.data_base.models.messages

import android.util.Log
import com.xabber.utils.prp
import com.xabber.utils.toMap
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date
import org.json.JSONObject

class MessageStorageItem : RealmObject {
    enum class MessageSendingState(val value: Int) {
        SENT(0),
        DELIVERED(1),
        READ(2),
        ERROR(3),
        NONE(4),
        NOT_SENT(5),
        SENDING(6),
        UPLOADING(7)
    }

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
    var state_: Int = MessageSendingState.NONE.value
    var systemMetadata_: String? = null
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var messageError: String? = null
    var messageErrorCode: String? = null
    var conversationType_: String = ConversationType.Regular.rawValue
    var inlineForwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()
    var errorMetadata_: String? = null // Changed from "" to null
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

    var state: MessageSendingState
        get() = MessageSendingState.entries.firstOrNull { it.value == state_ } ?: MessageSendingState.NONE
        set(newValue) {
            state_ = newValue.value
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
    }

    fun save(realm: MutableRealm, silentNotifications: Boolean): Boolean {
        return try {
            realm.copyToRealm(this, UpdatePolicy.ALL)
            Log.d(TAG, "Successfully saved message: primary=${this.primary}, messageId=${this.messageId}, owner=${this.owner}, opponent=${this.opponent}, body=${this.body}, state=${this.state}, isRead=${this.isRead}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving message ${this.primary}: ${e.message}", e)
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
            Log.d(TAG, "Successfully stored stanza for message: primary=${this.primary}, messageId=${this.messageId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing stanza for message ${this.primary}: ${e.message}")
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
            Log.d("MessageCommonReceiver", "Determined conversationType=${it.rawValue} for messageId=${message.id}, to=$to")
        }
    }
}