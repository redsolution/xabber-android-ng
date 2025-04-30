package com.xabber.xmpp.voip.voIPManager

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class CallMetadataStorageItem : RealmObject {
    @PrimaryKey
    var sid: String = ""

    var owner: String = ""
    var opponent: String = ""
    @Ignore
    var dateStart: Date = Date()
    @Ignore
    var dateEnd: Date? = null
    var isCallEnded: Boolean = false
    private var callStateRaw: Int = 0
    var income: Boolean = false
    var cancelled: Boolean = false
    var isDeleted: Boolean = false


    var callState: CallState
        get() = CallState.fromRaw(callStateRaw)
        set(value) {
            callStateRaw = value.rawValue
        }


    val duration: Double
        get() {
            val end = dateEnd ?: dateStart
            return (end.time - dateStart.time) / 1000.0
        }
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