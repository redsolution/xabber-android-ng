package com.xabber.presentation.application.fragments.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import com.xabber.models.dto.AvatarDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())
    private val _accounts = MutableLiveData<List<AccountDto>>()
    val accounts: LiveData<List<AccountDto>> = _accounts
    private val dataSource = ArrayList<AccountDto>()

    private val _avatars = MutableLiveData<List<AvatarDto>>()
    val avatars: LiveData<List<AvatarDto>> = _avatars
    init {
       getAccounts()
   initAccountsDataListener()
initAvatarsListener()
    }

  fun initAccountsDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                     dataSource.clear()
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

    fun getAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                dataSource.clear()
                dataSource.addAll(this.query(AccountStorageItem::class).find().map {
                    AccountDto(
                        id = it.primary,
                        order = it.order,
                        jid = it.jid,
                        nickname = it.nickname,
                        enabled = it.enabled,
                        statusMessage = it.statusMessage,
                        colorKey = it.colorKey,
                        hasAvatar = it.hasAvatar
                    )
                })
            }
            withContext(Dispatchers.Main) {
                _accounts.value = dataSource
            }
        }
    }

    fun initAvatarsListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AvatarStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<AvatarStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val avatars = ArrayList<AvatarDto>()
                       avatars.addAll(changes.list.map { T ->
                            AvatarDto(
                                id = T.primary,
                               jid = T.jid,
                                owner = T.owner,
                                fileUri = T.fileUri
                            )
                        })
                        withContext(Dispatchers.Main) {
                            _avatars.value = avatars
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun setEnabled(id: String, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val account =
                    this.query(AccountStorageItem::class, "primary = '$id'").first().find()
                if (account != null) findLatest(account)?.enabled = isChecked
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

}
