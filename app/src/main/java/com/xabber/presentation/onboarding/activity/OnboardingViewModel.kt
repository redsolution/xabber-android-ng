package com.xabber.presentation.onboarding.activity

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.HostListDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import com.xabber.models.xmpp.presences.ResourceStorageItem
import com.xabber.presentation.XabberApplication
import com.xabber.repository.AccountRepository
import io.reactivex.rxjava3.core.Single
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OnboardingViewModel : ViewModel() {
    companion object {
        const val PASSWORD_KEY = "password key"
    }

    private val passwordStorage: PasswordStorageHelper =
        PasswordStorageHelper(XabberApplication.applicationContext())

    private val accountRepository = AccountRepository()
    private val realm = Realm.open(defaultRealmConfig())
    private val defaultColor = "blue"
    private val accountOrder = 0

    private var accountNickName: String? = null

    private var accountJid: String? = null

    private var password: String? = null

    private var savedUri: String? = null

    private val _avatarBitmap = MutableLiveData<Bitmap>()
    val avatarBitmap: LiveData<Bitmap> = _avatarBitmap

    private val _avatarUri = MutableLiveData<Uri>()
    val avatarUri: LiveData<Uri> = _avatarUri

    fun setAvatarBitmap(bitmap: Bitmap) {
        _avatarBitmap.value = bitmap
    }

    fun setAvatarUri(uri: Uri) {
        _avatarUri.value = uri
    }

    fun setNickName(newNickName: String) {
        accountNickName = newNickName
    }

    fun setJid(newJid: String) {
        accountJid = newJid
    }

    fun setPassword(newPassword: String) {
        password = newPassword
    }

    fun setSavedAvatarUri(uri: String) {
        savedUri = uri
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
        shiftAccountOrderList()
        if (accountNickName != null && accountJid != null && password != null) {
            savePassword(password!!)
            viewModelScope.launch(Dispatchers.IO) {
                realm.writeBlocking {
                   val accountResource = this.copyToRealm(ResourceStorageItem().apply {

                    })

                    this.copyToRealm(AccountStorageItem().apply {
                        primary = accountJid!!
                        order = accountOrder
                        jid = accountJid!!
                        nickname = accountNickName!!
                        enabled = true
                        colorKey = defaultColor
                        hasAvatar = savedUri != null
                        resource = accountResource
                    })

                    if (savedUri != null)
                        this.copyToRealm(AvatarStorageItem().apply {
                            primary = accountJid!!
                            jid = accountJid!!
                            owner = accountJid!!
                            fileUri = savedUri!!
                        })
                }
            }
        }
    }

    private fun savePassword(password: String) {
        passwordStorage.setData(PASSWORD_KEY, password.toByteArray())
    }

    fun getPassword(): String {
        return String((passwordStorage.getData(PASSWORD_KEY) ?: ByteArray(0)))
    }

    fun removePassword() {
        passwordStorage.remove(PASSWORD_KEY)
    }

    private fun shiftAccountOrderList() {
        viewModelScope.launch(Dispatchers.IO) { }
        realm.writeBlocking {
            val accountList = this.query(AccountStorageItem::class).find()
            accountList.forEach { T ->
                val order = T.order
                T.order = order + 1
            }
        }
    }

}
