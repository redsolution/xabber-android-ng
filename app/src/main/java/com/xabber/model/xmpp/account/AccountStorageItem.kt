package com.xabber.model.xmpp.account

import com.xabber.model.xmpp.presences.ResourceStorageItem
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

class AccountStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""

    var order: Int = 0
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var username: String = ""
    var enabled: Boolean = true
    var pushNode: String = ""
    var pushService: String = ""
    var away: Long = 0
    var statusMessage: String = ""
    var colorKey: String = ""
    var hasAvatar: Boolean = false
    var resource: ResourceStorageItem? = null

//    companion object {
//        fun genPrimary(jid: String): String {
//            return prp(strArray = arrayOf(jid))
//        }
//    }
}