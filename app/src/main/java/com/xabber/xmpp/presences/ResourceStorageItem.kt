package com.xabber.xmpp.presences

import com.xabber.utils.storage.prp
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import java.util.*

open class ResourceStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var resource: String = ""

    var client: String = ""
    var priority: Int = 0

    var timestamp: Long = 0
    var type_: String = ClientType.Unknown.rawValue
    var status_: String = ResourceStatus.Offline.rawValue
    var entity_: String = RosterItemEntity.Contact.rawValue
    var statusMessage: String = ""
    var isTemporary: Boolean = false

    var isCurrentResourceForAccount: Boolean = false
    var deviceId: String = ""

//    @delegate:Ignore
//    var type: ClientType
//        get() = ClientType.values().firstOrNull { it.rawValue == type_ } ?: ClientType.Unknown
//        set(newValue: ClientType) {
//            type_ = newValue.rawValue
//        }
//
//    var status: ResourceStatus
//        get() = ResourceStatus.values().firstOrNull { it.rawValue == status_ } ?: ResourceStatus.Offline
//        set(newValue: ResourceStatus) { status_ = newValue.rawValue }
//
//    var entity: RosterItemEntity
//        get() = RosterItemEntity.values().firstOrNull { it.rawValue == entity_ } ?: RosterItemEntity.Contact
//        set(newValue: RosterItemEntity) { entity_ = newValue.rawValue }

//    companion object {
//        fun genPrimary(jid: String, owner: String, resource: String): String {
//            return prp(strArray = arrayOf(jid, owner, resource))
//        }
//    }
}