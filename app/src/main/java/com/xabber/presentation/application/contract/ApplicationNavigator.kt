package com.xabber.presentation.application.contract

import androidx.fragment.app.Fragment
import com.xabber.data.dto.ChatDto

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

    fun showChatSettings()


}