package com.xabber.xmpp.roster
//
//import android.util.Log
//import com.xabber.xmpp.last_chats.LastChatsStorageItem
//import com.xabber.utils.prp
//import com.xabber.xmpp.presence.ResourceStorageItem
//import io.realm.kotlin.Realm
//import io.realm.kotlin.ext.query
//import io.realm.kotlin.ext.realmListOf
//import io.realm.kotlin.types.RealmList
//import io.realm.kotlin.types.RealmObject
//import io.realm.kotlin.types.annotations.Index
//import io.realm.kotlin.types.annotations.PrimaryKey
//
//
//open class RosterStorageItem : RealmObject {
//    companion object {
//        private const val TAG = "RosterStorageItem"
//
//        fun genPrimary(jid: String, owner: String): String {
//            return listOf(jid, owner).prp()
//        }
//    }
//
//    @PrimaryKey
//    var primary: String = ""
//    var owner: String = ""
//    var jid: String = ""
//    var username: String = ""
//    var customUsername: String = ""
//    var removed: Boolean = false
//    private var subscriptionRaw: String = ""
//    private var askRaw: String = ""
//    var askMessage: String = ""
//    var approved: Boolean = false
//    var isHidden: Boolean = false
//    var notes: String? = null
//    var isSupportOmemo: Boolean = true
//    var isOmemoDevicesListReceived: Boolean = false
//    var oldschoolAvatarKey: String? = null
//    var avatarMaxUrl: String? = null
//    var avatarMinUrl: String? = null
//    var avatarUpdatedTS: Double = -1.0
//    var updatedTS: Double = -1.0
//    var encryptionUpdatedTS: Double = -1.0
//    var groups: RealmList<String> = realmListOf()
//    var associatedLastChat: LastChatsStorageItem? = null
//
//
//    var subscription: Subscription
//        get() = Subscription.fromRaw(subscriptionRaw)
//        set(value) {
//            subscriptionRaw = value.rawValue
//        }
//
//
//    var ask: Ask
//        get() = Ask.fromRaw(askRaw)
//        set(value) {
//            askRaw = value.rawValue
//        }
//
//
//    val avatarUrl: String?
//        get() = avatarMaxUrl ?: avatarMinUrl ?: oldschoolAvatarKey
//
//
//    val displayName: String
//        get() {
//            val trimmedCustom = customUsername.trim()
//            if (trimmedCustom.isNotEmpty()) return trimmedCustom
//            if (username.isNotEmpty()) return username
//            return jid.split("/").first() // Simplified JID parsing; replace with JidManager
//        }
//
//}
//
//
////enum class Subscription(val rawValue: String) {
////    TO("to"),
////    FROM("from"),
////    BOTH("both"),
////    NONE("none"),
////    UNDEFINED("undefined");
////
////    companion object {
////        fun fromRaw(raw: String): Subscription =
////            values().find { it.rawValue == raw } ?: UNDEFINED
////    }
////}
//
//
//enum class Ask(val rawValue: String) {
//    NONE("none"),
//    IN("in"),
//    OUT("out"),
//    BOTH("both");
//
//    companion object {
//        fun fromRaw(raw: String): Ask =
//            values().find { it.rawValue == raw } ?: NONE
//    }
//}
//
