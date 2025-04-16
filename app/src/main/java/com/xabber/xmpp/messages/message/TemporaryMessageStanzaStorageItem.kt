package com.xabber.xmpp.messages.message

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

open class TemporaryMessageStanzaStorageItem: RealmObject {
    companion object{
        fun primaryKey(): String? = "primary"
        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }

    @PrimaryKey
    val primary:String=""

    @Index
    val owner:String=""
    @Index
    val jid:String=""

    val date: Date = Date()
    val stanza: String=""
}