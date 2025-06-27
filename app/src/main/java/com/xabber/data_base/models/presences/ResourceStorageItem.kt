package com.xabber.data_base.models.presences

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

open class ResourceStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var jid: String = ""
    var resource: String = ""
    var client: String = ""
    var priority: Int = 0
    var timestamp: Long = 0
    var type_: String = ClientType.UNKNOWN.rawValue
    var status_: String = ResourceStatus.OFFLINE.rawValue
    var entity_: String = RosterItemEntity.CONTACT.rawValue
    var statusMessage: String = ""
    var isTemporary: Boolean = false
    var isCurrentResourceForAccount: Boolean = false
    var deviceId: String = ""

    var type: ClientType
        get() = ClientType.values().firstOrNull { it.rawValue == type_ } ?: ClientType.UNKNOWN
        set(newValue) {
            type_ = newValue.rawValue
        }

    var status: ResourceStatus
        get() = ResourceStatus.values().firstOrNull { it.rawValue == status_ } ?: ResourceStatus.OFFLINE
        set(newValue) {
            status_ = newValue.rawValue
        }

    var entity: RosterItemEntity
        get() = RosterItemEntity.values().firstOrNull { it.rawValue == entity_ } ?: RosterItemEntity.CONTACT
        set(newValue) {
            entity_ = newValue.rawValue
        }

    companion object {
        fun genPrimary(jid: String, owner: String, resource: String): String {
            return listOf(jid, resource, owner).prp()
        }
    }
}

enum class ClientType(val rawValue: String) {
    UNKNOWN("unknown"),
    ANDROID("android"),
    IOS("ios"),
    WEB("web");

    companion object {
        fun fromRaw(raw: String): ClientType? = values().find { it.rawValue == raw }
    }
}

enum class ResourceStatus(val rawValue: String) {
    ONLINE("online"),
    OFFLINE("offline"),
    AWAY("away"),
    DND("dnd"),
    XA("xa"),
    CHAT("chat");

    companion object {
        fun fromRaw(raw: String): ResourceStatus? = values().find { it.rawValue == raw }
    }
}

enum class RosterItemEntity(val rawValue: String) {
    CONTACT("contact"),
    SERVER("server"),
    GROUP_CHAT("groupchat"),
    BOT("bot"),
    INCOGNITO("incognito"),
    PRIVATE_CHAT("privatechat"),
    ENCRYPTED_CHAT("encrypted"),
    ISSUE("issue");


    companion object {
        fun fromRaw(raw: String): RosterItemEntity? = values().find { it.rawValue == raw }
    }
}

fun List<String>.prp(): String {
    return joinToString(separator = "_")
}

fun Array<String>.prp(): String {
    return joinToString(separator = "_")
}