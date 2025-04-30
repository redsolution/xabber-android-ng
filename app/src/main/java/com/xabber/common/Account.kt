package com.xabber.common

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.utils.custom.NickGenerator
import io.reactivex.subjects.BehaviorSubject
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import java.util.Date

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

    //observable
    var statusMessage: BehaviorSubject<String> = BehaviorSubject.createDefault("Offline")

    //custon models



    fun load() {
        try {
            val realm = Realm.open(defaultRealmConfig())
            val item = realm.query(AccountStorageItem::class, "primary == $0", jid)
                .first()
                .find()

            item?.let {
                this.jid = it.jid
                this.host = it.host

                this.port = it.port
                it.resource?.resource?.let { res ->
                    this.resource = res
                }
                this.username = it.username

            }

            if (this.deviceName.isEmpty()) {
                this.deviceName = NickGenerator.genRandomNick()
            }
        } catch (e: Exception) {
            Log.e("Account","cant load user ${this.jid} from db")
        }
    }

    fun create() {
        try {
            val realm = Realm.open(defaultRealmConfig())
            realm.writeBlocking {
                val item = AccountStorageItem().apply {
                    order = query<AccountStorageItem>().find().size
                    jid = this@Account.jid
                    host = this@Account.host
                    savePassword = this@Account.savePassword
                    manuallySetHost = this@Account.manuallySetHost
                    port = this@Account.port

                    username = this@Account.username


                    createdAt = System.currentTimeMillis()

                    deviceName = this@Account.deviceName
                }

                copyToRealm(item, updatePolicy = UpdatePolicy.ALL)
            }
        } catch (e: Exception) {
            Log.d("Account", "Can't update push info for user ${this.jid}", e)
        }
    }

    fun isExist(jid: String): Boolean {
        try {
            val realm = Realm.open(defaultRealmConfig())
            return realm.query(AccountStorageItem::class, "jid = $0", jid).first().find() == null
        } catch (e: Exception) {
            // Assuming DDLogDebug is a logging utility
            Log.d("Existing Account", "cant get information about new user $jid", e)
        }
        return true
    }

}