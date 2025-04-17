package com.xabber.xmpp.messages

import android.content.Context
import android.util.Log
import com.xabber.R
import com.xabber.utils.prp
import com.xabber.utils.toMap
import com.xabber.xmpp.groupchat.GroupchatUserStorageItem
import com.xabber.xmpp.last_chats.LastChatsStorageItem
import com.xabber.xmpp.messages.message.MessageForwardsInlineStorageItem
import com.xabber.xmpp.messages.message.MessageReferenceStorageItem
import com.xabber.xmpp.messages.message.XMPPMessage
import com.xabber.xmpp.notifications.toMap
import com.xabber.xmpp.roster.Ask
import com.xabber.xmpp.roster.RosterGroupStorageItem
import com.xabber.xmpp.roster.RosterStorageItem
import com.xabber.xmpp.roster.Subscription
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.json.JSONObject
import java.util.Date


open class MessageStorageItem : RealmObject {
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

    @Index
    var owner: String = ""

    @Index
    var opponent: String = ""

    @Index
    var body: String = ""

    var legacyBody: String = ""

    @Index
    var date: Date = Date()

    var sentDate: Date = Date()
    var editDate: Date? = null
    var outgoing: Boolean = false
    var isRead: Boolean = false

    @Index
    private var messageType: Int = MessageDisplayType.TEXT.rawValue

    @Index
    var messageId: String = ""

    var trustedSource: Boolean = false
    var previousId: String? = null
    var queryIds: String? = null

    @Index
    var archivedId: String = ""

    var isDeleted: Boolean = false
    private var stateRaw: Int = 0
    var groupchatCard: GroupchatUserStorageItem? = null
    var envelopeContainer: String? = null
    var afterburnInterval: Double = -1.0
    var burnDate: Double = -1.0
    var readDate: Double = -1.0
    private var errorMetadataRaw: String? = null
    private var systemMetadataRaw: String? = null
    var messageError: String? = null
    var messageErrorCode: String? = null
    private var conversationTypeRaw: String = ConversationType.REGULAR.rawValue
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var inlineForwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()

    @Ignore
    var forceUnreadState: Boolean? = null

    @Ignore
    var isInvite: Boolean = false

    @Ignore
    var originalStanza: XMPPMessage? = null


    var displayAs: MessageDisplayType
        get() = MessageDisplayType.fromRaw(messageType)
        set(value) {
            messageType = value.rawValue
        }


    var state: MessageSendingState
        get() = if (displayAs == MessageDisplayType.SYSTEM) {
            MessageSendingState.NONE
        } else {
            MessageSendingState.fromRaw(stateRaw)
        }
        set(value) {
            stateRaw = value.rawValue
        }


    var conversationType: ConversationType
        get() = ConversationType.fromRaw(conversationTypeRaw)
        set(value) {
            conversationTypeRaw = value.rawValue
        }


    val isHasAttachedMessages: Boolean
        get() = false


    val groupchatMetadata: Map<String, Any>?
        get() = references.find { it.kind == ReferenceKind.GROUPCHAT }?.metadata


    val groupchatAuthorId: String?
        get() = if (displayAs == MessageDisplayType.SYSTEM) {
            null
        } else {
            groupchatCard?.userId ?: groupchatMetadata?.get("id") as? String
        }


    val groupchatAuthorNickname: String?
        get() = if (displayAs == MessageDisplayType.SYSTEM) {
            null
        } else {
            groupchatCard?.nickname ?: (groupchatMetadata?.get("nickname") as? String
                ?: groupchatMetadata?.get("jid") as? String)
        }


    val groupchatAuthorBadge: String?
        get() {
            val role = groupchatCard?.role?.localized() ?: (groupchatMetadata?.get("role") as? String)
            val badge = groupchatCard?.badge ?: (groupchatMetadata?.get("badge") as? String ?: "")
            return if (role?.lowercase() == "member") {
                badge
            } else {
                if (badge.isNotEmpty()) badge else role?.replaceFirstChar { it.uppercase() }
            }
        }

    val groupchatDisplayedNickname: String?
        get() = groupchatAuthorNickname?.let {
            if (displayAs != MessageDisplayType.SYSTEM) {
                if (outgoing) "You:" else it
            } else null
        }


    val groupchatUserAvatarPath: String?
        get() = (groupchatMetadata?.get("avatar_uri") as? String)?.let {
            listOf(it, opponent).prp()
        }


    val callMetadata: Map<String, Any>?
        get() = references.find { it.kind == ReferenceKind.CALL }?.metadata


    var errorMetadata: Map<String, Any>?
        get() = errorMetadataRaw?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse error metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            errorMetadataRaw = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode error metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }


    var systemMetadata: Map<String, Any>?
        get() = systemMetadataRaw?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse system metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            systemMetadataRaw = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode system metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }







}

/**
 * Display type for messages.
 */
enum class MessageDisplayType(val rawValue: Int) {
    TEXT(0),
    FILES(1),
    IMAGES(2),
    VOICE(3),
    CALL(4),
    SYSTEM(5),
    STICKER(6),
    QUOTE(7),
    INITIAL(8);

    companion object {
        fun fromRaw(raw: Int): MessageDisplayType =
            values().find { it.rawValue == raw } ?: TEXT
    }
}

/**
 * Sending state for messages.
 */
enum class MessageSendingState(val rawValue: Int) {
    SENDED(0),
    DELIVER(1),
    READ(2),
    ERROR(3),
    NONE(4),
    NOT_SENDED(5),
    SENDING(6),
    UPLOADING(7);

    companion object {
        fun fromRaw(raw: Int): MessageSendingState =
            values().find { it.rawValue == raw } ?: NONE
    }
}


enum class VoIPCallState(val rawValue: String) {
    MISSED("missed"),
    NOANSWER("noanswer"),
    MADE("made"),
    BUSY("busy"),
    RECEIVED("received"),
    NONE("none");

    companion object {
        fun fromRaw(raw: String): VoIPCallState =
            values().find { it.rawValue == raw } ?: NONE
    }
}


enum class ConversationType(val rawValue: String) {
    REGULAR("regular"),
    OMEMO("omemo"),
    OMEMO1("omemo1"),
    AXOLOTL("axolotl"),
    GROUP("group");

    companion object {
        fun fromRaw(raw: String): ConversationType =
            values().find { it.rawValue == raw } ?: REGULAR
    }
}

enum class ReferenceKind(val xmlType: String) {
    MEDIA("media"),
    VOICE("voice"),
    CALL("call"),
    QUOTE("quote"),
    GROUPCHAT("groupchat"),
    SYSTEM_MESSAGE("systemMessage"),
    FORWARD("forward"),
    MARKUP("markup"),
    MENTION("mention"),
    NONE("none");

    companion object {
        fun fromXmlType(type: String): ReferenceKind =
            values().find { it.xmlType == type } ?: NONE
    }
}


enum class MimeIconType(val rawValue: String) {
    FILE("file"),
    ARCHIVE("archive"),
    DOCUMENT("document"),
    PDF("pdf"),
    PRESENTATION("presentation"),
    VIDEO("video"),
    AUDIO("audio"),
    IMAGE("image");

    companion object {
        fun fromRaw(raw: String): MimeIconType =
            values().find { it.rawValue == raw } ?: FILE
    }
}

