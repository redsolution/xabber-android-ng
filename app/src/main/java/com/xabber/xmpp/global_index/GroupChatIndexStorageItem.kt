package com.xabber.xmpp.global_index


import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey


open class GroupChatIndexStorageItem : RealmObject {
    enum class Membership(val rawValue: Int) {
        NONE(0),
        OPEN(1),
        MEMBER_ONLY(2);

        companion object {
            fun fromRaw(raw: Int): Membership =
                values().find { it.rawValue == raw } ?: NONE
        }
    }

    enum class Privacy(val rawValue: Int) {
        NONE(0),
        IS_PUBLIC(1);

        companion object {
            fun fromRaw(raw: Int): Privacy =
                values().find { it.rawValue == raw } ?: NONE
        }
    }

    companion object {

        fun primaryKey(): String? = "jid"
    }

    @PrimaryKey
    var jid: String = ""

    var itemId: String = ""
    var name: String = ""
    var text: String = ""
    var membershipRaw: Int = Membership.NONE.rawValue
    var privacyRaw: Int = Privacy.NONE.rawValue
    var members: Int = 0
    var messagesCount: Int = 0

    var membership: Membership
        get() = when (membershipRaw) {
            Membership.NONE.rawValue -> Membership.NONE
            Membership.OPEN.rawValue -> Membership.OPEN
            Membership.MEMBER_ONLY.rawValue -> Membership.MEMBER_ONLY
            else -> Membership.NONE
        }
        set(newValue) {
            membershipRaw = newValue.rawValue
        }

    var privacy: Privacy
        get() = when (privacyRaw) {
            Privacy.NONE.rawValue -> Privacy.NONE
            Privacy.IS_PUBLIC.rawValue -> Privacy.IS_PUBLIC
            else -> Privacy.NONE
        }
        set(newValue) {
            privacyRaw = newValue.rawValue
        }
}