package com.xabber.xmpp.messages.message

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class MessageStanzaStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var messageId: String = ""
    var stanza: String = ""
    var timestamp: Date = Date()


    fun set(id: String, owner: String, stanza: String, date: Date, primary: String) {
        this.primary = "${primary}_stanza"
        this.owner = owner
        this.messageId = id
        this.stanza = stanza
        this.timestamp = date
    }
}