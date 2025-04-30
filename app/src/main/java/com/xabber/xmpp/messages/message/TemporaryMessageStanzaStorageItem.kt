package com.xabber.xmpp.messages.message

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

open class TemporaryMessageStanzaStorageItem: RealmObject {

    fun primaryKey(): String? = "primary"


    companion object {

        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }

    @PrimaryKey
    var primary:String=""

    var owner:String=""
    var jid:String=""
    @Ignore
    var date: Date = Date()
    var stanza: String=""
}