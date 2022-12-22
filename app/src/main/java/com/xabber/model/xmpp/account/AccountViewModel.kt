package com.xabber.model.xmpp.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.presentation.application.fragments.account.color.MaterialColor
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountViewModel : ViewModel() {
    private val _primaryColor = MutableLiveData<Int>()
    val primaryColor: LiveData<Int> = _primaryColor
    val realm = Realm.open(defaultRealmConfig())

    init {
        initPrimaryAccountDataListener()
    }

    private fun initPrimaryAccountDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastChatsFlow = realm.query(AccountStorageItem::class, "order = 0").asFlow()

            lastChatsFlow.collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                       changes.list[0].colorKey
                        MaterialColor.values()
                    }
                    else -> {}
                }
            }
        }
    }
}