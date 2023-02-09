package com.xabber.presentation.application.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.CHAT_LIST_TO_FORWARD_DIALOG_TAG
import com.xabber.presentation.application.activity.DisplayManager.getMainContainerWidth
import com.xabber.presentation.application.activity.DisplayManager.isDualScreenMode
import com.xabber.presentation.application.contract.Navigator
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeDialogFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.account.reorder.ReorderAccountsFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chatlist.ArchiveFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.presentation.application.fragments.chatlist.add.NewChatFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewContactFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewGroupFragment
import com.xabber.presentation.application.fragments.chatlist.forward.ChatListToForwardFragment
import com.xabber.presentation.application.fragments.chatlist.spec_notifications.SpecialNotificationsFragment
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.fragments.contacts.StatusFragment
import com.xabber.presentation.application.fragments.contacts.edit.EditContactFragment
import com.xabber.presentation.application.fragments.contacts.vcard.ContactAccountFragment
import com.xabber.presentation.application.fragments.contacts.vcard.ContactAccountParams
import com.xabber.presentation.application.fragments.contacts.vcard.ContactProfileFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.settings.*
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import com.xabber.utils.lockScreenRotation

/**
 * ApplicationActivity implements the interface Navigator. Its methods are responsible for navigation.
 * This activity splits the screen into two if device is tablet.
 * The application works in full screen mode. This activity set height status bar in DisplayManager, so that fragments can
 * set indent. SoftInputAssist responsible for the correct height of the soft keyboard in full screen mode.
 * In onCreate check condition: user is authorized (stay this activity) or not (go to Onboarding activity)
 */

class ApplicationActivity : AppCompatActivity(), Navigator {

    private val binding: ActivityApplicationBinding by lazy {
        ActivityApplicationBinding.inflate(
            layoutInflater
        )
    }

    private var assist: SoftInputAssist? = null
    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)
    private val viewModel: ChatListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ThemeApplication)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        window?.statusBarColor = Color.TRANSPARENT
        if (viewModel.checkIsEntry()) {
            updateUiDependingOnMode(isDualScreenMode())
            setFullScreenMode()
            setHeightStatusBar()
            binding.slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED_CLOSED
            assist = SoftInputAssist(this)
            initBottomNavigation()
            subscribeToViewModelData()
            if (savedInstanceState == null)
                launchFragment(ChatListFragment())
        } else goToOnboarding()
    }

    override fun onResume() {
        super.onResume()
        assist?.onResume()
    }

    private fun goToOnboarding() {
        val intent = Intent(applicationContext, OnBoardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun setHeightStatusBar() {
        val height = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = resources.getDimensionPixelSize(height)
        setDelimiters(statusBarHeight)
        DisplayManager.setHeightStatusBar(statusBarHeight)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            insets.consumeSystemWindowInsets()
        }
    }

    private fun setDelimiters(prolongation: Int) {
        var actionBarHeight = 0
        val typedValue = TypedValue()
        if (this.theme.resolveAttribute(
                android.R.attr.actionBarSize,
                typedValue,
                true
            )
        ) actionBarHeight =
            TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
        binding.delimiterToolbar.updateLayoutParams<ConstraintLayout.LayoutParams> {
            this.height = actionBarHeight + prolongation
        }
    }

    private fun updateUiDependingOnMode(isDualScreenMode: Boolean) {
        if (isDualScreenMode) {
            setContainerWidth()
        }
    }

    private fun setContainerWidth() {
        binding.mainContainer.updateLayoutParams<SlidingPaneLayout.LayoutParams> {
            this.width = getMainContainerWidth()
        }
    }

    private fun showBadge(count: Int) {
        if (count > 0) {
            val creator = binding.bottomNavBar.getOrCreateBadge(R.id.chats)
            creator.backgroundColor =
                ResourcesCompat.getColor(binding.bottomNavBar.resources, R.color.green_500, null)
            creator.badgeGravity = BadgeDrawable.BOTTOM_END
            creator.number = count
        } else binding.bottomNavBar.removeBadge(R.id.chats)
    }

    private fun initBottomNavigation() {
        binding.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> {
                    if (activeFragment !is ChatListFragment) {
                        closeDetail()
                        launchFragment(ChatListFragment())
                    } else {
                        showUnreadChats(!viewModel.showUnreadOnly.value!!)
                    }
                }
                R.id.calls -> {
                    closeDetail()
                    if (activeFragment !is CallsFragment) launchFragment(CallsFragment())
                    showUnreadChats(false)
                }
                R.id.contacts -> if (activeFragment !is ContactsFragment) {
                    closeDetail()
                    launchFragment(ContactsFragment())
                    showUnreadChats(false)
                }
                R.id.discover -> if (activeFragment !is DiscoverFragment) {
                    closeDetail()
                    launchFragment(DiscoverFragment())
                    showUnreadChats(false)
                }
                R.id.settings -> if (activeFragment !is SettingsFragment) {
                    closeDetail()
                    launchFragment(
                        SettingsFragment(
                        )
                    )
                    showUnreadChats(false)
                }
            }
            true
        }
    }

    private fun showUnreadChats(showUnread: Boolean) {
        val menuItem = binding.bottomNavBar.menu.findItem(R.id.chats)
        if (showUnread) {
            menuItem.setIcon(R.drawable.ic_chat_alert)
            viewModel.setShowUnreadOnly(true)
        } else {
            menuItem.setIcon(R.drawable.ic_chat)
            viewModel.setShowUnreadOnly(false)
        }
    }

    private fun subscribeToViewModelData() {
        viewModel.initAccountListListener()
        viewModel.initUnreadMessagesCountListener()
        viewModel.unreadMessage.observe(this) {
            Log.d("ccc"," для активити $it")
            showBadge(it)
        }
        viewModel.getUnreadMessages()
    }

    private fun launchFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.application_container, fragment)
        }
    }

    private fun launchDetail(fragment: Fragment) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.detail_container, fragment)
        }
        binding.slidingPaneLayout.openPane()
    }

    private fun launchDetailInStack(fragment: Fragment) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.detail_container, fragment).addToBackStack(null)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun goBack() {
        if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack() else closeDetail()
    }

    override fun closeDetail() {
        if (supportFragmentManager.findFragmentById(R.id.detail_container) != null) {
            supportFragmentManager.beginTransaction()
                .remove(supportFragmentManager.findFragmentById(R.id.detail_container)!!)
                .commit()
            if (binding.slidingPaneLayout.isOpen) binding.slidingPaneLayout.close()
        } else {
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
        }
    }

    override fun showDialogFragment(dialog: DialogFragment, tag: String) {
        dialog.show(supportFragmentManager, tag)
    }

    override fun showBottomSheetDialog(dialog: BottomSheetDialogFragment) {
        dialog.show(supportFragmentManager, dialog.tag)
    }

    override fun showArchive() {
        supportFragmentManager.commit {
            replace(R.id.application_container, ArchiveFragment()).addToBackStack(null)
        }
    }

    override fun showChat(chatParams: ChatParams) {
        launchDetail(ChatFragment.newInstance(chatParams))
    }

    override fun showContacts() {
        launchFragment(ContactsFragment())
        val view = binding.bottomNavBar.findViewById<BottomNavigationItemView>(R.id.contacts)
        view.performClick()
    }

    override fun showReorderAccountsFragment() {
        launchDetail(ReorderAccountsFragment())
    }

    override fun showNewChat() {
        launchDetail(NewChatFragment())
    }

    override fun showNewContact() {
        launchDetailInStack(NewContactFragment.newInstance())
    }

    override fun showNewGroup(incognito: Boolean) {
        launchDetailInStack(NewGroupFragment.newInstance(incognito))
    }

    override fun showChatFragment() {
        launchDetailInStack(ChatListFragment())
    }

    override fun showSpecialNotificationSettings() {
        launchDetail(SpecialNotificationsFragment())
    }

    override fun showAccount(jid: String) {
        launchDetail(AccountFragment.newInstance(jid))
    }

    override fun showEditContact(params: ContactAccountParams) {
        launchDetailInStack(EditContactFragment.newInstance(params))
    }

    override fun showEditContactFromContacts(params: ContactAccountParams) {
        launchDetail(EditContactFragment.newInstance(params))
    }

    override fun showSettings() {
        launchDetail(SettingsFragment())
    }

    override fun showContactAccount(params: ContactAccountParams) {
        launchDetail(ContactAccountFragment.newInstance(params))
    }

    override fun showQRCode(qrCodeParams: QRCodeParams) {
        if (isTablet())
            showDialogFragment(
                QRCodeDialogFragment.newInstance(qrCodeParams),
                AppConstants.QR_CODE_DIALOG_TAG
            )
        else
            launchDetailInStack(QRCodeFragment.newInstance(qrCodeParams))
    }

    private fun isTablet(): Boolean = resources.getBoolean(R.bool.isTablet)

    override fun showContactProfile(params: ContactAccountParams) {
        launchDetailInStack(ContactProfileFragment.newInstance(params))
    }

    override fun showProfileSettings() {
        launchDetailInStack(ProfileSettingsFragment())
    }

    override fun showCloudStorageSettings() {
        launchDetailInStack(CloudStorageSettingsFragment())
    }

    override fun showEncryptionAndKeysSettings() {
        launchDetailInStack(EncryptionSettingsFragment())
    }

    override fun showDevicesSettings() {
        launchDetailInStack(DevicesSettingsFragment())
    }

    override fun showForwardFragment(forwardMessage: String) {
        if (isTablet()) showDialogFragment(
            ChatListToForwardFragment.newInstance(forwardMessage), CHAT_LIST_TO_FORWARD_DIALOG_TAG
        )
        else launchDetailInStack(ChatListToForwardFragment.newInstance(forwardMessage))
    }

    override fun showStatusFragment() {
        launchDetailInStack(StatusFragment())
    }

    override fun showChatInStack(chatParams: ChatParams) {
        launchDetailInStack(ChatFragment.newInstance(chatParams))
    }

    override fun showConnectionSettings() {
    }

    override fun showDataAndStorageSettings() {
    }

    override fun showDebugSettings() {
    }

    override fun showInterfaceSettings() {

    }

    override fun showNotificationsSettings() {

    }

    override fun showPrivacySettings() {

    }

    override fun showAddAccountFragment() {
        launchDetail(AddAccountFragment())
    }

    override fun lockScreen(lock: Boolean) {
        lockScreenRotation(lock)
    }

    override fun onPause() {
        super.onPause()
        assist?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        assist?.onDestroy()
    }

}
