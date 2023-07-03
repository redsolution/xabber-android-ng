package com.xabber.data_base.models.messages

import com.xabber.data_base.models.roster.RosterStorageItem
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList

import io.realm.kotlin.types.RealmObject


enum class MessageForwardsInlineStorageItemKind(val rawValue: String) {
    text("text"),
    images("images"),
    videos("videos"),
    files("files"),
    voice("voice"),
    quote("quote")
}

class MessageForwardsInlineStorageItem: RealmObject {
    var messageId: String = ""
    var owner: String = ""
    var jid: String = ""
    var kind_: String = MessageForwardsInlineStorageItemKind.text.rawValue
    var parentId: String = ""
    var body: String = ""
    var forwardJid: String = ""
    var forwardNickname: String = ""
    var isOutgoing: Boolean = false
    var originalDate: Long? = null
    var rosterItem: RosterStorageItem? = null
    var subforwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var canCheckRealmAccessedLinks: Boolean = true
}
