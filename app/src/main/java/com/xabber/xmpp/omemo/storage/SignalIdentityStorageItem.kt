package com.xabber.xmpp.omemo.storage

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey


open class SignalIdentityStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var deviceId: Int = 0
    var identityKey: String? = null
    var signedPreKey: String? = null
    var signedPreKeyId: Int = 0
    var signedPreKeyTimestamp: Double = 0.0
    var signedPreKeySignature: String? = null
    var name: String = ""
    var isPublicated: Boolean = false

    companion object {

        fun genPrimary(owner: String, jid: String, deviceId: Int): String {
            return listOf(owner, jid, deviceId.toString()).prp()
        }
    }
}