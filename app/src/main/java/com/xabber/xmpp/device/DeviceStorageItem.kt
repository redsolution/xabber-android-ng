package com.xabber.xmpp.device

import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.utils.prp
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
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

    @Index
    var owner: String = ""

    @Index
    var uid: String = ""

    var client: String = ""
    var device: String = ""
    var descr: String = ""
    var ip: String = ""
    var authDate: Date = Date()
    var expire: Date = Date()
    var resource: String? = null
    var omemoDeviceId: Int = -1


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
        descr: String
    ) {
        this.primary = genPrimary(uid = uid, owner = owner)
        this.owner = owner
        this.uid = uid
        this.ip = ip
        this.client = client
        this.device = device
        this.descr = descr
        this.expire = Date((expire * 1000).toLong())
        this.authDate = Date((authDate * 1000).toLong())
    }


    fun getResource(realm: Realm): ResourceStorageItem? {
        return try {
            resource?.let { resourceValue ->
                realm.query<ResourceStorageItem>(
                    "primary = $0",
                    com.xabber.xmpp.presence.ResourceStorageItem.genPrimary(
                        jid = owner,
                        owner = owner,
                        resource = resourceValue
                    )
                ).first().find()
            }
        } catch (e: Exception) {
            logger.warning("Failed to fetch ResourceStorageItem: ${e.message}")
            null
        }
    }
}