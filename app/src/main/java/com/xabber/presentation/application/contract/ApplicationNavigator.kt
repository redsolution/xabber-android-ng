package com.xabber.presentation.application.contract

import android.graphics.Bitmap
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.data.xmpp.account.Account

fun Fragment.navigator(): ApplicationNavigator = requireActivity() as ApplicationNavigator

interface ApplicationNavigator {

    fun goBack()

    fun showChatFragment()

    fun showMessage(jid: String)

    fun showAccount(account: Account)

    fun showContacts()

    fun showNewChat()

    fun showNewContact()

    fun showNewGroup(incognito: Boolean)

    fun showSpecialNotificationSettings()

    fun showEditContact(name: String)

    fun showChatSettings()

    fun closeDetail()

    fun showReorderAccountsFragment()

    fun showBottomSheetDialog(dialog: BottomSheetDialogFragment)

    fun showDialogFragment(dialog: DialogFragment)

    fun slidingPaneLayoutIsOpen(): Boolean

    fun requestPermissionToRecord(): Boolean

    fun lockScreenRotation(isLock: Boolean)



    //fun openCamera(): Bitmap?
}