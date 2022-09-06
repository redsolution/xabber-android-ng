package com.xabber.presentation.application.contract

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.model.dto.ContactDto
import com.xabber.model.xmpp.account.Account
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
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

    fun showMyQRCode(qrCodeParams: QRCodeParams)

    fun showContactProfile(contactDto: ContactDto)

    fun showProfileSettings()

    fun showCloudStorageSettings()

    fun showEncryptionAndKeysSettings()

    fun showDevicesSettings()

    fun lockScreen(lock: Boolean)

}
