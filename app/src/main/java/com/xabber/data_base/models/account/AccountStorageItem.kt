package com.xabber.data_base.models.account


import com.xabber.xmpp.presence.ResourceStorageItem
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

class AccountStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""
    var order: Int = 0
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var username: String = ""
    var enabled: Boolean = true
    var pushNode: String = ""
    var pushService: String = ""
    var away: Long = 0
    var statusMessage: String = ""
    var colorKey: String = ""
    var hasAvatar: Boolean = false
    var resource: ResourceStorageItem? = null
    var savePassword: Boolean = true
    var manuallySetHost: Boolean = false
    var node: String = ""
    var service: String = ""
    var deviceUuid: String = ""
    var xTokenUID: String = ""
    var xTokenSupport: Boolean = false
    var clientSyncSupport: Boolean = false
    var isCollapsed: Boolean = false
    var isEncryptionEnabled: Boolean = true
    var isOmemoDevicesListReceived: Boolean = false
    var isDevicesListReceived: Boolean = false
    var createdAt: Date = Date()
    var deviceName: String = ""
    var oldschoolAvatarKey: String? = null
    var avatarMaxUrl: String? = null
    var avatarMinUrl: String? = null
    var avatarUpdatedTs: Double = -1.0
    var updatedTS: Double = -1.0
    var encryptionUpdatedTs: Double = -1.0
    var counter: String = "1"

}
// primary = jid, аватар искать по owner + jid