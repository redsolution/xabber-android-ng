package com.xabber.xmpp.groupchat

import android.content.Context
import android.provider.Contacts.PresenceColumns.OFFLINE
import com.xabber.R
import com.xabber.utils.prp
import com.xabber.data_base.models.presences.ResourceStatus
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey


open class GroupChatStorageItem : RealmObject {
    companion object {

        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }

    @PrimaryKey
    var primary: String = ""
    var jid: String = ""
    var owner: String = ""
    var name: String = ""
    var privacy_: String = Privacy.NONE.rawValue
    var index_: String = IndexRaw.NONE.rawValue
    var membership_: String = Membership.NONE.rawValue
    var descr: String = ""
    var pinnedMessage: String = ""
    var contacts: RealmList<String> = realmListOf()
    var domains: RealmList<String> = realmListOf()
    var members: Int = 0
    var present: Int = 0
    var peerToPeer: Boolean = false
    var usersListVersion: String? = null
    var invited: RealmList<String> = realmListOf()
    var canInvite: Boolean = true
    var canChangeSettings: Boolean = false
    var canChangeUsersSettings: Boolean = false
    var canChangeNicknames: Boolean = false
    var canChangeBadge: Boolean = false
    var canBlockUsers: Boolean = false
    var canChangeAvatars: Boolean = false
    var canDeleteMessages: Boolean = false
    var defaultRestrictions: RealmList<String> = realmListOf()
    var status: String = ""
    var languages: RealmList<String> = realmListOf()
    var parentChat: String = ""
    var collectAvatars: Boolean = false
    var anonymous: Boolean = false
    private var muteState_: Int = MuteState.ENABLED.rawValue
    var isDeleted: Boolean = false
    var myMemberId: String = ""


    var membership: Membership
        get() = Membership.fromRaw(membership_)
        set(value) {
            membership_ = value.rawValue
        }


    var privacy: Privacy
        get() = Privacy.fromRaw(privacy_)
        set(value) {
            privacy_ = value.rawValue
        }


    var index: IndexRaw
        get() = IndexRaw.fromRaw(index_)
        set(value) {
            index_ = value.rawValue
        }


    var muteState: MuteState
        get() = MuteState.fromRaw(muteState_)
        set(value) {
            muteState_ = value.rawValue
        }





    val statusDisplayed: ResourceStatus
        get() = when (status) {
            "Inactive" -> ResourceStatus.OFFLINE
            "xa" -> ResourceStatus.XA
            "away" -> ResourceStatus.AWAY
            "dnd" -> ResourceStatus.DND
            "online", "active" -> ResourceStatus.ONLINE
            "chat" -> ResourceStatus.CHAT
            else -> ResourceStatus.OFFLINE
        }


}


enum class Membership(val rawValue: String) {
    NONE("none"),
    OPEN("open"),
    PRIVATE("private");

    companion object {
        fun fromRaw(raw: String): Membership =
            values().find { it.rawValue == raw } ?: NONE
    }

}


enum class Privacy(val rawValue: String) {
    NONE("none"),
    INCOGNITO("incognito"),
    PUBLIC_CHAT("public");

    companion object {
        fun fromRaw(raw: String): Privacy =
            values().find { it.rawValue == raw } ?: NONE
    }

}

enum class IndexRaw(val rawValue: String) {
    NONE("none"),
    LOCAL("local"),
    GLOBAL("global");

    companion object {
        fun fromRaw(raw: String): IndexRaw =
            values().find { it.rawValue == raw } ?: NONE
    }

}



enum class MuteState(val rawValue: Int) {
    ENABLED(0),
    DISABLED(1),
    DISABLED_FOR_TIME(2),
    ONLY_MENTIONS(3);

    companion object {
        fun fromRaw(raw: Int): MuteState =
            values().find { it.rawValue == raw } ?: ENABLED
    }
}
