package com.xabber.xmpp.messages

import com.xabber.xmpp.sync.ConversationType
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*
import io.realm.RealmList
import io.realm.realmListOf

class MessageStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var opponent: String = ""
    var body: String = ""
    var legacyBody: String = ""
    var date: Date = Date()
    var sentDate: Date = Date()
    var editDate: Date = Date()
    var outgoing: Boolean = false
    var isRead: Boolean = false

    var displayAs_: String = ""
    var messageId: String = ""
    var isFromTrustedSource: Boolean = false
    var oreviousId: String? = null
    var archivedId: String = ""
    var isDeleted: Boolean = false
    var state_: Int = 0
    var systemMetadata_: String = ""
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var nessageError: String? = null
    var messageErrorCode: String? = null
    var conversationType_: String = ConversationType.Regular.rawValue


    var conversationType: ConversationType
        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
        set(newValue: ConversationType) { conversationType_ = newValue.rawValue }

    var displayAs: MessageDisplayType
        get() = MessageDisplayType.values().firstOrNull { it.rawValue == displayAs_ } ?: MessageDisplayType.Text
        set(newValue: MessageDisplayType) { displayAs_ = newValue.rawValue }

    var state: MessageSendingState
        get() = MessageSendingState.values().firstOrNull { it.rawValue == state_ } ?: MessageSendingState.Sending
        set(newValue: MessageSendingState) { state_ = newValue.rawValue }
}