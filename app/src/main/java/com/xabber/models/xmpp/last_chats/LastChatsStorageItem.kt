package com.xabber.models.xmpp.last_chats

import com.xabber.R
import com.xabber.models.xmpp.chat_states.ComposingType
import com.xabber.models.xmpp.messages.MessageStorageItem
import com.xabber.models.xmpp.roster.RosterStorageItem
import com.xabber.models.xmpp.sync.ConversationType

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class LastChatsStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""  // автоматически jid + owner + conversation type
    var owner: String = ""   // jid наш
    var opponentJid: String = ""     // jid собеседника
    var messageDate: Long = 0    // дата последнего сообщения
    var lastReadMessageDate: Long = 0
    var rosterItem: RosterStorageItem? = null   // данные собеседника
    var lastMessage: MessageStorageItem? = null  // последнее собщение
    var lastMessageId: String = "" // id последнего сообщения (мне не нужно)
    var isSynced: Boolean = true  // синхронизировано
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
    var avatar: Int = R.drawable.dog  //
    var colorKey: String = "blue"
    var conversationType_: String = ConversationType.Regular.rawValue
    var composingType_: String = ComposingType.none.rawValue
    var chatMarkersSupport: Boolean = false
    var lastPosition: String = ""

//    var conversationType: ConversationType
//        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
//        set(newValue: ConversationType) { conversationType_ = newValue.rawValue }
//
//    var composingType: ComposingType
//        get() = ComposingType.values().firstOrNull { it.rawValue == composingType_ } ?: ComposingType.none
//        set(newValue: ComposingType) { composingType_ = newValue.rawValue }

//    companion object {
//        fun genPrimary(jid: String, owner: String, conversationType: ConversationType): String {
//            return prp(strArray = arrayOf(jid, owner, conversationType.rawValue))
//        }
//    }
}