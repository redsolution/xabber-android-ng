package com.xabber.models.xmpp.account

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import com.xabber.models.dto.AvatarDto
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import com.xabber.presentation.application.activity.ColorManager
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
    private val _colorKey = MutableLiveData<String>()
    val colorKey: LiveData<String> = _colorKey

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

    fun setEnabled(jid: String, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val account =
                    this.query(AccountStorageItem::class, "primary = '$jid'").first().find()
                if (account != null) account.enabled = isChecked
            }
        }
    }

    fun initDataListener(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class, "primary = '$id'")
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
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
                            _colorKey.value = dataSource[0].colorKey
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun deleteAvatar(id: String) {
        realm.writeBlocking {
            val avatar = this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
            if (avatar != null) findLatest(avatar)?.let { delete(it) }

            val account = this.query(AccountStorageItem::class, "primary = '$id'").first().find()
            account?.hasAvatar = false
        }
    }

    fun saveAvatar(id: String, uri: String) {
        realm.writeBlocking {
            val avatar = this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
            if (avatar == null) {
                this.copyToRealm(AvatarStorageItem().apply {
                    primary = id
                    fileUri = uri
                    jid = id
                    owner = id

                })
            } else avatar.fileUri = uri
            val account = this.query(AccountStorageItem::class, "jid = '$id'").first().find()
            account?.hasAvatar = true
        }
    }

    fun getAvatar(id: String): AvatarDto? {
        var avatarDto: AvatarDto? = null
        realm.writeBlocking {
            val avatarStorageItem =
                this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
            if (avatarStorageItem != null) {
                avatarDto = AvatarDto(
                    id = avatarStorageItem.primary,
                    jid = avatarStorageItem.jid,
                    fileUri = avatarStorageItem.fileUri
                )
            }
        }
        return avatarDto
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

}