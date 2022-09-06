package com.xabber.presentation.onboarding.activity

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.model.dto.HostListDto
import com.xabber.repository.AccountRepository
import com.xabber.model.xmpp.account.AccountStorageItem
import com.xabber.model.xmpp.presences.ResourceStorageItem
import com.xabber.presentation.AppConstants
import io.reactivex.rxjava3.core.Single
import io.realm.Realm
import io.realm.RealmConfiguration

class OnboardingViewModel : ViewModel() {
    private val accountRepository = AccountRepository()

    private val _nickName = MutableLiveData<String>()
    val nickName: LiveData<String> = _nickName
    private val _userName = MutableLiveData<String>()
    val username: LiveData<String> = _userName
    private val _password = MutableLiveData<String>()
    val password: LiveData<String> = _userName

    private val _avatarBitmap = MutableLiveData<Bitmap>()
    val avatarBitmap: LiveData<Bitmap> = _avatarBitmap

    private val _avatarUri = MutableLiveData<Uri>()
    val avatarUri: LiveData<Uri> = _avatarUri

    fun setNickName(nickName: String) {
        _nickName.value = nickName
    }

    fun setUserName(userName: String) {
        _userName.value = userName
    }

    fun setPassword(password: String) {
        _password.value = password
    }

    fun getHost(): Single<HostListDto> = accountRepository.getHostList()

    fun checkIfNameAvailable(username: String, host: String): Single<Any> =
        accountRepository.checkIfNameAvailable(
            mapOf(
                "username" to username,
                "host" to host,
                "no_captcha_key" to AppConstants.NO_CAPTCHA_KEY,
            )
        )


    suspend fun registerAccount(
        username: String
    ) {
        val config =
            RealmConfiguration.Builder(setOf(AccountStorageItem::class, ResourceStorageItem::class))
                .build()
        val realm = Realm.open(config)
        realm.write {
            this.copyToRealm(AccountStorageItem().apply {
                jid = username

            })
        }

        realm.close()
    }


    fun setAvatarBitmap(bitmap: Bitmap) {
        _avatarBitmap.value = bitmap
    }

    fun setAvatarUri(uri: Uri) {
        _avatarUri.value = uri
    }
}
