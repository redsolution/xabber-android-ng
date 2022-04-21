package com.xabber.presentation.application.contract

import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.xabber.data.dto.ChatDto
import com.xabber.presentation.onboarding.contract.ResultListener

fun Fragment.navigator(): ApplicationNavigator = requireActivity() as ApplicationNavigator

interface ApplicationNavigator {

    fun goBack()

    fun goToMessage(chat: ChatDto)

    fun goToAccount()

    fun goToNewMessage()

    fun startNewContactFragment()

    fun startNewGroupFragment(incognito: Boolean)

    fun startSpecialNotificationsFragment()

    fun startEditContactFragment()

    fun startAccountFragment()

    fun <T : Parcelable> showResult(result: T)
    fun <T : Parcelable> giveResult(
        clazz: Class<T>,
        owner: LifecycleOwner,
        listener: ResultListener<T>
    )
}