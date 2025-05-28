package com.xabber.common

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.utils.custom.NickGenerator
import io.reactivex.subjects.BehaviorSubject
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Account {
    var jid: String = ""
    override fun toString(): String {
        return "Account $jid"
    }
    var host: String = ""
    var port: Int = 5222
    var username: String = ""
    var supportTokens: Boolean = false
    var tokenUid: String = ""
    var savePassword: Boolean = true
    var useSecureConnection: Boolean = false
    var manuallySetHost: Boolean = false
    var resource: String = ""
    var priority: Int = 0
    var deviceName: String = ""
    var statusMessage: BehaviorSubject<String> = BehaviorSubject.createDefault("Offline")
    private var stream: Stream? = null

    fun loadAccount() {
        try {
            val realm = Realm.open(defaultRealmConfig())
            val item = realm.query(AccountStorageItem::class, "primary == $0", jid).first().find()
            item?.let {
                this.jid = it.jid
                this.host = it.host
                this.port = it.port
                it.resource?.resource?.let { res -> this.resource = res }
                this.username = it.username
            }
            if (this.deviceName.isEmpty()) {
                this.deviceName = NickGenerator.genRandomNick()
            }
            initializeStream()
        } catch (e: Exception) {
            Log.e("Account", "Can't load user ${this.jid} from db", e)
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
                    createdAt = 0
                    deviceName = this@Account.deviceName
                }
                copyToRealm(item, updatePolicy = UpdatePolicy.ALL)
            }
            initializeStream()
        } catch (e: Exception) {
            Log.d("Account", "Can't update push info for user ${this.jid}", e)
        }
    }

    fun isExist(jid: String): Boolean {
        try {
            val realm = Realm.open(defaultRealmConfig())
            return realm.query(AccountStorageItem::class, "jid = $0", jid).first().find() == null
        } catch (e: Exception) {
            Log.d("Existing Account", "Can't get information about new user $jid", e)
        }
        return true
    }

    private fun initializeStream() {
        try {
            if (jid.isNotEmpty()) {
                stream = Stream(jid, port)
                Log.d("Account", "Stream initialized for $jid with port $port")
            } else {
                Log.w("Account", "Cannot initialize Stream: JID is empty")
            }
        } catch (e: Exception) {
            Log.e("Account", "Error initializing Stream for $jid: ${e.message}", e)
        }
    }

    suspend fun connectStream(): Boolean = withContext(Dispatchers.IO) {
        try {
            stream?.let {
                val connected = it.connect()
                if (connected) {
                    statusMessage.onNext("Online")
                    Log.d("Account", "Stream connected and XMPP stream initiated for $jid")
                } else {
                    statusMessage.onNext("Offline")
                    Log.e("Account", "Stream connection or XMPP stream initiation failed for $jid")
                }
                connected
            } ?: run {
                Log.w("Account", "No Stream initialized for $jid")
                false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error connecting Stream for $jid: ${e.message}", e)
            statusMessage.onNext("Offline")
            false
        }
    }

    suspend fun closeStream() = withContext(Dispatchers.IO) {
        stream?.close()
        stream = null
        statusMessage.onNext("Offline")
        Log.d("Account", "Stream closed for $jid")
    }

    fun getStream(): Stream? {
        return stream
    }
}