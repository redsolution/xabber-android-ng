package com.xabber.presentation.application.contract

import android.graphics.Bitmap
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

fun Fragment.navigator(): ApplicationNavigator = requireActivity() as ApplicationNavigator

interface ApplicationNavigator {

    fun goBack()

    fun showChatFragment()

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

    fun showDialogFragment(dialog: DialogFragment)

    fun slidingPaneLayoutIsOpen(): Boolean



    //fun openCamera(): Bitmap?
}