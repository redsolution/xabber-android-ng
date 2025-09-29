package com.xabber.xmpp.notifications

import android.os.Build
import androidx.annotation.RequiresApi
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import com.xabber.utils.prp

@RequiresApi(Build.VERSION_CODES.O)
class ShowedNotificationRequests : RealmObject {
    enum class Kind(val rawValue: String) {
        MESSAGE("message"),
        SUBSCRIPTION("subscribtion"),
        SYSTEM("system");

        companion object {
            fun fromRaw(raw: String): Kind = values().find { it.rawValue == raw } ?: MESSAGE
        }
    }

    companion object {
        fun genPrimary(stanzaId: String, owner: String): String {
            return "${stanzaId}_$owner"
        }
    }

    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var jid: String = ""
    var requestId: String = ""
    var stanzaId: String = ""
    private var kind_: String = ""

    var kind: Kind
        get() = Kind.fromRaw(kind_)
        set(value) {
            kind_ = value.rawValue
        }
}