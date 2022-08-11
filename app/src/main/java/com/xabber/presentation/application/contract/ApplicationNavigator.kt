package com.xabber.presentation.application.contract

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.data.dto.ContactDto
import com.xabber.data.xmpp.account.Account
import com.xabber.presentation.application.fragments.account.QRCodeParams
import com.xabber.presentation.application.fragments.chat.ChatParams

fun Fragment.navigator(): ApplicationNavigator = requireActivity() as ApplicationNavigator

interface ApplicationNavigator {

    fun goBack()

    fun showChatFragment()

    fun showChat(chatParams: ChatParams)

    fun showAccount(account: Account)

    fun showContacts()

    fun showNewChat()

    fun showNewContact()

    fun showNewGroup(incognito: Boolean)

    fun showSpecialNotificationSettings()

    fun showEditContact(contactDto: ContactDto?)

    fun showChatSettings()

    fun closeDetail()

    fun showReorderAccountsFragment()

    fun showBottomSheetDialog(dialog: BottomSheetDialogFragment)

    fun showDialogFragment(dialog: DialogFragment)

    fun enableScreenRotationLock(isLock: Boolean)

    fun showSettings()

    fun showContactAccount(contactDto: ContactDto)

    fun showQRCode(qrCodeParams: QRCodeParams)

    fun showContactProfile(contactDto: ContactDto)

}
