package com.xabber.data_base.models.account


import com.xabber.xmpp.presence.ResourceStorageItem
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
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
    var away: Long = 0
    var colorKey: String = ""
    var resource: ResourceStorageItem? = null
    var deviceUuid: String = ""

    var isEncryptionEnabled: Boolean = true
    var createdAt: Long = System.currentTimeMillis()

    var oldschoolAvatarKey: String? = null
    var avatarMaxUrl: String? = null
    var avatarMinUrl: String? = null
    var avatarUpdatedTs: Double = -1.0
    var updatedTS: Double = -1.0
    var encryptionUpdatedTs: Double = -1.0


    var hasAvatar: Boolean = false

}
// primary = jid, аватар искать по owner + jid