package com.xabber.presentation.application.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.data.dto.ContactDto
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.application.activity.DisplayManager.getMainContainerWidth
import com.xabber.presentation.application.activity.DisplayManager.isDualScreenMode
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.fragments.account.*
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chat.ChatViewModel
import com.xabber.presentation.application.fragments.chatlist.*
import com.xabber.presentation.application.fragments.contacts.ContactAccountFragment
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.fragments.contacts.EditContactFragment
import com.xabber.presentation.application.fragments.contacts.NewContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.settings.SettingsFragment
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.lockScreenRotation
import com.xabber.presentation.onboarding.activity.OnBoardingActivity

class ApplicationActivity : AppCompatActivity(), ApplicationNavigator {

    private val binding: ActivityApplicationBinding by lazy {
        ActivityApplicationBinding.inflate(
            layoutInflater
        )
    }
    private var assist: SoftInputAssist? = null
    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)
    private val viewModel: ApplicationViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (true) {
            updateUiDependingOnMode(isDualScreenMode())
            if (savedInstanceState == null) {
                launchFragment(ChatListFragment.newInstance(""))
            } else {
                val unreadMessagesCount =
                    savedInstanceState.getInt(AppConstants.UNREAD_MESSAGES_COUNT)
                showBadge(unreadMessagesCount)
            }
            setFullScreenMode()
            setHeightStatusBar()
            assist = SoftInputAssist(this)
            subscribeViewModelData()
            initBottomNavigation()
            determinateMask()
            determinateAccountList()
        } else goToOnboarding()
    }

    override fun onResume() {
        super.onResume()
        assist?.onResume()
    }

    private fun goToOnboarding() {
        val intent = Intent(this, OnBoardingActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.appearance, R.anim.disappearance)
    }

    private fun setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun setHeightStatusBar() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            DisplayManager.setHeightStatusBar(insets.systemWindowInsetTop)
            setDelimiters(insets.systemWindowInsetTop)
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

    private fun determinateMask() {
        val maskName = getSharedPreferences(
            AppConstants.MASK_KEY,
            Context.MODE_PRIVATE
        ).getString(AppConstants.MASK_KEY, "Circle")
        val mask = when (maskName) {
            "Circle" -> Mask.Circle
            "Hexagon" -> Mask.Hexagon
            "Pentagon" -> Mask.Pentagon
            "Squircle" -> Mask.Squircle
            "Octagon" -> Mask.Octagon
            "Rounded" -> Mask.Rounded
            "Star" -> Mask.Star
            else -> Mask.Circle
        }
        UiChanger.setMask(mask)
    }

    private fun determinateAccountList() {
        //    val accountList = getSharedPreferences(AppConstants.SHARED_PREF_ACCOUNT_LIST_KEY)
    }

    private fun subscribeViewModelData() {
        viewModel.unreadCount.observe(this) {
            showBadge(it)
        }
    }

    private fun showBadge(count: Int) {
//        if (count > 0) {
//            val creator = binding.bottomNavBar.getOrCreateBadge(R.id.chats)
//            creator.backgroundColor =
//                ResourcesCompat.getColor(binding.bottomNavBar.resources, R.color.green_500, null)
//            creator.badgeGravity = BadgeDrawable.BOTTOM_END
//            creator.number = count
//        } else binding.bottomNavBar.removeBadge(R.id.chats)
    }

    private fun initBottomNavigation() {
        binding.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> {
                    if (activeFragment !is ChatListFragment) {
                        launchFragment(ChatListFragment.newInstance("name.surname@redsolution.com"))
                        saveAndClearDetailStack()
                    } else {
                        //  if (viewModel.unreadCount.value != null && viewModel.unreadCount.value != 0) {
                        menuItem.setIcon(R.drawable.ic_chat_alert)
                        //viewModel.chatListType.set = ChatListType.UNREAD
                        //  }
                    }
                }
                R.id.calls -> {
                    if (activeFragment !is CallsFragment) launchFragment(CallsFragment())
                    saveAndClearDetailStack()
                }
                R.id.contacts -> if (activeFragment !is ContactsFragment) {
                    launchFragment(ContactsFragment())
                    saveAndClearDetailStack()
                }
                R.id.discover -> if (activeFragment !is DiscoverFragment) {
                    launchFragment(DiscoverFragment())
                    saveAndClearDetailStack()
                }
                R.id.settings -> if (activeFragment !is AccountFragment) {
                    launchFragment(
                        AccountFragment.newInstance(
                            Account(
                                "Natalia Barabanshikova",
                                "Natalia Barabanshikova",
                                "natalia.barabanshikova@redsolution.com",
                                R.color.blue_500,
                                R.drawable.img, 1
                            )
                        )
                    )
                    saveAndClearDetailStack()
                }
            }
            true
        }
    }

    private fun saveAndClearDetailStack() {
        if (supportFragmentManager.findFragmentById(R.id.detail_container) != null) supportFragmentManager.beginTransaction()
            .remove(supportFragmentManager.findFragmentById(R.id.detail_container)!!)
            .commit()
    }

    private fun launchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.application_container, fragment).commit()
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
            replace(R.id.detail_container, fragment).addToBackStack(null).setCustomAnimations(
                R.animator.in_right,
                R.animator.out_left,
                R.animator.in_left,
                R.animator.out_right
            )
        }
        binding.slidingPaneLayout.openPane()
    }

    override fun showBottomSheetDialog(dialog: BottomSheetDialogFragment) {
        dialog.show(supportFragmentManager, dialog.tag)
    }

    override fun showDialogFragment(dialog: DialogFragment) {
        dialog.show(supportFragmentManager, AppConstants.DIALOG_TAG)
    }

    override fun closeDetail() {
        if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
        else {
            supportFragmentManager.beginTransaction()
                .remove(supportFragmentManager.findFragmentById(R.id.detail_container)!!).commit()
            if (binding.slidingPaneLayout.isOpen) binding.slidingPaneLayout.close()
        }
    }

    override fun showReorderAccountsFragment() {
        launchDetail(ReorderAccountsFragment())
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun showChatFragment() {
        if (slidingPaneLayoutIsOpen()) launchDetailInStack(ChatListFragment())
    }

    override fun showChat(chatParams: ChatParams) {
        launchDetail(ChatFragment.newInstance(chatParams))
    }

    override fun showAccount(account: Account) {
        launchDetail(AccountFragment.newInstance(account))
    }

    override fun showContacts() {
        launchFragment(ContactsFragment())
    }

    override fun showNewChat() {
        launchDetail(NewChatFragment())
    }

    override fun showNewContact() {
        launchDetailInStack(NewContactFragment())
    }

    override fun showNewGroup(incognito: Boolean) {
        launchDetailInStack(NewGroupFragment.newInstance(incognito))
    }

    override fun showSpecialNotificationSettings() {
        launchDetail(SpecialNotificationsFragment())
    }

    override fun showEditContact(contactDto: ContactDto?) {
        launchDetailInStack(EditContactFragment.newInstance(contactDto))
    }

    override fun showChatSettings() {
        launchDetail(ChatSettingsFragment())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.unreadCount.value?.let { outState.putInt(AppConstants.UNREAD_MESSAGES_COUNT, it) }
    }


    fun slidingPaneLayoutIsOpen(): Boolean = binding.slidingPaneLayout.isOpen


    override fun enableScreenRotationLock(isLock: Boolean) {
        lockScreenRotation(isLock)
    }

    override fun showSettings() {
        launchDetail(SettingsFragment())
    }

    override fun showContactAccount(contactDto: ContactDto) {
        launchDetail(ContactAccountFragment.newInstance(contactDto))
    }

    private fun isTablet(): Boolean = resources.getBoolean(R.bool.isTablet)

    override fun showQRCode(qrCodeParams: QRCodeParams) {
        if (isTablet()) {
            showDialogFragment(QRCodeDialogFragment.newInstance(qrCodeParams))
        } else {
            launchDetail(QRCodeFragment.newInstance(qrCodeParams))
        }
    }

    override fun showContactProfile(contactDto: ContactDto) {
        launchDetailInStack(ContactProfileFragment.newInstance(contactDto))
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
