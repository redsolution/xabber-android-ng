package com.xabber.common

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.utils.custom.NickGenerator
import io.reactivex.subjects.BehaviorSubject
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
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
    var stream: Stream? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    fun setOnErrorCallback(callback: (String) -> Unit) {
        onErrorCallback = callback
        stream?.setOnErrorCallback { error ->
            callback(error)
            statusMessage.onNext("Offline")
        }
    }

    suspend fun loadAccount() = withContext(Dispatchers.IO) {
        try {
            val realm = Realm.open(defaultRealmConfig())
            val item = realm.query(AccountStorageItem::class, "primary == $0", jid).first().find()
            item?.let {
                this@Account.jid = it.jid
                this@Account.host = it.host
                this@Account.port = it.port
                it.resource?.resource?.let { res -> this@Account.resource = res }
                this@Account.username = it.username
            }
            if (this@Account.deviceName.isEmpty()) {
                this@Account.deviceName = NickGenerator.genRandomNick()
            }
            realm.close()
            initializeStream()
        } catch (e: Exception) {
            Log.e("Account", "Can't load user $jid from db", e)
            onErrorCallback?.invoke("Error loading account: ${e.message}")
        }
    }

    suspend fun create() {
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
                    createdAt = System.currentTimeMillis() // Set createdAt as Long
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
            val exists = realm.query(AccountStorageItem::class, "jid = $0", jid).first().find() != null
            realm.close()
            return exists
        } catch (e: Exception) {
            Log.e("Account", "Error checking account existence for $jid: ${e.message}", e)
            return false
        }
    }

    private suspend fun initializeStream() = withContext(Dispatchers.IO) {
        try {
            if (jid.isNotEmpty()) {
                stream?.close()
                stream = Stream(jid, port).apply {
                    onErrorCallback?.let { setOnErrorCallback(it) }
                }
                Log.d("Account", "Stream initialized for $jid with port $port")
            } else {
                Log.w("Account", "Cannot initialize Stream: JID is empty")
                onErrorCallback?.invoke("Cannot initialize connection: Invalid JID")
            }
        } catch (e: Exception) {
            Log.e("Account", "Error initializing Stream for $jid: ${e.message}", e)
            onErrorCallback?.invoke("Error initializing connection: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun connectStream(): Boolean = withContext(Dispatchers.IO) {
        try {
            stream?.let {
                val connectError = it.connect()
                if (connectError == null) {
                    statusMessage.onNext("Online")
                    Log.d("Account", "Stream connected for $jid")
                    true
                } else {
                    statusMessage.onNext("Offline")
                    Log.e("Account", "Stream connection failed for $jid: $connectError")
                    onErrorCallback?.invoke(connectError)
                    false
                }
            } ?: run {
                Log.w("Account", "No Stream initialized for $jid")
                onErrorCallback?.invoke("No connection initialized")
                false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error connecting Stream for $jid: ${e.message}", e)
            onErrorCallback?.invoke("Connection error: ${e.message}")
            statusMessage.onNext("Offline")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun closeStream() = withContext(Dispatchers.IO) {
        stream?.close()
        stream = null
        statusMessage.onNext("Offline")
        Log.d("Account", "Stream closed for $jid")
    }
}