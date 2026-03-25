package com.xabber.presentation.application.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.dto.AccountDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApplicationViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    var showUnreadOnly = false

    private val _unreadMessages = MutableLiveData<Int>()
    val unreadMessage: LiveData<Int> = _unreadMessages
    private var unreadListenerStarted = false

    fun checkIsEntry(): Boolean {
        return realm.query<AccountStorageItem>().first().find() != null
    }

    fun initUnreadMessagesCountListener() {
        if (unreadListenerStarted) return
        unreadListenerStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = getEnabledAccountIds()
            val query = ApplicationUnreadQueryBuilder.build(accounts)
            if (query == null) {
                withContext(Dispatchers.Main) { _unreadMessages.value = 0 }
                return@launch
            }
            val request = realm.query(LastChatsStorageItem::class, query)
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is InitialResults -> publishUnreadCount(changes.list)
                    is UpdatedResults -> {
                        publishUnreadCount(changes.list)
                    }
                }
            }
        }
    }

    private suspend fun publishUnreadCount(chats: List<LastChatsStorageItem>) {
        val count = chats.sumOf { it.unread }
        withContext(Dispatchers.Main) {
            _unreadMessages.value = count
        }
    }

    private fun getEnabledAccountIds(): Set<String> {
        return realm.query<AccountStorageItem>("enabled = true")
            .find()
            .mapTo(linkedSetOf()) { it.primary }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
