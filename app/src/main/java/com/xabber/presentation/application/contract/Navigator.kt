package com.xabber.presentation.application.contract

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.contacts.vcard.ContactAccountParams

fun Fragment.navigator(): Navigator = requireActivity() as Navigator

interface Navigator {

    fun goBack()

    fun closeDetail()

    fun showDialogFragment(dialog: DialogFragment, tag: String)

    fun showBottomSheetDialog(dialog: BottomSheetDialogFragment)

    fun showChatFragment()

    fun showChat(chatParams: ChatParams)

    fun showArchive()

    fun showNewChat()

    fun showNewContact()

    fun showNewGroup(incognito: Boolean)

    fun showSpecialNotificationSettings()

    fun showContacts()

    fun showEditContact(params: ContactAccountParams)

    fun showEditContactFromContacts(params: ContactAccountParams)

    fun showAccount(jid: String)

    fun showReorderAccountsFragment()

    fun showSettings()

    fun showContactAccount(params: ContactAccountParams)

    fun showQRCode(qrCodeParams: QRCodeParams)

    fun showContactProfile(params: ContactAccountParams)

    fun showProfileSettings()

    fun showCloudStorageSettings()

    fun showEncryptionAndKeysSettings()

    fun showDevicesSettings()

    fun showForwardFragment(forwardMessage: String)

    fun showStatusFragment()

    fun showChatInStack(chatParams: ChatParams)

    fun showInterfaceSettings()

    fun showNotificationsSettings()

    fun showDataAndStorageSettings()

    fun showPrivacySettings()

    fun showConnectionSettings()

    fun showDebugSettings()

    fun showAddAccountFragment()

    fun lockScreen(lock: Boolean)

}
