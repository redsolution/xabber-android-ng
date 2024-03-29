package com.xabber.presentation.application.fragments.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.AccountDto
import com.xabber.dto.AvatarDto
import com.xabber.utils.toAccountDto
import com.xabber.utils.toAvatarDto
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
    private val _avatars = MutableLiveData<List<AvatarDto>>()
    val avatars: LiveData<List<AvatarDto>> = _avatars

    init {
        initAccountsDataListener()
        initAvatarsListener()
    }

    private fun initAccountsDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val dataSource = ArrayList<AccountDto>()
            val request =
                realm.query(com.xabber.data_base.models.account.AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<com.xabber.data_base.models.account.AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        dataSource.clear()
                        dataSource.addAll(changes.list.map { T ->
                            T.toAccountDto()
                        })
                        dataSource.sort()
                        withContext(Dispatchers.Main) {
                            _accounts.value = dataSource
                        }
                    }
                    else -> {}
                }
            }
        }
    }


    fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val dataSource = ArrayList<AccountDto>()
            realm.writeBlocking {
                val accountStorageItems = this.query(com.xabber.data_base.models.account.AccountStorageItem::class)
                    .find()
                dataSource.addAll(accountStorageItems.map { T ->
                    T.toAccountDto()
                })
                dataSource.sort()
            }
            withContext(Dispatchers.Main) {
                _accounts.value = dataSource
            }
        }
    }


    private fun initAvatarsListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<com.xabber.data_base.models.avatar.AvatarStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val avatars = ArrayList<AvatarDto>()
                        avatars.addAll(changes.list.map { T ->
                            T.toAvatarDto()
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
                    this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "primary = '$id'").first().find()
                if (account != null) findLatest(account)?.enabled = isChecked
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

}
