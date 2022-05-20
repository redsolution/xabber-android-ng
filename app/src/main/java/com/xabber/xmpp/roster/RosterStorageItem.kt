package com.xabber.xmpp.roster

import com.xabber.utils.storage.prp
import com.xabber.xmpp.avatar.AvatarStorageItem
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

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

    companion object {
        fun genPrimary(jid: String, owner: String): String {
            return prp(strArray = arrayOf(jid, owner))
        }
    }
}