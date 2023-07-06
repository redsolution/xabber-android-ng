package com.xabber.presentation.application.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.xabber.presentation.AppConstants.CHAT_LIST_UNREAD_KEY
import com.xabber.presentation.application.contract.Navigator
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeDialogFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.account.reorder.ReorderAccountsFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.ChatSettingsFragment
import com.xabber.presentation.application.fragments.chatlist.ArchiveFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.presentation.application.fragments.chatlist.add.NewChatFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewContactFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewGroupFragment
import com.xabber.presentation.application.fragments.chatlist.forward.ChatListToForwardFragment
import com.xabber.presentation.application.fragments.chatlist.spec_notifications.SpecialNotificationsFragment
import com.xabber.presentation.application.fragments.contacts.*
import com.xabber.presentation.application.fragments.contacts.edit.EditContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.settings.*
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.presentation.application.manage.DisplayManager.getMainContainerWidth
import com.xabber.presentation.application.manage.DisplayManager.isDualScreenMode
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import com.xabber.utils.MaskManager
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

    private val handler = Handler(Looper.getMainLooper())
    private var assist: SoftInputAssist? = null
    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)
    private val viewModel = ApplicationViewModel()
    private val chatListViewModel: ChatListViewModel by viewModels()

    private val showBadge = {
        val count = viewModel.unreadMessage.value
        if (count != null) {
            if (count > 0) {
                val creator = binding.bottomNavBar.getOrCreateBadge(R.id.chats)
                creator.backgroundColor =
                    ResourcesCompat.getColor(
                        binding.bottomNavBar.resources,
                        R.color.green_500,
                        null
                    )
                creator.badgeGravity = BadgeDrawable.BOTTOM_END
                creator.number = count
            } else binding.bottomNavBar.removeBadge(R.id.chats)
        } else binding.bottomNavBar.removeBadge(R.id.chats)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ThemeApplication)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        window?.statusBarColor =
            Color.TRANSPARENT  // На некоторых устройствах не срабатывает аттрибут цвета статус бара из темы приложения, поэтому дополнительно программно задаем прозрачный цвет статус-бару
        if (viewModel.checkIsEntry()) {  // Проверяем авторизован ли пользователь
            updateUiDependingOnMode(isDualScreenMode()) // Определяем нужно ли нам разделение экрана
            setFullScreenMode()
            setHeightStatusBar()     // Вычисляем и устанавливаем высоту статус бара, чтобы устанавливать отступ
            setMask()   // Задаем маску из Preferences, по дефолту - круглая маска
            setChatSettings()
            handler.postDelayed(showBadge, 0)
            binding.slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED_CLOSED
            assist =
                SoftInputAssist(window)  // Инициализируем класс, отвечающий за высоту soft keyboard в режиме full screen
            initBottomNavigation()
            subscribeToViewModelData()
            if (savedInstanceState != null) {
                setupIconChat(chatListViewModel.showUnreadOnly.value ?: false)
            } else launchFragment(ChatListFragment())
        } else goToOnboarding()

        binding.slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
    }

    override fun onResume() {
        super.onResume()
        assist?.onResume()
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

    private fun setMask() {
        val mask =
            getSharedPreferences(AppConstants.SHARED_PREF_MASK, Context.MODE_PRIVATE).getInt(
                AppConstants.MASK_KEY,
                R.drawable.ic_mask_circle
            )
        MaskManager.mask = mask
    }

    private fun setChatSettings() {
        val corner =
            getSharedPreferences(AppConstants.SHARED_PREF_CORNER, Context.MODE_PRIVATE).getInt(
                AppConstants.CORNER_KEY,
                7
            )
        val type = getSharedPreferences(AppConstants.SHARED_PREF_TYPE, Context.MODE_PRIVATE).getInt(
            AppConstants.TYPE_TAIL_KEY,
            MessageTailType.SMOOTH.rawValue
        )
        val tailPosition = getSharedPreferences(
            AppConstants.SHARED_PREF_TAIL_POSITION,
            Context.MODE_PRIVATE
        ).getBoolean(AppConstants.TAIL_POSITION, true)

        ChatSettingsManager.defineMessageDrawable(corner, type, tailPosition)

        val designType =
            getSharedPreferences(AppConstants.SHARED_PREF_CHAT_DESIGN, Context.MODE_PRIVATE).getInt(
                AppConstants.CHAT_DESIGN_TYPE,
                1
            )
        ChatSettingsManager.designType = designType

        val gradient = getSharedPreferences(AppConstants.SHARED_PREF_GRADIENT, Context.MODE_PRIVATE).getInt(AppConstants.GRADIENT, 7)

        ChatSettingsManager.designType = designType
        ChatSettingsManager.gradient = gradient

        val gradientDraw = when (gradient) {
            1 -> R.drawable.gradient_bordo
            2 -> R.drawable.gradient_red
            3 -> R.drawable.gradient_orange
            4 -> R.drawable.gradient_yellish_blue
            5 -> R.drawable.gradient_light_green
            6 -> R.drawable.gradient_light_yellish_blue
            7 -> R.drawable.gradient_blue
            8 -> R.drawable.gradient_purple
            else -> {
                R.drawable.gradient_blue
            }
        }
        binding.detailContainer.setBackgroundResource(gradientDraw)
        val designDrawable = when (designType) {
            1 -> R.drawable.aliens_repeat
            2 -> R.drawable.cats_repeat
            3 -> R.drawable.hearts_repeat
            4 -> R.drawable.flowers_repeat
            5 -> R.drawable.meadow_repeat
            6 -> R.drawable.summer_repeat
            else -> {
                R.drawable.aliens_repeat
            }
        }
        binding.fr.setBackgroundResource(designDrawable)
    }

    private fun initBottomNavigation() {
        binding.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> {
                    if (activeFragment !is ChatListFragment) {
                        closeDetail()
                        launchFragment(ChatListFragment())
                    } else {
                        showUnreadChats(!chatListViewModel.showUnreadOnly.value!!)
                    }
                }
                R.id.calls -> {
                    chatListViewModel.setShowUnreadOnly(false)
                    closeDetail()
                    if (activeFragment !is CallsFragment) launchFragment(CallsFragment())
                    setupIconChat(false)
                }
                R.id.contacts -> if (activeFragment !is ContactsFragment) {
                    chatListViewModel.setShowUnreadOnly(false)
                    closeDetail()
                    launchFragment(ContactsFragment())
                    setupIconChat(false)
                }
                R.id.discover -> if (activeFragment !is DiscoverFragment) {
                    chatListViewModel.setShowUnreadOnly(false)
                    closeDetail()
                    launchFragment(DiscoverFragment())
                    setupIconChat(false)
                }
                R.id.settings -> if (activeFragment !is SettingsFragment) {
                    chatListViewModel.setShowUnreadOnly(false)
                    closeDetail()
                    launchFragment(
                        SettingsFragment(
                        )
                    )
                    setupIconChat(false)
                }
            }
            true
        }
    }

    private fun subscribeToViewModelData() {
        viewModel.initAccountListListener()
        viewModel.initUnreadMessagesCountListener()
        viewModel.unreadMessage.observe(this) {
            handler.postDelayed(showBadge, 300)
        }
        viewModel.getUnreadMessages()
    }

    private fun setupIconChat(unreadChats: Boolean) {
        val menuItem = binding.bottomNavBar.menu.findItem(R.id.chats)
        if (unreadChats) {
            menuItem.setIcon(R.drawable.ic_chat_alert)
        } else {
            menuItem.setIcon(R.drawable.ic_chat)
        }
    }

    private fun goToOnboarding() {
        val intent = Intent(applicationContext, OnBoardingActivity::class.java)
        startActivity(intent)
        finish()
    }


    private fun showUnreadChats(showUnread: Boolean) {
        if (activeFragment is ChatListFragment) chatListViewModel.setShowUnreadOnly(showUnread)
        setupIconChat(showUnread)
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
        if (supportFragmentManager.backStackEntryCount > 0)
            supportFragmentManager.popBackStack()
        else {
            closeDetail()
        }
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

    override fun showForwardFragment(forwardMessage: String, jid: String) {
//        if (isTablet()) showDialogFragment(
//            ChatListToForwardFragment.newInstance(forwardMessage), CHAT_LIST_TO_FORWARD_DIALOG_TAG
//        )
    //    else
        launchDetailInStack(ChatListToForwardFragment.newInstance(forwardMessage, jid))
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

    override fun showInterfaceSettings(inStack: Boolean) {
        if (inStack) launchDetailInStack(InterfaceFragment()) else launchDetail(InterfaceFragment())
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

    override fun showChatSettings() {
        launchDetailInStack(ChatSettingsFragment())
    }

    override fun showMaskSettings() {
        launchDetailInStack(MaskFragment())
    }

    override fun setDesignBackground() {
        val gradientDraw = when (ChatSettingsManager.gradient) {
            1 -> R.drawable.gradient_bordo
            2 -> R.drawable.gradient_red
            3 -> R.drawable.gradient_orange
            4 -> R.drawable.gradient_yellish_blue
            5 -> R.drawable.gradient_light_green
            6 -> R.drawable.gradient_light_yellish_blue
            7 -> R.drawable.gradient_blue
            8 -> R.drawable.gradient_purple
            else -> {
                R.drawable.gradient_blue
            }
        }
        binding.detailContainer.setBackgroundResource(gradientDraw)
        val designDrawable = when (ChatSettingsManager.designType) {
            1 -> R.drawable.aliens_repeat
            2 -> R.drawable.cats_repeat
            3 -> R.drawable.hearts_repeat
            4 -> R.drawable.flowers_repeat
            5 -> R.drawable.meadow_repeat
            6 -> R.drawable.summer_repeat
            else -> {
                R.drawable.aliens_repeat
            }
        }
        binding.fr.setBackgroundResource(designDrawable)
    }

    override fun onPause() {
        super.onPause()
        assist?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            CHAT_LIST_UNREAD_KEY,
            viewModel.showUnreadOnly
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        assist?.onDestroy()
    }

}
