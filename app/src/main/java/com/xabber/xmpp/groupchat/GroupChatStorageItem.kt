package com.xabber.xmpp.groupchat

import android.content.Context
import com.xabber.R
import com.xabber.utils.prp
import com.xabber.xmpp.presence.ResourceStatus
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
    private var privacyRaw: String = Privacy.NONE.rawValue
    private var indexRaw: String = IndexRaw.NONE.rawValue
    private var membershipRaw: String = Membership.NONE.rawValue
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
    private var muteStateRaw: Int = MuteState.ENABLED.rawValue
    var isDeleted: Boolean = false


    var membership: Membership
        get() = Membership.fromRaw(membershipRaw)
        set(value) {
            membershipRaw = value.rawValue
        }


    var privacy: Privacy
        get() = Privacy.fromRaw(privacyRaw)
        set(value) {
            privacyRaw = value.rawValue
        }


    var index: IndexRaw
        get() = IndexRaw.fromRaw(indexRaw)
        set(value) {
            indexRaw = value.rawValue
        }


    var muteState: MuteState
        get() = MuteState.fromRaw(muteStateRaw)
        set(value) {
            muteStateRaw = value.rawValue
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
    MEMBER_ONLY("member-only");

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
