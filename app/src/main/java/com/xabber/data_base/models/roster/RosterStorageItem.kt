package com.xabber.data_base.models.roster

import android.util.Log
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.presentation.XabberApplication

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import com.xabber.utils.prp
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort

class RosterStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var jid: String = ""
    var nickname: String = "" // Maps to username in Swift
    var customNickname: String = "" // Maps to customUsername in Swift
    var isDeleted: Boolean = false // Maps to removed in Swift
    var isBlocked: Boolean = false
    var subscription_: String = Subscription.UNDEFINED.rawValue
    var ask_: String = Ask.NONE.rawValue
    var askMessage: String = ""
    var approved: Boolean = false
    var isHidden: Boolean = false
    var notes: String? = null
    var isSupportOmemo: Boolean = true
    var isOmemoDevicesListReceived: Boolean = false
    var colorKey: String = XabberApplication.applicationContext().resources.getString(R.string.blue)
    var avatarR: Int = R.drawable.dog
    var oldschoolAvatarKey: String? = null
    var avatarMaxUrl: String? = null
    var avatarMinUrl: String? = null
    var avatarUpdatedTS: Double = -1.0
    var updatedTS: Double = -1.0
    var encryptionUpdatedTS: Double = -1.0
    var groups: RealmList<String> = realmListOf()
    var associatedLastChat: LastChatsStorageItem? = null

    var subscription: Subscription
        get() = Subscription.fromRaw(subscription_)
        set(value) {
            subscription_ = value.rawValue
        }

    var ask: Ask
        get() = Ask.fromRaw(ask_)
        set(value) {
            ask_ = value.rawValue
        }

    val displayName: String
        get() {
            if (customNickname.trim().isNotEmpty()) return customNickname.trim()
            if (nickname.isNotEmpty()) return nickname
            return jid // Simplified; replace with JidManager.shared.prepareJid if available
        }

    val avatarUrl: String?
        get() = avatarMaxUrl ?: avatarMinUrl ?: oldschoolAvatarKey

    fun isThereSubscriptionRequest(): Boolean {
        if (jid.contains("/")) return false // Simplified check for server JID
        return when (ask) {
            Ask.IN, Ask.BOTH -> true
            else -> false
        }
    }

    fun getPrimaryResource(): ResourceStorageItem? {
        return try {
            val realm = Realm.open(defaultRealmConfig())
            val resource = realm.query<ResourceStorageItem>("owner = $0 AND jid = $1", owner, jid)
                .sort(
                    "timestamp" to Sort.DESCENDING,
                    "priority" to Sort.DESCENDING
                )
                .first().find()
            realm.close()
            resource
        } catch (e: Exception) {
            Log.e("RosterStorageItem", "Can't get primary resource for jid: $jid, owner: $owner", e)
            null
        }
    }

    companion object {
        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }
}

enum class Subscription(val rawValue: String) {
    TO("to"),
    FROM("from"),
    BOTH("both"),
    NONE("none"),
    UNDEFINED("undefined");

    companion object {
        fun fromRaw(raw: String): Subscription =
            values().find { it.rawValue == raw } ?: UNDEFINED
    }
}

enum class Ask(val rawValue: String) {
    NONE("none"),
    IN("in"),
    OUT("out"),
    BOTH("both");

    companion object {
        fun fromRaw(raw: String): Ask =
            values().find { it.rawValue == raw } ?: NONE
    }
    }