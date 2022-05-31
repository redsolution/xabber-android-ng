package com.xabber.presentation.application.contract

import androidx.fragment.app.Fragment

fun Fragment.navigator(): ApplicationNavigator = requireActivity() as ApplicationNavigator

interface ApplicationNavigator {

    fun goBack()

    fun showMessage(jid: String)

    fun showAccount()

    fun showContacts()

    fun showNewChat()

    fun showNewContact()

    fun showNewGroup(incognito: Boolean)

    fun showSpecialNotificationSettings()

    fun showEditContact(name: String)

    fun showChatSettings()

    fun closeDetail()

}