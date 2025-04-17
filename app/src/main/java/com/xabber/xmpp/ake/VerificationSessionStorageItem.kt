package com.xabber.xmpp.ake

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey

class VerificationSessionStorageItem: RealmObject {
    enum class VerificationState(val rawValue: String) {
        NONE("none"),
        SENT_REQUEST("sent_request"),
        RECEIVED_REQUEST("received_request"),
        ACCEPTED_REQUEST("accepted_request"),
        RECEIVED_REQUEST_ACCEPT("received_request_accept"),
        HASH_SENT_TO_OPPONENT("hash_sent_to_opponent"),
        HASH_SENT_TO_INITIATOR("hash_sent_to_initiator"),
        TRUSTED("trusted"),
        REJECTED("rejected"),
        FAILED("failed")
    }
    fun primaryKey(): String? = "primary"

    fun genPrimary(jid: String, owner: String): String {
        return listOf(jid, owner).prp()
    }

    var state: VerificationState
        get() = VerificationState.values().find { it.rawValue == stateRaw } ?: VerificationState.NONE
        set(value) {
            stateRaw = value.rawValue
        }


    @PrimaryKey
    var primaryKey: String = ""

    var owner: String=""
    var myDeviceId:Int=0
    var jid: String=""
    var fullJID: String=""
    var opponentDeviceId: Int=0
    var byteSequence: String=""
    var code: String=""
    var stateRaw: String = VerificationState.NONE.rawValue
    var sid: String=""
    var opponentByteSequenceEncrypted: String=""
    var OpponentByteSequenceIv: String=""
    var timestamp: String=""
    var ttl: String="300"


}