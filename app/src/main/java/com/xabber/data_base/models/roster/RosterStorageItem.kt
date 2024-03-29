package com.xabber.data_base.models.roster

import com.xabber.R
import com.xabber.presentation.XabberApplication
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
    var subscribtion_: String = RosterSubscribtion.Undefined.rawValue
    var ask_: String = RosterAsk.None.rawValue
    var askMessage: String = ""
    var shouldShowSubscribtionRequest: Boolean = false
    var approved: Boolean = false
    var isHidden: Boolean = false
    var notes: String? = null
    var isSupportOmemo: Boolean = false
    var colorKey: String = XabberApplication.applicationContext().resources.getString(R.string.blue)
    var avatarR: Int = R.drawable.dog


////    @Ignore
//    val ask: RosterAsk
//        get() = RosterAsk.values().firstOrNull { it.rawValue == ask_ } ?: RosterAsk.None
////        set(newValue: RosterAsk) {
////            ask_ = newValue.rawValue
////        }
//
//    val subscribtion: RosterSubscribtion
//        get() = RosterSubscribtion.values().firstOrNull { it.rawValue == subscribtion_ }
//            ?: RosterSubscribtion.Undefined
////        set(newValue: RosterSubscribtion) {
////            subscribtion_ = newValue.rawValue
////        }

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