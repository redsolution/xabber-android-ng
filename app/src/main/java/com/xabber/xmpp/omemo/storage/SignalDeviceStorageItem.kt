package com.xabber.xmpp.omemo.storage

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class SignalDeviceStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var deviceId: Int = 0
    var name: String? = null

    private var stateRaw: String = TrustState.UNKNOWN.rawValue

    var updateDate: Date = Date()
    var trustDate: Date = Date(-1)
    var lastTrustedItemsUpdateTimestamp: String = ""
    var trustedByDeviceId: String? = null
    var fingerprint: String = ""
    var freshlyUpdated: Boolean = false
    var isTrustedByCertificate: Boolean = false
    var signature: String? = null
    var signedBy: String? = null
    var signedAt: Double = -1.0
    var isPublicated: Boolean = false


    var state: TrustState
        get() = TrustState.fromRaw(stateRaw)
        set(value) {
            stateRaw = value.rawValue
        }

    companion object {

        fun genPrimary(owner: String, jid: String, deviceId: Int): String {
            return listOf(owner, jid, deviceId.toString()).prp()
        }
    }
}


enum class TrustState(val rawValue: String) {
    UNKNOWN("unknown"),
    IGNORE("ignore"),
    TRUSTED("trust"),
    DISTRUSTED("distrust"),
    REVOKED("revoked"),
    FINGERPRINT_CHANGED("fingerprintChanged");

    companion object {

        fun fromRaw(raw: String): TrustState =
            values().find { it.rawValue == raw } ?: UNKNOWN
    }
}