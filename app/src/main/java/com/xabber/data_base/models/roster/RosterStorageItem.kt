package com.xabber.data_base.models.roster

import com.xabber.R
import com.xabber.presentation.XabberApplication

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

// информация о контакте
class RosterStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""
    var owner: String = "" // аккаунт к которому принадлежит контакт
    var jid: String = "" // если никнеймов нет отобразить в чатах jid
    var nickname: String = "" // как он себя записал
    var customNickname: String = "" // как мы его себе записали
    var isDeleted: Boolean = false
    var isBlocked: Boolean = false
    var subscription_: String = RosterSubscribtion.Undefined.rawValue
    var ask_: String = RosterAsk.None.rawValue
    var askMessage: String = ""
    var shouldShowSubscribtionRequest: Boolean = false
    var approved: Boolean = false
    var isHidden: Boolean = false
    var notes: String? = null
    var isSupportOmemo: Boolean = false
    var colorKey: String = XabberApplication.applicationContext().resources.getString(R.string.blue)
    var avatarR: Int = R.drawable.dog
    var avatarUpdatedTS: Double = -1.0
    var updatedTS: Double = -1.0
    var encryptionUpdatedTS: Double = -1.0
    var groups: RealmList<String> = realmListOf()

    var ask: Ask
        get() = Ask.fromRaw(ask_)
        set(value) {
            ask_ = value.rawValue
        }
//
var subscription: Subscription
    get() = Subscription.fromRaw(subscription_)
    set(value) {
        subscription_ = value.rawValue
    }

//    fun primaryResource(): ResourceStorageItem? {
//        val realm = Realm.open(configuration = defaultRealmConfig())
//        val resources: RealmResults<ResourceStorageItem> = realm
//            .query<ResourceStorageItem>("owner == $owner AND jid == $jid AND SORT(timestamp DESC) AND SORT(priority DESC)")
//            .find()
//        return resources.firstOrNull()
//    }

//    companion object {
//        fun genPrimary(jid: String, owner: String): String {
//            return prp(strArray = arrayOf(jid, owner))
//        }
//    }
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