package com.xabber.presentation.application.contract

import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.xabber.data.dto.ChatDto
import com.xabber.presentation.onboarding.contract.ResultListener

fun Fragment.navigator(): ApplicationNavigator = requireActivity() as ApplicationNavigator

interface ApplicationNavigator {

    fun goBack()

    fun showMessage(chat: ChatDto)

    fun showAccount()

    fun showNewChat()

    fun showNewContact()

    fun showNewGroup(incognito: Boolean)

    fun showSpecialNotificationSettings()

    fun showEditContact()

    fun <T : Parcelable> showResult(result: T)
    fun <T : Parcelable> giveResult(
        clazz: Class<T>,
        owner: LifecycleOwner,
        listener: ResultListener<T>
    )
}