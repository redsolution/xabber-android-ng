package com.xabber.presentation.application.fragments.account

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.xmpp.avatar.AvatarStorageItem
import com.xabber.dto.AccountDto
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.onboarding.util.PasswordStorageHelper
import com.xabber.utils.toAccountDto
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
class AccountViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val passwordStorage: PasswordStorageHelper =
        PasswordStorageHelper(XabberApplication.applicationContext())
    private val accountStorageItemDao = AccountManager
    private val _accounts = MutableLiveData<List<AccountDto>>()
    val accounts: LiveData<List<AccountDto>> = _accounts
    private val _colorKey = MutableLiveData<String>()
    val colorKey: LiveData<String> = _colorKey
    private val _avatarBitmap = MutableLiveData<Bitmap>()
    val avatarBitmap: LiveData<Bitmap> = _avatarBitmap
    private val _avatarUri = MutableLiveData<Uri>()
    val avatarUri: LiveData<Uri> = _avatarUri

    fun checkIsNameAvailable(username: String, host: String): Boolean =
        true // Placeholder for server implementation

    fun setColor(id: String, color: String) {
        viewModelScope.launch(Dispatchers.IO) {
            accountStorageItemDao.setColorKey(id, color)
        }
    }

    fun addAccount(
        accountJid: String,
        userName: String,
        accountColor: String,
        accountHasAvatar: Boolean = false,
        password: String
    ) {
        accountStorageItemDao.createAccount(accountJid, userName)
        passwordStorage.setData(accountJid, password.toByteArray())
    }

    fun getAccount(id: String): AccountDto? {
        val account = accountStorageItemDao.getAccount(id)?.toAccountDto()
        Log.d("AccountViewModel", "getAccount for id=$id: $account")
        return account
    }

    fun setEnabled(id: String, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            accountStorageItemDao.setEnabled(id, isChecked)
        }
    }

    fun initDataListener(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = realm.query(AccountStorageItem::class, "primary = '$id'")
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val accountList = changes.list
                        Log.d("AccountViewModel", "Raw AccountStorageItems for id=$id: ${accountList.map { "jid=${it.jid}, nickname=${it.username}, colorKey=${it.colorKey}, enabled=${it.enabled}, hasAvatar=${it.hasAvatar}" }}")
                        val dataSource = ArrayList<AccountDto>()
                        dataSource.addAll(accountList.map {
                            it.toAccountDto().copy(
                                jid = it.jid ?: "",
                                nickname = it.username ?: "",
                                colorKey = it.colorKey ?: "blue" // Default color
                            )
                        })
                        withContext(Dispatchers.Main) {
                            _accounts.value = dataSource
                            _colorKey.value = if (dataSource.isNotEmpty()) {
                                dataSource[0].colorKey
                            } else {
                                "blue" // Default color
                            }
                            Log.d("AccountViewModel", "Updated accounts: ${dataSource.map { it.jid }}, colorKey: ${_colorKey.value}")
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
            Log.d("AccountViewModel", "Deleted avatar for id=$id")
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
            } else {
                avatar.fileUri = uri
            }
            val account = this.query(AccountStorageItem::class, "primary = '$id'").first().find()
            account?.hasAvatar = true
            Log.d("AccountViewModel", "Saved avatar for id=$id, uri=$uri")
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }
}