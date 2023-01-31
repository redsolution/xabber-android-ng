package com.xabber.models.xmpp.account

import com.xabber.models.xmpp.presences.ResourceStorageItem

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class AccountStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = "" // аватар искать по owner + jid
    var order: Int = 0
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var nickname: String = ""
    var enabled: Boolean = true
    var pushNode: String = ""
    var pushService: String = ""
    var away: Long = 0
    var statusMessage: String = ""
    var colorKey: String = ""
    var hasAvatar: Boolean = false
    var resource: ResourceStorageItem? = null  // статус
        // аватарка где?


//    companion object {
//        fun genPrimary(jid: String): String {
//            return prp(strArray = arrayOf(jid))
//        }
//    }
}