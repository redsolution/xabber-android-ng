package com.xabber.xmpp.groupchat


import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class GroupchatInvitesStorageItem : RealmObject {
    companion object {

        fun genPrimary(inviteId: String, owner: String): String {
            return listOf(inviteId, owner).prp()
        }

        fun primaryKey(): String? = "primary"
    }

    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var groupchat: String = ""
    var jid: String = ""
    var sender: String = ""
    var inviteId: String = ""
    var date: Long = 0
    var reason: String? = null
    var outgoing: Boolean = true
    var isRead: Boolean = false
    var isHidden: Boolean = false
    var temporary: Boolean = true
    var isProcessed: Boolean = false
    var entity_: String = RosterItemEntity.GROUPCHAT.rawValue
    var isAnonymous: Boolean = false

    var entity: RosterItemEntity
        get() = RosterItemEntity.fromRaw(entity_) ?: RosterItemEntity.GROUPCHAT
        set(newValue) {
            entity_ = newValue.rawValue
        }
}


enum class RosterItemEntity(val rawValue: String) {
    GROUPCHAT("groupchat");

    companion object {
        fun fromRaw(raw: String): RosterItemEntity? =
            values().find { it.rawValue == raw }
    }
}