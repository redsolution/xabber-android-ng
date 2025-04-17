package com.xabber.xmpp.omemo.storage

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey


open class SignalPreKeysStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var deviceId: Int = 0
    var pkId: Int = 0
    var keyUUID: String = ""
    var preKey: String? = null

    companion object {

        fun genPrimary(keyUUID: String): String {
            return keyUUID
        }
    }
}