package com.xabber.xmpp.messages.message

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

open class TemporaryMessageStanzaStorageItem : RealmObject {
    companion object {
        fun genPrimary(messageId: String, owner: String): String {
            return listOf(messageId, owner, "temp_stanza").prp()
        }

        fun primaryKey(): String? = "primary"
    }

    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var messageId: String = ""
    var date: Long = 0
    var stanza: String = ""
    var isProcessed: Boolean = false
}