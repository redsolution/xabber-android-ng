package com.xabber.data_base.models.last_chats

import android.util.Log
import com.xabber.R
import com.xabber.data_base.models.chat_states.ComposingType
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.types.annotations.Index

class LastChatsStorageItem : RealmObject {
    companion object {
        fun genPrimary(jid: String, owner: String, conversationType: ConversationType): String {
            if (jid.isBlank() || owner.isBlank() || conversationType.rawValue.isBlank()) {
                return ""
            }
            val primary = listOf(jid, owner, conversationType.rawValue).prp()
            return primary
        }
    }
    var conversationType: ConversationType
        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
        set(newValue) {
            conversationType_ = newValue.rawValue
        }

    val isAfterburnEnabled: Boolean
        get() = afterburnInterval > 0

    val isMuted: Boolean
        get() = System.currentTimeMillis() / 1000.0 < muteExpired

    var chatState: ComposingType
        get() = when (chatState_) {
            ComposingType.none.rawValue.toInt() -> ComposingType.none
            ComposingType.typing.rawValue.toInt() -> ComposingType.typing
            ComposingType.voice.rawValue.toInt() -> ComposingType.voice
            ComposingType.video.rawValue.toInt() -> ComposingType.video
            ComposingType.uploadFile.rawValue.toInt() -> ComposingType.uploadFile
            ComposingType.uploadImage.rawValue.toInt() -> ComposingType.uploadImage
            ComposingType.uploadAudio.rawValue.toInt() -> ComposingType.uploadAudio
            else -> ComposingType.none
        }
        set(newValue) {
            chatState_ = newValue.rawValue.toInt()
        }

    @PrimaryKey
    var primary: String = ""  // автоматически jid + owner + conversation type
    var owner: String = ""   // jid юзера
    var jid: String = ""     // jid собеседника
    var messageDate: Long = 0L    // дата последнего сообщения
    var lastReadMessageDate: Long = 0
    var rosterItem: RosterStorageItem? = null   // данные собеседника
    var lastMessage: MessageStorageItem? = null  // последнее собщение
    var lastMessageId: String = "" // id последнего сообщения (мне не нужно)
    var isSynced: Boolean = false  // синхронизировано (changed to false)
    var isHistoryGapFixedForSession: Boolean = false
    var isArchived: Boolean = false  // архив
    var messagesCount: Int = -1
    var retractVersion: String? = null
    var mentionId: String? = null
    var lastReadId: String? = null
    var displayedId: String? = null
    var deliveredId: String? = null
    var unread: Int = 0         // количесво непрочитанных сообщений
    var draftMessage: String? = null   // черновик
    var groupchatMyId: String? = null
    var isPrereaded: Boolean = false
    var pinnedPosition: Long = 0 // время закрепа
    var muteExpired: Long = -1   //
    var conversationType_: String = ConversationType.Regular.rawValue
    var composingType_: String = ComposingType.none.rawValue
    var chatMarkersSupport: Boolean = false
    var lastPosition: String = ""     //
    var avatar: Int = R.drawable.dog
    var isInitialArchiveLoaded: Boolean = false
    var fullArchiveLoaded: Boolean = false
    var lastLoadedMessageHistoryId: String? = null
    var isBlocked: Boolean = false // TODO: Deprecate as per Swift
    var isPinned: Boolean = false
    var afterburnInterval: Double = -1.0
    var afterburnIntervalLastUpdate: Double = -1.0
    var isAllHistoryLoaded: Boolean = false
    var isFreshNotEmptyEncryptedChat: Boolean = false
    var hasErrorInChat: Boolean = false
    var updateTS: Double = 0.0
    var lastChatOffset: Float = 0f

    private var chatState_: Int = 0
}