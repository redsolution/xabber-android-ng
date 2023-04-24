package com.xabber.models.xmpp.account

import com.xabber.models.xmpp.presences.ResourceStorageItem

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

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
}
// primary = jid, аватар искать по owner + jid