package com.xabber.xmpp.presence
//
//import com.xabber.utils.prp
//import com.xabber.xmpp.device.DeviceStorageItem
//import io.realm.kotlin.Realm
//import io.realm.kotlin.ext.query
//import io.realm.kotlin.types.RealmObject
//import io.realm.kotlin.types.annotations.Ignore
//import io.realm.kotlin.types.annotations.Index
//import io.realm.kotlin.types.annotations.PrimaryKey
//import java.util.Date
//import java.util.logging.Logger
//
//
//class ResourceStorageItem : RealmObject {
//    companion object {
//        private val logger = Logger.getLogger(ResourceStorageItem::class.java.name)
//
//
//        fun genPrimary(jid: String, owner: String, resource: String): String {
//            return listOf(jid, resource, owner).prp()
//        }
//    }
//
//    @PrimaryKey
//    var primary: String = ""
//    var owner: String = ""
//    var jid: String = ""
//    var resource: String = ""
//    var client: String = ""
//    var priority: Int = 0
//    private var type_: Int = 0
//    var timestamp: Date = Date()
//    private var status_: String = ResourceStatus.OFFLINE.rawValue
//    private var statusExt_: String = RosterItemEntity.CONTACT.rawValue
//    var isTemporary: Boolean = false
//    var statusMessage: String = ""
//    var isCurrentResourceForAccount: Boolean = false
//    var deviceId: String? = null
//    var type: ClientType
//        get() = ClientType.fromRaw(type_)
//        set(value) {
//            type_ = value.rawValue
//        }
//
//    var status: ResourceStatus
//        get() = ResourceStatus.fromRaw(status_)
//        set(value) {
//            status_ = value.rawValue
//        }
//
//
//    var entity: RosterItemEntity
//        get() = if (jid.contains("redmine_issue")) {
//            RosterItemEntity.ISSUE
//        } else {
//            RosterItemEntity.fromRaw(statusExt_)
//        }
//        set(value) {
//            statusExt_ = if (jid.contains("redmine_issue")) {
//                RosterItemEntity.ISSUE.rawValue
//            } else {
//                value.rawValue
//            }
//        }
//
//
//    val displayedStatus: String
//        get() {
//            if (status == ResourceStatus.OFFLINE) {
//                return if (entity == RosterItemEntity.CONTACT) {
//                    "Offline"
//                } else {
//                    statusMessage.ifEmpty { "Offline" }
//                }
//            }
//            if (statusMessage.isNotEmpty()) {
//                return statusMessage
//            }
//            return when (status) {
//                ResourceStatus.OFFLINE -> "Offline" // groupchat_status_offline
//                ResourceStatus.XA -> "Away for long time" // groupchat_status_away_long
//                ResourceStatus.AWAY -> "Away" // groupchat_status_away
//                ResourceStatus.DND -> "Busy" // groupchat_status_busy
//                ResourceStatus.ONLINE -> "Online" // groupchat_status_online
//                ResourceStatus.CHAT -> "Ready to chat" // groupchat_status_ready_to_chat
//            }
//        }
//
//
//
//}
//
//
//enum class ResourceStatus(val rawValue: String) {
//    OFFLINE("offline"),
//    XA("xa"),
//    AWAY("away"),
//    DND("dnd"),
//    ONLINE("online"),
//    CHAT("chat");
//
//    companion object {
//        fun fromRaw(raw: String): ResourceStatus =
//            values().find { it.rawValue == raw } ?: OFFLINE
//    }
//}
//
//
//enum class RosterItemEntity(val rawValue: String) {
//    CONTACT("contact"),
//    GROUPCHAT("groupchat"),
//    BOT("bot"),
//    SERVER("server"),
//    INCOGNITO_CHAT("incognito"),
//    PRIVATE_CHAT("private"),
//    ENCRYPTED_CHAT("encrypted"),
//    ISSUE("issue");
//
//    companion object {
//        fun fromRaw(raw: String): RosterItemEntity =
//            values().find { it.rawValue == raw } ?: CONTACT
//    }
//}
//
//
//enum class ClientType(val rawValue: Int) {
//    UNKNOWN(0),
//    BOT(1),
//    CONSOLE(2),
//    GAME(3),
//    HANDHELD(4),
//    PC(5),
//    PHONE(6),
//    SMS(7),
//    WEB(8),
//    GROUPCHAT(9);
//
//    companion object {
//        fun fromRaw(raw: Int): ClientType =
//            values().find { it.rawValue == raw } ?: UNKNOWN
//    }
//}