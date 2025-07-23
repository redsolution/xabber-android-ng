package com.xabber.xmpp.messages.message

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class MessageStanzaStorageItem : RealmObject {

    companion object {

        fun genPrimary(inviteId: String, owner: String): String {
            return listOf(inviteId, owner).prp()
        }

        fun primaryKey(): String? = "primary"
    }

    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var messageId: String = ""
    var stanza: String = ""
    @Ignore
    var timestamp: Date = Date()


    fun set(id: String, owner: String, stanza: String, date: Date, primary: String) {
        this.primary = listOf(primary, "_stanza").joinToString("")
        this.owner = owner
        this.messageId = id
        this.stanza = stanza
        this.timestamp = date
    }
}