package com.xabber.presentation.onboarding.activity

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.HostListDto
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.onboarding.util.PasswordStorageHelper
import com.xabber.remote.AccountRepository
import io.reactivex.rxjava3.core.Single
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OnboardingViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())
    private val passwordStorage = PasswordStorageHelper(XabberApplication.applicationContext())
    private val accountRepository = AccountRepository()
    private val defaultColor = XabberApplication.applicationContext().resources.getString(R.string.blue)
    private val primaryAccountOrder = 0

    private var accountNickName: String? = null

    private var accountJid: String? = null

    private var password: String? = null

    private var savedUri: String? = null

    private val _avatarBitmap = MutableLiveData<Bitmap>()
    val avatarBitmap: LiveData<Bitmap> = _avatarBitmap

    fun setAvatarBitmap(bitmap: Bitmap) {
        _avatarBitmap.value = bitmap
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

    fun checkIsNameAvailable(username: String, host: String): Boolean =
        true  // здесь пока заглушка, в дальнейшем заменить реальной проверкой имени на сервере

    fun registerAccount() {
        val deviceName = android.os.Build.MODEL
        if (accountNickName != null && accountJid != null && password != null) {
            savePassword(accountJid!!, password!!)
            viewModelScope.launch(Dispatchers.IO) {
                realm.write {
                    val accountResource = this.copyToRealm(ResourceStorageItem().apply {
                        primary = accountJid + accountNickName + deviceName
                        jid = accountJid!!
                        owner = accountNickName!!
                        resource = deviceName
                    })
                    this.copyToRealm(com.xabber.data_base.models.account.AccountStorageItem().apply {
                        primary = accountJid!!
                        order = primaryAccountOrder
                        jid = accountJid!!
                        username = accountNickName!!
                        enabled = true
                        colorKey = defaultColor
                        hasAvatar = savedUri != null
                        resource = accountResource
                    })
                    if (savedUri != null)
                        this.copyToRealm(com.xabber.data_base.models.avatar.AvatarStorageItem().apply {
                            primary = accountJid!!
                            jid = accountJid!!
                            owner = accountJid!!
                            fileUri = savedUri!!
                        })
                }
            }
        }
    }

    private fun savePassword(accountJid: String, password: String) {
        passwordStorage.setData(accountJid, password.toByteArray())
    }

}
