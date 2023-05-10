package com.xabber.data_base.models.messages

import com.xabber.data_base.models.sync.ConversationType

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey


class MessageStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""  // id
    var owner: String = ""    // наш jid
    var opponent: String = ""  // jid оппонента
    var body: String = ""
    var legacyBody: String = ""
    var date: Long = 0
    var sentDate: Long = 0
    var editDate: Long = 0
    var outgoing: Boolean = false  // true я
    var isRead: Boolean = false
    var displayAs_: String = ""
    var messageId: String = ""
    var isFromTrustedSource: Boolean = false
    var oreviousId: String? = null
    var archivedId: String = "" // мне не нужно
    var isDeleted: Boolean = false
    var state_: Int = 0
    var systemMetadata_: String = ""
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var nessageError: String? = null
    var messageErrorCode: String? = null
    var conversationType_: String = ConversationType.Regular.rawValue
    var inlineForwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()


//    var conversationType: ConversationType
//        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
//        set(newValue: ConversationType) { conversationType_ = newValue.rawValue }
//
//    var displayAs: MessageDisplayType
//        get() = MessageDisplayType.values().firstOrNull { it.rawValue == displayAs_ } ?: MessageDisplayType.Text
//        set(newValue: MessageDisplayType) { displayAs_ = newValue.rawValue }
//
//    var state: MessageSendingState
//        get() = MessageSendingState.values().firstOrNull { it.rawValue == state_ } ?: MessageSendingState.Sending
//        set(newValue: MessageSendingState) { state_ = newValue.rawValue }
}
