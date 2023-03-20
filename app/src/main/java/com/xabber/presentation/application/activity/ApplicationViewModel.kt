package com.xabber.presentation.application.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApplicationViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    var showUnreadOnly = false

    private val enabledAccounts = HashSet<String>()
    private val _unreadMessages = MutableLiveData<Int>()
    val unreadMessage: LiveData<Int> = _unreadMessages

    init {
        getUnreadMessages()
    }

    fun checkIsEntry(): Boolean {
        var isEntry = false
        realm.writeBlocking {
            val account = this.query(AccountStorageItem::class).first().find()
            isEntry = account != null
        }
        return isEntry
    }

    fun initAccountListListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class, "enabled = true")
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list.forEach { enabledAccounts.add(it.jid) }
                    }
                    else -> {}
                }
            }
        }
    }


    fun initUnreadMessagesCountListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(
                    LastChatsStorageItem::class,
                    "isArchived = false && muteExpired <= 0 && unread > 0"
                )
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        var count = 0
                        changes.list.forEach { count += it.unread }
                        withContext(Dispatchers.Main) { _unreadMessages.value = count }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getUnreadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(
                    LastChatsStorageItem::class,
                    "isArchived = false && muteExpired <= 0 && unread > 0"
                ).find()
            var count = 0
            request.forEach {
                if (it.unread > 0) count += it.unread
            }
            withContext(Dispatchers.Main) {
                _unreadMessages.value = count
            }
        }
    }

}
