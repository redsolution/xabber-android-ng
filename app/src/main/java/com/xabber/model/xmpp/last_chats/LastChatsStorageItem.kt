package com.xabber.model.xmpp.last_chats

import com.xabber.R
import com.xabber.model.xmpp.chat_states.ComposingType
import com.xabber.model.xmpp.messages.MessageStorageItem
import com.xabber.model.xmpp.roster.RosterStorageItem
import com.xabber.model.xmpp.sync.ConversationType
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

class LastChatsStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var jid: String = ""
    var messageDate: Long = 0
    var lastReadMessageDate: Long = 0
    var rosterItem: RosterStorageItem? = null
    var lastMessage: MessageStorageItem? = null
    var lastMessageId: String = ""
    var isSynced: Boolean = true
    var isHistoryGapFixedForSession: Boolean = false
    var isArchived: Boolean = false
    var messagesCount: Int = -1
    var retractVersion: String? = null
    var mentionId: String? = null
    var lastReadId: String? = null
    var displayedId: String? = null
    var deliveredId: String? = null
    var unread: String = ""
    var draftMessage: String? = null
    var groupchatMyId: String? = null
    var isPrereaded: Boolean = false
    var pinnedPosition: Long = 0
    var isPinned: Boolean = false
    var muteExpired: Long = -1
    var avatar: Int = R.drawable.img
    var color: Int = R.color.blue_500
    var conversationType_: String = ConversationType.Regular.rawValue
    var composingType_: String = ComposingType.none.rawValue
    var chatMarkersSupport: Boolean = false

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