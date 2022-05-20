package com.xabber.xmpp.last_chats

import com.xabber.utils.storage.prp
import com.xabber.xmpp.chat_states.ComposingType
import com.xabber.xmpp.messages.MessageStorageItem
import com.xabber.xmpp.roster.RosterStorageItem
import com.xabber.xmpp.sync.ConversationType
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*

class LastChatsStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var messageDate: Date = Date()
    var lastReadMessageDate: Date = Date()
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
    var unread: Int = 0
    var draftMessage: String? = null
    var groupchatMyId: String? = null
    var isPrereaded: Boolean = false
    var pinnedPosition: Double = 0.0
    var isPinned: Boolean = false
    var muteExpired: Double = -1.0
    var conversationType_: String = ConversationType.Regular.rawValue
    var composingType_: String = ComposingType.none.rawValue
    var chatMarkersSupport: Boolean = false

    var conversationType: ConversationType
        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
        set(newValue: ConversationType) { conversationType_ = newValue.rawValue }

    var composingType: ComposingType
        get() = ComposingType.values().firstOrNull { it.rawValue == composingType_ } ?: ComposingType.none
        set(newValue: ComposingType) { composingType_ = newValue.rawValue }

    companion object {
        fun genPrimary(jid: String, owner: String, conversationType: ConversationType): String {
            return prp(strArray = arrayOf(jid, owner, conversationType.rawValue))
        }
    }
}