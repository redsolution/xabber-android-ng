package com.xabber.presentation.application.activity

import SoftInputAssist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.model.dto.ContactDto
import com.xabber.model.xmpp.account.Account
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.notification.PushService
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.DisplayManager.getMainContainerWidth
import com.xabber.presentation.application.activity.DisplayManager.isDualScreenMode
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeDialogFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.account.reorder.ReorderAccountsFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chat.ChatViewModel
import com.xabber.presentation.application.fragments.chatlist.ChatListFragment
import com.xabber.presentation.application.fragments.chatlist.ChatSettingsFragment
import com.xabber.presentation.application.fragments.chatlist.SpecialNotificationsFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewChatFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewGroupFragment
import com.xabber.presentation.application.fragments.contacts.*
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.settings.*
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import com.xabber.utils.lockScreenRotation
import com.xabber.utils.mask.Mask


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
    private lateinit var pushBroadcastReceiver: BroadcastReceiver



    override fun onCreate(savedInstanceState: Bundle?) {


        setTheme(R.style.ThemeApplication)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val value = intent.getExtras()?.get("redirect").toString();
        if (value == "homefragment") {
            launchDetail(
                ChatFragment.newInstance(
                    ChatParams(
                        ChatListDto(
                            "1",
                            "Иван Сергеев",
                            "Иван Сергеев",
                            "Иван Сергеев",
                            "Я подумаю, но не обещаю пока ничего",
                            System.currentTimeMillis(),
                            MessageSendingState.Sended,
                            false,
                            true,
                            null,
                            false,
                            false,
                            false,
                            0,
                            0,
                            ResourceStatus.Chat,
                            RosterItemEntity.Contact,
                            "",
                            0,
                            R.color.green_500,
                            R.drawable.rayan,
                            ContactDto(
                                "1",
                                "Иван Сергеев",
                                "Иван Сергеев",
                                "Иван",
                                "Сергеев",
                                "ivan@xabber.com",
                                R.color.green_500,
                                R.drawable.rayan,
                                null, null, ResourceStatus.Online, RosterItemEntity.Contact
                            )
                        )
                    )
                )
            )
        }
        if (true) {
            updateUiDependingOnMode(isDualScreenMode())
            setFullScreenMode()
            setHeightStatusBar()
            assist = SoftInputAssist(this)
            subscribeViewModelData()
            initBottomNavigation()
            determinateMask()
            determinateAccountList()

            pushBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, intent: Intent?) {
                    val extras = intent?.extras
                    Log.d("keyintent", "messageReceive")
                    extras?.keySet()?.firstOrNull {
                        it == PushService.KEY_ACTION
                    }?.let { key ->
                        when (extras.getString(key)) {
                            PushService.ACTION_SHOW_MESSAGE -> {
                                extras.getString(PushService.KEY_MESSAGE)?.let { message ->
                                    Log.d("keyintent", "messageReceive")
                                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                            else -> {
                                Log.d("keyintent", "no key")
                            }
                        }
                    }

                }
            }
            val intentFilter = IntentFilter()
            intentFilter.addAction(PushService.INTENT_FILTER)
            registerReceiver(pushBroadcastReceiver, intentFilter)
            Log.d("keyIntent", "broadcast = $pushBroadcastReceiver")
            binding.slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED_CLOSED
            binding.slidingPaneLayout.clearAnimation()
            if (savedInstanceState == null) {


                launchFragment(ChatListFragment())
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelable("key", QRCodeParams::class.java)
        } else savedInstanceState.getParcelable("key")
                val unreadMessagesCount =
                    savedInstanceState.getInt(AppConstants.UNREAD_MESSAGES_COUNT)
                showBadge(unreadMessagesCount)
            }
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
        overridePendingTransition(R.anim.appearance, R.anim.disappearance)
    }

    private fun setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

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
                        launchFragment(ChatListFragment())
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
                R.id.settings -> if (activeFragment !is SettingsFragment) {
                    launchFragment(
                        SettingsFragment(
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
        launchDetailInStack(ChatListFragment())
    }

    override fun showChat(chatParams: ChatParams) {
        launchDetail(ChatFragment.newInstance(chatParams))
    }

    override fun showAccount() {
        val account = Account("Наталья Баранщикова", "Наталья Барабанщикова", "barabanshikova@mail.com", R.color.blue_500, R.drawable.img, 1)
        launchDetail(AccountFragment.newInstance(account))
    }

    override fun showContacts() {
        launchFragment(ContactsFragment())
        val view = binding.bottomNavBar.findViewById<BottomNavigationItemView>(R.id.contacts)
        view.performClick()
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

    override fun showEditContactFromContacts(contactDto: ContactDto?) {
        launchDetail(EditContactFragment.newInstance(contactDto))
    }

    override fun showChatSettings() {
        launchDetail(ChatSettingsFragment())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("key", QRCodeParams("k", "p", R.color.green_500))
        viewModel.unreadCount.value?.let { outState.putInt(AppConstants.UNREAD_MESSAGES_COUNT, it) }
    }

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
            launchDetailInStack(QRCodeFragment.newInstance(qrCodeParams))
        }
    }

    override fun showMyQRCode(qrCodeParams: QRCodeParams) {
        if (isTablet()) {
            showDialogFragment(QRCodeDialogFragment.newInstance(qrCodeParams))
        } else {
            launchDetail(QRCodeFragment.newInstance(qrCodeParams))
        }
    }

    override fun showContactProfile(contactDto: ContactDto) {
        launchDetailInStack(ContactProfileFragment.newInstance(contactDto))
    }

    override fun showProfileSettings() {
        launchDetail(ProfileSettingsFragment())
    }

    override fun showCloudStorageSettings() {
        launchDetail(CloudStorageSettingsFragment())
    }

    override fun showEncryptionAndKeysSettings() {
        launchDetail(EncryptionSettingsFragment())
    }

    override fun showDevicesSettings() {
        launchDetail(DevicesSettingsFragment())
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
//        unregisterReceiver(pushBroadcastReceiver)
        assist?.onDestroy()
    }



}
