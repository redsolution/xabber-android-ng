package com.xabber.data_base.models.messages

import android.util.Log
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.utils.prp
import com.xabber.utils.toMap
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.json.JSONObject


class MessageStorageItem: RealmObject {
    companion object {
        private const val TAG = "MessageStorageItem"

        const val ADD_CONTACT_LOCAL_ARCHIVED_ID = "add-contact-local-archived-id"


        fun messageIdForAuthRequest(jid: String): String {
            return listOf("subscribtion", jid).prp()
        }


        fun messageIdForContact(owner: String, jid: String, ts: String): String {
            return listOf("contact", ts, jid, owner).prp()
        }


        fun messageIdForVoIPCall(owner: String, jid: String, callId: String): String {
            return listOf("voip", owner, jid, callId).prp()
        }


        fun messageIdForInitial(jid: String, conversationType: ConversationType): String {
            return listOf(jid, conversationType.rawValue, "initial_message").prp()
        }


        fun genPrimary(messageId: String, owner: String): String {
            return "${messageId}_$owner"
        }
    }

    @PrimaryKey
    var primary: String = ""  // id
    var owner: String = ""    // наш jid
    var opponent: String = ""  // jid оппонента
    var body: String = ""
    var legacyBody: String = ""
    var date: Long = 0
    var sentDate: Long = 0
    var editDate: Long = 0
    var outgoing: Boolean = false
    var isRead: Boolean = outgoing
    var displayAs_: String = ""
    var messageId: String = ""
    var isFromTrustedSource: Boolean = false
    var oreviousId: String? = null
    var archivedId: String = "" // мне не нужно
    var isDeleted: Boolean = false
    var state_: Int = 0
    var systemMetadata_: String? = null
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var nessageError: String? = null
    var messageErrorCode: String? = null
    var conversationType_: String = ConversationType.Regular.rawValue
    var inlineForwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()
    private var errorMetadata_: String? = ""



    var errorMetadata: Map<String, Any>?
        get() = errorMetadata_?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse error metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            errorMetadata_ = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode error metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }


    var systemMetadata: Map<String, Any>?
        get() = systemMetadata_?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse system metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            systemMetadata_ = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode system metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }


//    var conversationType: ConversationType
//        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
//        set(newValue: ConversationType) { conversationType_ = newValue.rawValue }
//
//    var displayAs: MessageDisplayType
//        get() = MessageDisplayType.values().firstOrNull { it.rawValue == displayAs_ } ?: MessageDisplayType.Text
//        set(newValue: MessageDisplayType) { displayAs_ = newValue.rawValue }
//
//    var state: MessageSendingState
//        get() = MessageSendingState.values().firstOrNull { it.rawValue == state_ } ?: MessageSendingState.Sending
//        set(newValue: MessageSendingState) { state_ = newValue.rawValue }
}
