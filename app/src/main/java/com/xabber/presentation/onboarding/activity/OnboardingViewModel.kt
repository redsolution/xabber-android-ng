package com.xabber.presentation.onboarding.activity

import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.HostListDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.presentation.XabberApplication
import com.xabber.repository.AccountRepository
import io.reactivex.rxjava3.core.Single
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.random.Random

class OnboardingViewModel : ViewModel() {
    private val PASSWORD_KEY = "password"
    private val passwordStorage: PasswordStorageHelper =
        PasswordStorageHelper(XabberApplication.applicationContext())

    private val accountRepository = AccountRepository()
    val realm = Realm.open(defaultRealmConfig())

    private var nickName: String? = null

    private var jid: String? = null

    private var password: String? = null

    private val _avatarBitmap = MutableLiveData<Bitmap>()
    val avatarBitmap: LiveData<Bitmap> = _avatarBitmap

    private val _avatarUri = MutableLiveData<Uri>()
    val avatarUri: LiveData<Uri> = _avatarUri

    var avatarName: String = ""

    fun setNickName(newNickName: String) {
        nickName = newNickName
    }

    fun setJid(newJid: String) {
        jid = newJid
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
        if (nickName != null && jid != null && password != null) {
            savePassword(password!!)
            viewModelScope.launch(Dispatchers.IO) {
                realm.writeBlocking {
                    this.copyToRealm(AccountStorageItem().apply {
                        order = 0
                        jid = this@OnboardingViewModel.jid!!
                        nickname = this@OnboardingViewModel.nickName!!
                        enabled = true
                        colorKey = "blue"
                    })
                }
            }
        }
    }

    private fun savePassword(password: String) {
        passwordStorage.setData(PASSWORD_KEY, password.toByteArray())
        Log.d("ppp", "password = ${getPassword()}")
    }


    fun getPassword(): String {
        return String((passwordStorage.getData(PASSWORD_KEY) ?: ByteArray(0)))
    }

    fun removePassword() {
        passwordStorage.remove(PASSWORD_KEY)
    }

    fun saveAvatar(name: String) {
       avatarName = name
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
