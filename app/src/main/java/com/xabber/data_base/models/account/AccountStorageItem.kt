package com.xabber.data_base.models.account

import com.xabber.data_base.models.presences.ResourceStorageItem
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

/**
 * Account properties representation in database.
 * Primary key is the account JID.
 */
class AccountStorageItem : RealmObject {
    @PrimaryKey
    var jid: String = ""                     // Primary key (same as Swift)
    var primary: String = ""
    var order: Int = 0
    var host: String = ""
    var savePassword: Boolean = true
    var manuallySetHost: Boolean = false
    var port: Int = 5222
    var username: String = ""
    var enabled: Boolean = true
    var node: String = ""
    var service: String = ""
    var away: Long = 0                        // Timestamp in milliseconds (Date in Swift)
    var statusMessage: String = ""
    var colorKey: String = ""
    var deviceUuid: String = ""
    var xTokenUID: String = ""
    var xTokenSupport: Boolean = false
    var clientSyncSupport: Boolean = false
    var hasAvatar: Boolean = false

    var resource: ResourceStorageItem? = null
    var isCollapsed: Boolean = false
    var isEncryptionEnabled: Boolean = true
    var isOmemoDevicesListReceived: Boolean = false
    var isDevicesListReceived: Boolean = false
    var createdAt: Long = 0                   // Timestamp in milliseconds
    var deviceName: String = ""
    var oldschoolAvatarKey: String? = null
    var avatarMaxUrl: String? = null
    var avatarMinUrl: String? = null
    var avatarUpdatedTS: Double = -1.0
    var updatedTS: Double = -1.0
    var encryptionUpdatedTS: Double = -1.0
    var counter: String = "1"

    // Computed property matching Swift's avatarUrl
    val avatarUrl: String?
        get() = avatarMaxUrl ?: avatarMinUrl ?: oldschoolAvatarKey
}