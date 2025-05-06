package com.xabber.xmpp.voip.voIPManager

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class CallMetadataStorageItem : RealmObject {
    companion object {
        fun genPrimary(jid: String, owner: String, resource: String): String {
            return listOf(jid, resource, owner).prp()
        }
    }
    @PrimaryKey
    var sid: String = ""
    var owner: String = ""
    var opponent: String = ""
    var dateStart: Long = 0
    var dateEnd: Long = 0
    var isCallEnded: Boolean = false
    var callState_: Int = 0
    var income: Boolean = false
    var cancelled: Boolean = false
    var isDeleted: Boolean = false


    var callState: CallState
        get() = CallState.fromRaw(callState_)
        set(value) {
            callState_ = value.rawValue
        }


//    val duration: Double
//        get() {
//            val end = dateEnd ?: dateStart
//            return (end.time - dateStart.time) / 1000.0
//        }
}


enum class CallState(val rawValue: Int) {
    INITIATED(0),
    STARTED(1),
    ENDED(2),
    REJECTED(3),
    FAILED(4),
    RETRACTED(5);

    companion object {
        fun fromRaw(raw: Int): CallState =
            values().find { it.rawValue == raw } ?: FAILED
    }
}