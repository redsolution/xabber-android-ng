package com.xabber.xmpp.roster

import com.xabber.defaultRealmConfig
import com.xabber.utils.storage.prp
import com.xabber.xmpp.avatar.AvatarStorageItem
import com.xabber.xmpp.presences.ResourceStorageItem
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import io.realm.query

class RosterStorageItem: RealmObject {
    @PrimaryKey
    val primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var nickname: String = ""
    var customNickname: String = ""
    var isDeleted: Boolean = false
    var subscribtion_: String = RosterSubscribtion.Undefined.rawValue
    var ask_: String = RosterAsk.None.rawValue
    var askMessage: String = ""
    var shouldShowSubscribtionRequest: Boolean = false
    var approved: Boolean = false
    var isHidden: Boolean = false
    var notes: String? = null
    var isSupportOmemo: Boolean = false
    var avatar: AvatarStorageItem? = null

    var ask: RosterAsk
        get() = RosterAsk.values().firstOrNull { it.rawValue == ask_ } ?: RosterAsk.None
        set(newValue: RosterAsk) { ask_ = newValue.rawValue }

    var subscribtion: RosterSubscribtion
        get() = RosterSubscribtion.values().firstOrNull { it.rawValue == subscribtion_ } ?: RosterSubscribtion.Undefined
        set(newValue: RosterSubscribtion) { subscribtion_ = newValue.rawValue }

    var primaryResource: ResourceStorageItem?
        get() {
            val realm = Realm.open(configuration = defaultRealmConfig())
            val resources: RealmResults<ResourceStorageItem> = realm
                .query<ResourceStorageItem>("owner == $owner AND jid == $jid AND SORT(timestamp DESC) AND SORT(priority DESC)")
                .find()
            return resources.firstOrNull()
        }
        private set(newValue: ResourceStorageItem?) { error("you cant set primary resource") }

    var displayName: String
        get() {
            if (customNickname.trim().length > 0) {
                return customNickname.trim()
            }
            if (nickname.length > 0) {
                return nickname
            }
            return jid
        }
        set(newValue: String) { error("you cant set display name") }

    companion object {
        fun genPrimary(jid: String, owner: String): String {
            return prp(strArray = arrayOf(jid, owner))
        }
    }
}