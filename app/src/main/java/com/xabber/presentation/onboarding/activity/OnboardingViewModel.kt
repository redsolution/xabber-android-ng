package com.xabber.presentation.onboarding.activity

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.model.dto.HostListDto
import com.xabber.model.xmpp.account.AccountStorageItem
import com.xabber.repository.AccountRepository
import io.reactivex.rxjava3.core.Single
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OnboardingViewModel : ViewModel() {
    private val accountRepository = AccountRepository()
    val realm = Realm.open(defaultRealmConfig())

    private var nickName: String? = null

    private var userName: String? = null

    private var password: String? = null

    private val _avatarBitmap = MutableLiveData<Bitmap>()
    val avatarBitmap: LiveData<Bitmap> = _avatarBitmap

    private val _avatarUri = MutableLiveData<Uri>()
    val avatarUri: LiveData<Uri> = _avatarUri

    fun setNickName(newNickName: String) {
        nickName = newNickName
    }

    fun setUserName(newUserName: String) {
        userName = newUserName
    }

    fun setPassword(newPassword: String) {
       password = newPassword
    }

    fun setAvatarBitmap(bitmap: Bitmap) {
        _avatarBitmap.value = bitmap
    }

    fun setAvatarUri(uri: Uri) {
        _avatarUri.value = uri
    }

    fun getHost(): Single<HostListDto> = accountRepository.getHostList()

    fun checkIsNameAvailable(username: String, host: String): Boolean = true
//        accountRepository.checkIfNameAvailable(
//            mapOf(
//                "username" to username,
//                "host" to host,
//                "no_captcha_key" to AppConstants.NO_CAPTCHA_KEY,
//            )
//        )


    fun registerAccount() {
        val accountOrder = defineAccountOrder()
        if (nickName != null && userName != null && password != null) {
            viewModelScope.launch(Dispatchers.IO) {
                realm.writeBlocking {
                    this.copyToRealm(AccountStorageItem().apply {
                        order = accountOrder
                        jid = userName!!
                        nickname = nickName!!
                    })
                }
                realm.writeBlocking {
                   val a = this.query(AccountStorageItem::class).first().find()
                    Log.d("entry", "account = $a")
                }
            }
        }
    }

    fun saveAvatar() {
        viewModelScope.launch {
            realm.writeBlocking {

            }
        }
    }

    private fun defineAccountOrder(): Int {
        var order = 0
        viewModelScope.launch(Dispatchers.IO) { }
        realm.writeBlocking {
            val accountList = this.query(AccountStorageItem::class).find()
            order = accountList.size
        }
        return order
    }

}
