package com.xabber.presentation.application

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import com.xabber.models.dto.AvatarDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import com.xabber.presentation.application.activity.MaskManager
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.*

class BaseViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())
    private val _colorKey = MutableLiveData<String>()
    val colorKey: LiveData<String> = _colorKey
    private val _avatar = MutableLiveData<String>()
    val avatar: LiveData<String> = _colorKey
    private var job: Job? = null

    private val _account = MutableLiveData<String?>()
    val account: LiveData<String?> = _account

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
                        val primaryAccount = a.minByOrNull { T -> T.order }?.jid
                        withContext(Dispatchers.Main) {
                            _account.value = primaryAccount
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun initPrimaryAccountListener(id: String?) {
        if (id == null) {
            job?.cancel()
            _colorKey.value = "offline"
        } else {
            if (job != null && job!!.isActive) job?.cancel()
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val request =
                        realm.query(AccountStorageItem::class, "primary = '$id'")
                    request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                        when (changes) {
                            is UpdatedResults -> {
                                val primaryAccount = changes.list[0]
                                withContext(Dispatchers.Main) {
                                    _colorKey.value = primaryAccount.colorKey
                                }
                            }
                            else -> {}
                        }
                    }
                }
        }
    }

  fun getPrimaryAccount(): AccountDto? {
      var accountDto: AccountDto? = null
   //   viewModelScope.launch(Dispatchers.IO) {
          val realmAccounts = realm.query(AccountStorageItem::class, "enabled = true").find()
          val primaryAccount = realmAccounts.minByOrNull { T -> T.order }
          if (primaryAccount != null) {
              accountDto = AccountDto(
                  id = primaryAccount.primary,
                  order = primaryAccount.order,
                  colorKey = primaryAccount.colorKey,
                  hasAvatar = primaryAccount.hasAvatar,
                  jid = primaryAccount.jid,
                  nickname = primaryAccount.nickname,
                  enabled = primaryAccount.enabled
              )
          }
  //    }
      return accountDto
    }



    fun getAvatar(jid: String): AvatarDto? {
        var avatarDto: AvatarDto? = null
        realm.writeBlocking {
            val realmAvatar =
                this.query(AvatarStorageItem::class, "primary = '$jid'").first().find()
            if (realmAvatar != null)
                avatarDto = AvatarDto(
                    id = realmAvatar.primary,
                    owner = realmAvatar.owner,
                    jid = realmAvatar.jid,
                    uploadUrl = realmAvatar.uploadUrl,
                    fileUri = realmAvatar.fileUri,
                    image96 = realmAvatar.image96,
                    image128 = realmAvatar.image128,
                    image192 = realmAvatar.image192,
                    image384 = realmAvatar.image384,
                    image512 = realmAvatar.image512
                )
        }
        return avatarDto
    }

    fun initAvatarListener(id: String?) {
        if (id == null) {
            job?.cancel()
        } else {
            if (job != null && job!!.isActive) job?.cancel()
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val request =
                        realm.query(AvatarStorageItem::class, "primary = '$id'")
                    request.asFlow().collect { changes: ResultsChange<AvatarStorageItem> ->
                        when (changes) {
                            is UpdatedResults -> {
                                val avatar = changes.list[0]
                                withContext(Dispatchers.Main) {
                                    _avatar.value = avatar.fileUri
                                }
                            }
                            else -> {}
                        }
                    }
                }
        }
    }


    override fun onCleared() {
        super.onCleared()
        realm.close()
        job?.cancel()
    }

}
