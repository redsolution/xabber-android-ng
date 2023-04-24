package com.xabber.presentation.application

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import com.xabber.models.dto.AvatarDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import com.xabber.utils.toAccountDto
import com.xabber.utils.toAvatarDto
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BaseViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())
    private val _colorKey = MutableLiveData<String>()
    val colorKey: LiveData<String> = _colorKey
    val avatar: LiveData<String> = _colorKey

    init {
        initAccountsListener()
    }

    private fun initAccountsListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val a = changes.list.filter { T -> T.enabled }
                        val primaryAccount = a.minByOrNull { T -> T.order }
                        val color = primaryAccount?.colorKey
                        withContext(Dispatchers.Main) {
                            _colorKey.value = color ?: "offline"
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { T -> T.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
        }
        return accountDto
    }

    fun getAvatar(id: String): AvatarDto? {
        var avatarDto: AvatarDto? = null
        realm.writeBlocking {
            val realmAvatar =
                this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
            if (realmAvatar != null)
                avatarDto = realmAvatar.toAvatarDto()
        }
        return avatarDto
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

}
