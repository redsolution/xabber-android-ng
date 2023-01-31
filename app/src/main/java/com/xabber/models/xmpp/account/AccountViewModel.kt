package com.xabber.models.xmpp.account

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val _accounts = MutableLiveData<List<AccountDto>>()
    val accounts: LiveData<List<AccountDto>> = _accounts
    private val _avatarBitmap = MutableLiveData<Bitmap>()
    val avatarBitmap: LiveData<Bitmap> = _avatarBitmap


    fun setAvatarBitmap(bitmap: Bitmap) {
        _avatarBitmap.value = bitmap
    }

    fun addAccount(accountJid: String) {
//        val accountOrder = defineAccountOrder()
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                this.copyToRealm(AccountStorageItem().apply {
//                    primary = accountJid
//                    order = accountOrder
//                    jid = accountJid
//                })
//            }
//        }
    }

    private fun defineAccountOrder(): Int {
        var order = 0
        viewModelScope.launch(Dispatchers.IO) { }
        realm.writeBlocking {
            val accountList = this.query(AccountStorageItem::class).find()
            order = accountList.size
        }
        return order
    }

    fun setAvatar(jid: String, uri: String?) {
Log.d("bbb", "setAvatar")
            realm.writeBlocking {
                val item = this.query(AccountStorageItem::class).first().find()
              item?.hasAvatar = !item!!.hasAvatar
            }
    }

    fun getAccount(jid: String): AccountDto? {
        var account: AccountDto? = null
        realm.writeBlocking {
            val item = this.query(AccountStorageItem::class, "jid = '$jid'").first().find()
            if (item != null) account = AccountDto(
                id = item.primary,
                jid = item.jid,
                order = item.order,
                nickname = item.nickname,
                enabled = item.enabled,
                statusMessage = item.statusMessage,
                colorKey = item.colorKey,
                hasAvatar = item.hasAvatar
            )
        }
        return account
    }

    fun setEnabled(jid: String) {
        realm.writeBlocking {
            val account = this.query(AccountStorageItem::class).first().find()
            account?.enabled = !account!!.enabled
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }


    fun initDataListener() {
        Log.d("itt","${realm.query(AccountStorageItem::class).find()}")
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {

                    is UpdatedResults -> {
                        Log.d("bbb", "chsnges")
                        changes.list
                        val dataSource = ArrayList<AccountDto>()
                        dataSource.addAll(changes.list.map { T ->
                            AccountDto(
                                id = T.primary,
                                order = T.order,
                                jid = T.jid,
                                nickname = T.nickname,
                                enabled = T.enabled,
                                statusMessage = T.statusMessage,
                                colorKey = T.colorKey,
                                hasAvatar = T.hasAvatar
                            )
                        })
                        withContext(Dispatchers.Main) {
                            _accounts.value = dataSource
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun saveAvatar(bitmap: Bitmap) {
_avatarBitmap.value = bitmap
    }

}