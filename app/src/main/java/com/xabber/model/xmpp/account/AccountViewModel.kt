package com.xabber.model.xmpp.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.defaultRealmConfig
import io.realm.Realm
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountViewModel : ViewModel() {
    private val _primaryColor = MutableLiveData<Int>()
    val primaryColor: LiveData<Int> = _primaryColor

    val realm = Realm.open(defaultRealmConfig())

    fun initAccountDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastChatsFlow = realm.query(AccountStorageItem::class).asFlow()

            lastChatsFlow.collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                    }
                }
            }
        }
    }
}