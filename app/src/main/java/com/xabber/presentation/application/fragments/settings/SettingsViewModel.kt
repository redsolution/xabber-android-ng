package com.xabber.presentation.application.fragments.settings

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import com.xabber.models.xmpp.account.AccountStorageItem
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
    private var dataSources = ArrayList<AccountDto>()

    fun getAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                dataSources.clear()
                dataSources.addAll(this.query(AccountStorageItem::class).find().map {
                    AccountDto(
                        id = it.primary,
                        order = it.order,
                        jid = it.jid,
                        nickname = it.nickname,
                        enabled = it.enabled,
                        statusMessage = it.statusMessage,
                        colorKey = it.colorKey
                    )
                })
            }
            withContext(Dispatchers.Main) {
                _accounts.value = dataSources
            }
        }

    }

    fun initializeDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        dataSources.clear()
                        dataSources.addAll(changes.list.map { T ->
                            AccountDto(
                                id = T.primary,
                                order = T.order,
                                jid = T.jid,
                                nickname = T.nickname,
                                enabled = T.enabled,
                                statusMessage = T.statusMessage,
                                colorKey = T.colorKey
                            )
                        })
                        withContext(Dispatchers.Main) {
                            _accounts.value = dataSources
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun setEnabled(jid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val account = this.query(AccountStorageItem::class).first().find()
                account?.enabled = !account!!.enabled
            }
        }
    }


    fun initDataListener() {
        Log.d("itt","settings change")
      viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class)
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
                                colorKey = T.colorKey
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

    private fun getEnableAccountList(): ArrayList<String> {
        val accountList = ArrayList<String>()
        realm.writeBlocking {
            val accounts = this.query(AccountStorageItem::class, "enabled = true").find()
            accountList.addAll(accounts.map { T ->
                T.jid
            })
        }
        return accountList
    }

    fun getAccountList() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                   AccountStorageItem::class
                ).find()
            val dataSource = ArrayList<AccountDto>()
            dataSource.addAll(realmList.map { T ->
                AccountDto(
                    id = T.primary,
                    order = T.order,
                    jid = T.jid,
                    nickname = T.nickname,
                    enabled = T.enabled,
                    statusMessage = T.statusMessage,
                    colorKey = T.colorKey
                )
            })
            withContext(Dispatchers.Main) {
                _accounts.value = dataSource
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }






}
