package com.xabber.xmpp.device

import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.utils.prp
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date
import java.util.logging.Logger

class DeviceStorageItem : RealmObject {
    companion object {
        private val logger = Logger.getLogger(DeviceStorageItem::class.java.name)

        fun genPrimary(uid: String, owner: String): String {
            return listOf(uid, owner).prp()
        }
    }

    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var uid: String = ""
    var client: String = ""
    var device: String = ""
    var descr: String = ""
    var ip: String = ""
    var authDate: Double = 1.0
    var expire: Double = 1.0
    var resource: String? = null
    var omemoDeviceId: Int = -1
    var secret: String = "" // New field for secret
    var validationKey: String = "" // New field for validation-key

    val encryptionEnabled: Boolean
        get() = omemoDeviceId >= 0

    fun configure(
        owner: String,
        uid: String,
        ip: String,
        client: String,
        device: String,
        expire: Double,
        authDate: Double,
        descr: String,
        secret: String = this.secret, // Preserve existing secret if not provided
        validationKey: String = this.validationKey // Preserve existing validationKey if not provided
    ) {
        this.primary = genPrimary(uid = uid, owner = owner)
        this.owner = owner
        this.uid = uid
        this.ip = ip
        this.client = client
        this.device = device
        this.descr = descr
        this.expire = expire
        this.authDate = authDate
        this.secret = secret
        this.validationKey = validationKey
    }
}