package com.xabber.presentation.application.activity


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.navigation.NavigationView
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.dto.AccountDto
import com.xabber.dto.AvatarDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.CHAT_LIST_UNREAD_KEY
import com.xabber.presentation.application.contract.Navigator
import com.xabber.presentation.application.dialogs.AccountDialog
import com.xabber.presentation.application.dialogs.NotificationsFragmentFull
import com.xabber.presentation.application.fragments.account.AccountAdapter
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeDialogFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.account.reorder.ReorderAccountsFragment
import com.xabber.presentation.application.fragments.calls.CallsDialog
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chat.ChatSettingsFragment
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chatlist.ChatListFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.presentation.application.fragments.chatlist.add.NewChatFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewContactFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewGroupFragment
import com.xabber.presentation.application.fragments.chatlist.archive.ArchiveDialog
import com.xabber.presentation.application.fragments.chatlist.archive.ArchiveFragment
import com.xabber.presentation.application.fragments.chatlist.forward.ChatListToForwardFragment
import com.xabber.presentation.application.fragments.contacts.*
import com.xabber.presentation.application.fragments.contacts.edit.EditContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.notifications.NotificationDialog
import com.xabber.presentation.application.fragments.notifications.NotificationFragment
import com.xabber.presentation.application.fragments.savedMessages.SavedMessagesDialog
import com.xabber.presentation.application.fragments.savedMessages.SavedMessagesFragment
import com.xabber.presentation.application.fragments.settings.*
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.presentation.application.manage.DisplayManager.getMainContainerWidth
import com.xabber.presentation.application.manage.DisplayManager.isDualScreenMode
import com.xabber.presentation.application.manage.MaskManager
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.lockScreenRotation
import com.xabber.utils.toAccountDto
import com.xabber.utils.toAvatarDto
import io.realm.kotlin.Realm

/**
 * ApplicationActivity implements the interface Navigator. Its methods are responsible for navigation.
 * This activity splits the screen into two if device is tablet.
 * The application works in full screen mode. This activity set height status bar in DisplayManager, so that fragments can
 * set indent. SoftInputAssist responsible for the correct height of the soft keyboard in full screen mode.
 * In onCreate check condition: user is authorized (stay this activity) or not (go to Onboarding activity)
 */

class ApplicationActivity : AppCompatActivity(), Navigator, NavigationView.OnNavigationItemSelectedListener, AccountAdapter.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val binding: ActivityApplicationBinding by lazy {
        ActivityApplicationBinding.inflate(
            layoutInflater
        )
    }

    private val realm = Realm.open(defaultRealmConfig())
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarToggle: ActionBarDrawerToggle
    private var assist: SoftInputAssist? = null
    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)
    private val viewModel = ApplicationViewModel()
    private val chatListViewModel: ChatListViewModel by viewModels()
    private var shapeView: ShapeOfView? = null
//    private val showBadge = {
//        val count = viewModel.unreadMessage.value
//        if (count != null) {
//            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                if (count > 0) {
//                    val creator = binding.railNavBar!!.getOrCreateBadge(R.id.chats)
//                    creator.backgroundColor =
//                        ResourcesCompat.getColor(
//                            binding.railNavBar!!.resources,
//                            R.color.green_500,
//                            null
//                        )
//                    creator.badgeGravity = BadgeDrawable.BOTTOM_END
//                    creator.number = count
//                } else binding.railNavBar!!.removeBadge(R.id.chats)
//            } else {
//                if (count > 0) {
//                    val creator = binding.bottomNavBar!!.getOrCreateBadge(R.id.chats)
//                    creator.backgroundColor =
//                        ResourcesCompat.getColor(
//                            binding.bottomNavBar!!.resources,
//                            R.color.green_500,
//                            null
//                        )
//                    creator.badgeGravity = BadgeDrawable.BOTTOM_END
//                    creator.number = count
//                } else binding.bottomNavBar!!.removeBadge(R.id.chats)
//            }
//        }else binding.bottomNavBar!!.removeBadge(R.id.chats)
//    }

    @SuppressLint("WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ThemeApplication)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initViews()
        setupStatusBar()
        setupNavigationDrawer()

        if (viewModel.checkIsEntry()) {
            initializeAppForLoggedInUser(savedInstanceState)
        } else {
            goToOnboarding()
        }
        val profileButton: LinearLayout = findViewById(R.id.profile_button)

        profileButton.setOnClickListener {
            handleProfileNavigation()
        }
        shapeView?.setDrawable(MaskManager.mask)
        val sharedPreferences = getSharedPreferences(AppConstants.SHARED_PREF_MASK, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun setupStatusBar() {
        window?.statusBarColor = Color.TRANSPARENT
    }

    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val toolbarNav = findViewById<Toolbar>(R.id.toolbar_nav)
        val dpAsPixels = getStatusBarHeight()
// Get the current padding values
        val currentPaddingLeft = toolbarNav.paddingLeft
        val currentPaddingRight = toolbarNav.paddingRight
        val currentPaddingBottom = toolbarNav.paddingBottom

// Set the new padding with only the top padding updated
        toolbarNav.setPadding(currentPaddingLeft, dpAsPixels, currentPaddingRight, currentPaddingBottom)

        setSupportActionBar(toolbarNav)

        supportActionBar?.setDisplayShowTitleEnabled(false)


        val avatarImageView = findViewById<ImageView>(R.id.avatar_image_view)
        val titleTextView = findViewById<TextView>(R.id.title_text_view)
        val subtitleTextView = findViewById<TextView>(R.id.subtitle_text_view)

        val account = getPrimaryAccount()
        val avatar = account?.let { getAvatar(it.id) }

        account?.let {
            titleTextView.text = it.getAccountName()
            subtitleTextView.text = it.jid
        }

        avatar?.let {
            Glide.with(this).load(it.fileUri).into(avatarImageView)
            avatarImageView.requestLayout()
        }

        navigationView.setNavigationItemSelectedListener(this)
        actionBarToggle = ActionBarDrawerToggle(this, drawerLayout,  0, 0).apply {
            drawerLayout.addDrawerListener(this)
            syncState()

        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (DisplayManager.isDualScreenMode() && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            drawerLayout.setScrimColor(Color.parseColor("#80000000")) // Semi-transparent black overlay
            drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    // Adjust the scrim dynamically if needed
                    drawerLayout.setScrimColor(Color.parseColor("#80000000"))
                }
                override fun onDrawerOpened(drawerView: View) {}
                override fun onDrawerClosed(drawerView: View) {}
                override fun onDrawerStateChanged(newState: Int) {}
            })

            // Ensure the drawer layout spans the full width of the screen
            drawerLayout.layoutParams = drawerLayout.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }

    }


    private fun initViews() {
        shapeView = findViewById(R.id.shape_view)
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        shapeView?.setDrawable(MaskManager.mask)
    }
    private fun initializeAppForLoggedInUser(savedInstanceState: Bundle?) {
        updateUiDependingOnMode(isDualScreenMode())
        setFullScreenMode()
        setHeightStatusBar()
        setMask()
        setChatSettings()

        binding.slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED_CLOSED
        assist = SoftInputAssist(window)
        subscribeToViewModelData()

        if (savedInstanceState != null) {
            setupIconChat(chatListViewModel.showUnreadOnly.value ?: false)
        } else {
            launchFragment(ChatListFragment())
        }
    }
    private fun toggleNightDayMode() {

        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            AppCompatDelegate.MODE_NIGHT_NO -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        Toast.makeText(this, "Mode switched!", Toast.LENGTH_SHORT).show()

    }


    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { T -> T.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
        }
        return accountDto
    }

    private fun getAvatar(id: String): AvatarDto? {
        var avatarDto: AvatarDto? = null
        realm.writeBlocking {
            val realmAvatar =
                this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
            if (realmAvatar != null)
                avatarDto = realmAvatar.toAvatarDto()
        }
        return avatarDto
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.chats -> handleChatsNavigation()
            R.id.calls -> handleCallsNavigation()
            R.id.contacts -> handleContactsNavigation()
            R.id.notifications -> handleNotificationsNavigation()
            R.id.archive -> handleArchiveNavigation()
            R.id.saved_messages-> handleSavedMessagesNavigation()
        }

        closeDrawerSlowly()
        return true
    }

    private fun handleProfileNavigation() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation
        val jid = getPrimaryAccount()?.jid ?: run {
            Toast.makeText(this, "No account found", Toast.LENGTH_SHORT).show()
            closeDrawerSlowly()
            return
        }

        if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
            (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)) {
            val accountDialog = AccountDialog.newInstance(jid)
            accountDialog.show(supportFragmentManager, "Account")
        } else {
            showAccount(jid)
        }
        closeDrawerSlowly()
    }

    private fun handleChatsNavigation() {
        if (activeFragment !is ChatListFragment) {
            closeDetail()
            replaceFragment(ChatListFragment())

        } else {
            showUnreadChats(!chatListViewModel.showUnreadOnly.value!!)
        }
    }

    private fun handleNotificationsNavigation() {
        chatListViewModel.setShowUnreadOnly(false)

        if (activeFragment !is NotificationFragment) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
                (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)
            ) {
                val notify = NotificationDialog()
                notify.show(supportFragmentManager, "notifications")
            } else {
                replaceFragment(NotificationFragment())

                binding.toolbarNav.isVisible = false
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        setupIconChat(false)

    }

    private fun handleSavedMessagesNavigation() {
        chatListViewModel.setShowUnreadOnly(false)

        if (activeFragment !is SavedMessagesFragment) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
                (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)
            ) {
                val SavedMessages = SavedMessagesDialog()
                SavedMessages.show(supportFragmentManager, "Saved Messages")
            } else {
                replaceFragment(NotificationFragment())

                binding.toolbarNav.isVisible = false
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        setupIconChat(false)

    }

    private fun handleArchiveNavigation() {
        chatListViewModel.setShowUnreadOnly(false)

        if (activeFragment !is ArchiveFragment) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
                (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)
            ) {
                val archive = ArchiveDialog()
                archive.show(supportFragmentManager, "archive")
            } else {
                replaceFragment(ArchiveFragment())

                binding.toolbarNav.isVisible = false
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        setupIconChat(false)
    }

    private fun handleCallsNavigation() {

        chatListViewModel.setShowUnreadOnly(false)

        if (activeFragment !is CallsFragment) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
                (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)
            ) {
                val calls = CallsDialog()
                calls.show(supportFragmentManager, "calls")
            } else {
                replaceFragment(CallsFragment())

                binding.toolbarNav.isVisible = false
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        setupIconChat(false)
    }

    private fun handleContactsNavigation() {
        chatListViewModel.setShowUnreadOnly(false)
        if (activeFragment !is ContactsFragment) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
                (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)
            ) {
                val contacts = ContactsDialog()
                contacts.show(supportFragmentManager, "contacts")
            } else {
                replaceFragment(ContactsFragment())
                binding.toolbarNav.isVisible = false
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        setupIconChat(false)
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(null)
            .replace(R.id.application_container, fragment)
            .commit()
    }



    private fun closeDrawerSlowly() {
            drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun onBackPressed() {

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
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
    @SuppressLint("DiscouragedApi")
    fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
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
        binding.delimiterToolbar?.updateLayoutParams<ConstraintLayout.LayoutParams> {
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
        binding.detailContainer?.setBackgroundResource(gradientDraw)
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



    private fun subscribeToViewModelData() {
        viewModel.initAccountListListener()
        viewModel.initUnreadMessagesCountListener()
        viewModel.unreadMessage.observe(this) {
           // handler.postDelayed(showBadge, 300)
        }
        viewModel.getUnreadMessages()
    }

    private fun setupIconChat(unreadChats: Boolean) {
     // if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val menuItem = binding.navView.menu.findItem(R.id.chats)
            if (unreadChats) {
                menuItem.setIcon(R.drawable.ic_chat_alert)
            } else {
                menuItem.setIcon(R.drawable.ic_chat)
            }
//        } else  {
//            val menuItem = binding.bottomNavBar!!.menu.findItem(R.id.chats)
//            if (unreadChats) {
//                menuItem.setIcon(R.drawable.ic_chat_alert)
//            } else {
//                menuItem.setIcon(R.drawable.ic_chat)
//            }
//        }
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
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            drawerLayout.closeDrawer(GravityCompat.START)
            binding.toolbarNav!!.isVisible = true
            setupNavigationDrawer()
        }
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

    override fun showNotificationsSettings (inStack: Boolean) {
        if (inStack) launchDetailInStack(NotificationsFragmentFull()) else launchDetail(NotificationsFragmentFull())
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
        val sharedPreferences = getSharedPreferences(AppConstants.SHARED_PREF_MASK, Context.MODE_PRIVATE)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onClick(id: String) {
        TODO("Not yet implemented")
    }

    override fun setEnabled(id: String, isChecked: Boolean) {
        TODO("Not yet implemented")
    }

}
