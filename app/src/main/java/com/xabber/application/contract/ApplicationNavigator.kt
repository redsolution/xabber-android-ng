package com.xabber.application.contract

import androidx.fragment.app.Fragment

fun Fragment.navigator(): ApplicationNavigator = requireActivity() as ApplicationNavigator

interface ApplicationNavigator {

    fun goBack()

    fun goToChat()

    fun goToCalls()

    fun goToContacts()

    fun goToDiscover()

    fun goToSettings()

    fun goToMessage()


}