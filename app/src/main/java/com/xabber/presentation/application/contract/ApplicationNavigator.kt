package com.xabber.presentation.application.contract

import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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

    fun showBottomSheetDialog(dialog: BottomSheetDialogFragment)

}