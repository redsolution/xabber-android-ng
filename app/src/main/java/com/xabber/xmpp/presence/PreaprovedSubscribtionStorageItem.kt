package com.xabber.xmpp.presence

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

open class PreaprovedSubscribtionStorageItem: RealmObject {
    fun primaryKey(): String? = "primary"
    companion object{

        fun genPrimary(owner: String, jid: String, deviceId: Int): String {
            return listOf(owner, jid, deviceId.toString()).prp()
        }
    }

    @PrimaryKey
    val primary: String=""

    val owner: String=""
    val jid: String=""
    val dateCreated: Date = Date()

}