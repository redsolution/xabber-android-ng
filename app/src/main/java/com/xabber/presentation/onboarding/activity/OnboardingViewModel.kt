package com.xabber.presentation.onboarding.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.XabberAccountDto
import com.xabber.data.repository.AccountRepository
import com.xabber.data.util.AppConstants
import com.xabber.xmpp.account.AccountStorageItem
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.annotations.PrimaryKey
import io.realm.query

class OnboardingViewModel : ViewModel() {
    val accountRepository = AccountRepository()

    private val _nickName = MutableLiveData<String>()
    val nickName: LiveData<String> = _nickName
    private val _userName = MutableLiveData<String>()
    val username: LiveData<String> = _userName
    private val _password = MutableLiveData<String>()
    val password: LiveData<String> = _userName


    fun setNickName(nickName: String) {
        _nickName.value = nickName
    }

    fun stUserName(userName: String) {
        _userName.value = userName
    }

    fun setPassword(password: String) {
        _password.value = password
    }


    fun checkIfNameAvailable(username: String, host: String): Single<Any> =
        accountRepository.checkIfNameAvailable(
            mapOf(
                "username" to username,
                "host" to host,
                "no_captcha_key" to AppConstants.NO_CAPTCHA_KEY,
            )
        )

    fun registerAccount(
        username: String,
        host: String,
        password: String
    ): Single<XabberAccountDto> =
        accountRepository.registerAccount(
            mapOf(
                "username" to username,
                "host" to host,
                "password" to password,
                "no_captcha_key" to AppConstants.NO_CAPTCHA_KEY
            )
        )





}


