package com.xabber.data_base.models.last_chats

import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

class LastChatsStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var jid: String = ""
    var messageDate: Date = Date(0) // Equivalent to Date(timeIntervalSince1970: 0)
    var lastReadMessageDate: Date = Date()
    var rosterItem: RosterStorageItem? = null
    var lastMessage: MessageStorageItem? = null
    var lastMessageId: String = ""
    var isSynced: Boolean = true
    var isInitialArchiveLoaded: Boolean = false
    var isHistoryGapFixedForSession: Boolean = false
    var isArchived: Boolean = false
    var fullArchiveLoaded: Boolean = false
    var retractVersion: String? = null
    var mentionId: String? = null
    var lastReadId: String? = null
    var displayedId: String? = null
    var deliveredId: String? = null
    var lastLoadedMessageHistoryId: String? = null
    var unread: Int = 0
    var isBlocked: Boolean = false // TODO: Consider deprecating as noted in Swift
    var draftMessage: String? = null
    var groupchatMyId: String? = null
    var isPrereaded: Boolean = false
    var pinnedPosition: Double = 0.0
    var isPinned: Boolean = false
    var muteExpired: Double = -1.0
    var afterburnInterval: Double = -1.0
    var afterburnIntervalLastUpdate: Double = -1.0
    var isAllHistoryLoaded: Boolean = false
    var isFreshNotEmptyEncryptedChat: Boolean = false
    var hasErrorInChat: Boolean = false
    var updateTS: Double = 0.0
    var conversationType_: String = ConversationType.OMEMO.rawValue
    var lastChatOffset: Float = 0.0F
    var chatState_: Int = 0
    var chatMarkersSupport: Boolean = false

    @Ignore
    var conversationType: ConversationType
        get() = ConversationType.fromRaw(conversationType_) ?: ConversationType.REGULAR
        set(value) {
            conversationType_ = value.rawValue
        }

    @Ignore
    var chatState: ComposingType
        get() = when (chatState_) {
            ComposingType.NONE.rawValue -> ComposingType.NONE
            ComposingType.TYPING.rawValue -> ComposingType.TYPING
            ComposingType.VOICE.rawValue -> ComposingType.VOICE
            ComposingType.VIDEO.rawValue -> ComposingType.VIDEO
            ComposingType.UPLOAD_FILE.rawValue -> ComposingType.UPLOAD_FILE
            ComposingType.UPLOAD_IMAGE.rawValue -> ComposingType.UPLOAD_IMAGE
            ComposingType.UPLOAD_AUDIO.rawValue -> ComposingType.UPLOAD_AUDIO
            else -> ComposingType.NONE
        }
        set(value) {
            chatState_ = value.rawValue
        }

    @Ignore
    val isAfterburnEnabled: Boolean
        get() = afterburnInterval > 0

    @Ignore
    val isMuted: Boolean
        get() = System.currentTimeMillis() / 1000.0 < muteExpired

    companion object {
        fun genPrimary(jid: String, owner: String, conversationType: ConversationType): String {
            return listOf(jid, owner, conversationType.rawValue).prp()
        }
    }

    fun setPrimary(owner: String) {
        primary = listOf(jid, owner, conversationType.rawValue).prp()
        this.owner = owner
    }
}

// Placeholder enums for ClientSynchronizationManager.ConversationType
enum class ConversationType(val rawValue: String) {
    REGULAR("regular"),
    OMEMO("omemo");

    companion object {
        fun fromRaw(raw: String): ConversationType? = values().find { it.rawValue == raw }
    }
}

// Placeholder enums for ChatStatesManager.ComposingType
enum class ComposingType(val rawValue: Int) {
    NONE(0),
    TYPING(1),
    VOICE(2),
    VIDEO(3),
    UPLOAD_FILE(4),
    UPLOAD_IMAGE(5),
    UPLOAD_AUDIO(6);

    companion object {
        fun fromRaw(raw: Int): ComposingType? = values().find { it.rawValue == raw }
    }
}

fun List<String>.prp(): String {
    return joinToString(separator = "_")
}

fun Array<String>.prp(): String {
    return joinToString(separator = "_")
}