package com.xabber.presentation.application.manage

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.utils.custom.NickGenerator
import com.xabber.xmpp.messages.message.CommonConfigManager
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import java.util.Date
import io.realm.kotlin.types.RealmInstant

class Account {
    var jid: String =""
    override fun toString(): String {
        return "Account $jid"
    }
    var host: String = ""
    var port: Int = 5222
    var username: String = ""
    //Settings
    var supportTokens: Boolean = false
    var tokenUid: String = ""
    var savePassword: Boolean = true
    var useSecureConnection: Boolean = false
    var manuallySetHost: Boolean = false
    var resource: String = ""
    var priority: Int = 0

    //service data
    var deviceName: String = ""


    var push: PushNotificationsManager


    fun load() {
        try {
            val realm = Realm.open(defaultRealmConfig())
            val item = realm.query(AccountStorageItem::class, "primary == $0", jid)
                .first()
                .find()

            item?.let {
                this.jid = it.jid
                this.host = it.host
                this.supportTokens = it.xTokenSupport
                this.tokenUid = it.xTokenUID
                this.savePassword = it.savePassword
                this.manuallySetHost = it.manuallySetHost
                this.port = it.port
                this.deviceName = it.deviceName
                it.resource?.resource?.let { res ->
                    this.resource = res
                }
                this.username = it.username
                this.push.node = it.node
                this.push.service = it.service
            }

            if (this.deviceName.isEmpty()) {
                this.deviceName = NickGenerator.genRandomNick()
            }
        } catch (e: Exception) {
            Log.e("Account","cant load user ${this.jid} from db")
        }
    }

    fun save() {
        try {
            val realm = Realm.open(defaultRealmConfig())
            realm.writeBlocking {
                val item = AccountStorageItem().apply {
                    order = query<AccountStorageItem>().count().toInt()
                    jid = this@Account.jid
                    host = this@Account.host
                    savePassword = this@Account.savePassword
                    manuallySetHost = this@Account.manuallySetHost
                    port = this@Account.port

                    username = this@Account.username
                    node = this@Account.push.node
                    service = this@Account.push.service
                    statusMessage = this@Account.statusMessage.value
                    xTokenSupport = this@Account.supportTokens
                    xTokenUID = this@Account.tokenUid
                    createdAt = Date()

                    deviceName = this@Account.deviceName
                    deviceUuid = this@Account.devices.deviceId
                }

                copyToRealm(item, updatePolicy = UpdatePolicy.MODIFIED)
            }
        } catch (e: Exception) {
            Log.d("Account", "Can't update push info for user ${this.jid}", e)
        }
    }
}