package com.xabber.presentation.application.activity


import android.Manifest
import  android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.account.AccountManager
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
import com.xabber.presentation.application.fragments.calls.CallFiltersFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chat.ChatSettingsFragment
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.view.ChatView
import com.xabber.presentation.application.fragments.chatlist.ChatListView
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.presentation.application.fragments.chatlist.add.NewChatFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewContactFragment
import com.xabber.presentation.application.fragments.chatlist.add.NewGroupFragment
import com.xabber.presentation.application.fragments.chatlist.archive.ArchiveFragment
import com.xabber.presentation.application.fragments.chatlist.archive.ArchivePanelFragment
import com.xabber.presentation.application.fragments.chatlist.forward.ChatListToForwardFragment
import com.xabber.presentation.application.fragments.contacts.*
import com.xabber.presentation.application.fragments.contacts.edit.EditContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.notifications.NotificationFragment
import com.xabber.presentation.application.fragments.notifications.NotificationPanelFragment
import com.xabber.presentation.application.fragments.savedMessages.SavedMessagesFragment
import com.xabber.presentation.application.fragments.savedMessages.SavedMessagesPanelFragment
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
import com.xabber.xmpp.avatar.AvatarStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.xabber.data_base.models.account.AccountStorageItem
import android.view.ViewTreeObserver


/**
 * ApplicationActivity implements the interface Navigator. Its methods are responsible for navigation.
 * This activity splits the screen into two if device is tablet.
 * The application works in full screen mode. This activity set height status bar in DisplayManager, so that fragments can
 * set indent. SoftInputAssist responsible for the correct height of the soft keyboard in full screen mode.
 * In onCreate check condition: user is authorized (stay this activity) or not (go to Onboarding activity)
 */

@Suppress("DEPRECATION")
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
    private var navigationDrawerInitialized = false
    private var pendingNavigationHeaderUpdate = false
    private var pendingPostLaunchUiSetup = false
    private var startupTrace: StartupTrace? = null
    private var firstPreDrawLogged = false
    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)
    private val viewModel = ApplicationViewModel()
    private val chatListViewModel: ChatListViewModel by viewModels()
    private var shapeView: ShapeOfView? = null
    private var isLoggingOut: Boolean = false // Prevent multiple logout calls
    private var isUpdatingUI: Boolean = false // Prevent recursive UI updates

    private var reconnectSnackbar: Snackbar? = null

    // --- Chat view stack (max 6 cached chat fragments) ---
    private val chatFragmentStack = ArrayDeque<String>() // chatIds, oldest first
    private var currentDetailTag: String? = null

    private fun chatTag(chatId: String) = "${CHAT_TAG_PREFIX}${chatId}"

    /** Scan cached chat fragments and update currentDetailTag to whichever is currently visible. */
    private fun syncCurrentDetailTag() {
        val fm = supportFragmentManager
        for (chatId in chatFragmentStack.reversed()) {
            val tag = chatTag(chatId)
            val frag = fm.findFragmentByTag(tag) ?: continue
            if (!frag.isHidden) {
                currentDetailTag = tag
                return
            }
        }
        currentDetailTag = null
    }
    // ------------------------------------------------------

    companion object {
        var currentActivity: ApplicationActivity? = null
            private set

        private const val CHAT_TAG_PREFIX = "chat_"
        private const val MAX_CHAT_STACK = 10
        private const val KEY_CHAT_STACK = "chat_fragment_stack"
        private const val KEY_CURRENT_DETAIL_TAG = "current_detail_tag"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        startupTrace = StartupTrace("ApplicationActivity", "ApplicationActivity")
        setTheme(R.style.ThemeApplication)
        super.onCreate(savedInstanceState)
        startupTrace?.step("super.onCreate")
        val toolbarNav = findViewById<Toolbar>(R.id.toolbar_nav)
        setSupportActionBar(toolbarNav)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        setContentView(binding.root)
        registerFirstFrameTrace()
        initViews()
        setupStatusBar()
        currentActivity = this
        startupTrace?.step("base ui initialized")

        // Observe reconnecting state from AccountManager and drive the snackbar.
        // lifecycleScope runs on Main and is cancelled when the activity is destroyed.
        // repeatOnLifecycle(STARTED) pauses collection when the app goes to background.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasReconnecting = false
                AccountManager.reconnectingState.collect { isReconnecting ->
                    if (isReconnecting) {
                        wasReconnecting = true
                        showReconnectingSnackbar()
                    } else if (wasReconnecting) {
                        wasReconnecting = false
                        hideReconnectingSnackbar()
                        // Chat fragments observe reconnectingState themselves and
                        // re-sync their archive — no need to destroy and recreate them.
                    }
                }
            }
        }

        // Restore chat stack state after process recreation
        if (savedInstanceState != null) {
            val savedStack = savedInstanceState.getStringArrayList(KEY_CHAT_STACK)
            if (savedStack != null) chatFragmentStack.addAll(savedStack)
            currentDetailTag = savedInstanceState.getString(KEY_CURRENT_DETAIL_TAG)
        }

        // Keep currentDetailTag in sync whenever the backstack changes (e.g. popBackStack via close())
        supportFragmentManager.addOnBackStackChangedListener { syncCurrentDetailTag() }

        setupBackPressedHandler()
        startupTrace?.step("back press handler ready")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        startupTrace?.step("notification permission check")


        // Check for an existing account
        val hasStoredAccount = hasStoredAccount()
        startupTrace?.step("checked stored account")
        if (hasStoredAccount) {
            Log.d("ApplicationActivity", "Found existing account, initializing app")
            initializeAppForLoggedInUser(savedInstanceState)
            startupTrace?.step("logged-in ui initialized")
            lifecycleScope.launch {
                startupTrace?.step("account load coroutine launched")
                AccountManager.loadFirstAccountAsync()
            }
        } else {
            Log.d("ApplicationActivity", "No account found, redirecting to onboarding")
            goToOnboarding()
        }
        val profileButton: LinearLayout = findViewById(R.id.profile_button)

        profileButton.setOnClickListener {
            handleProfileNavigation()
        }
        shapeView?.setDrawable(MaskManager.mask)
        val sharedPreferences = getSharedPreferences(AppConstants.SHARED_PREF_MASK, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        handleNotificationIntent(intent)
        startupTrace?.step("onCreate complete")

//        CoroutineScope(Dispatchers.IO).launch {
//            Account().loadAccount()
//            val connected = Account().connectStream()
//            if (!connected) {
//                withContext(Dispatchers.Main) {
//                    showErrorDialog("Failed to connect to server.")
//                }
//            }
//        }
//        val dnsTestButton = findViewById<ImageView>(R.id.dnsTestButton)
//        val resultText = findViewById<TextView>(R.id.result_text)
//        dnsTestButton.setOnClickListener {
//            lifecycleScope.launch {
//                try {
//                    val result = withContext(Dispatchers.IO) {
//                        fetchFromSrv()
//                    }
//                    resultText.text = result
//                } catch (e: Exception) {
//                    Log.e(TAG, "Failed to fetch SRV data: ${e.message}", e)
//                    resultText.text = "Failed: ${e.message}"
//                }
//            }
//        }


    }

    private fun registerFirstFrameTrace() {
        binding.root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstPreDrawLogged) {
                    firstPreDrawLogged = true
                    startupTrace?.step("first pre-draw")
                    binding.root.post {
                        startupTrace?.step("first frame committed")
                    }
                }
                if (binding.root.viewTreeObserver.isAlive) {
                    binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                }
                return true
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val owner = intent?.getStringExtra("owner") ?: return
        val jid = intent.getStringExtra("jid") ?: return
        val category = intent.getStringExtra("category") ?: return
        // Clear extras so re-creation doesn't re-open the same chat
        intent.removeExtra("owner")
        intent.removeExtra("jid")
        intent.removeExtra("category")
        val chatId = jid + owner + category
        showChatInStack(ChatParams(chatId))
    }

    private fun showErrorDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(errorMessage)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Опционально: перенаправить на экран логина
            }
            .setCancelable(false)
            .create()
            .show()
    }

    private fun setupStatusBar() {
        window?.statusBarColor = Color.TRANSPARENT
    }

    private fun setupNavigationDrawer() {
        startupTrace?.step("setupNavigationDrawer:start")
        if (isLoggingOut || isUpdatingUI) {
            Log.w("ApplicationActivity", "Skipping setupNavigationDrawer during logout or UI update")
            return
        }
        isUpdatingUI = true
        try {
            drawerLayout = findViewById(R.id.drawer_layout)
            val navigationView = findViewById<NavigationView>(R.id.nav_view)
            val toolbarNav = findViewById<Toolbar>(R.id.toolbar_nav)
            if (!navigationDrawerInitialized) {
                val statusBarHeight = DisplayManager.getHeightStatusBar()

                ViewCompat.setOnApplyWindowInsetsListener(navigationView) { v, insets ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
                    insets
                }
                toolbarNav.setPadding(
                    toolbarNav.paddingLeft, statusBarHeight,
                    toolbarNav.paddingRight, toolbarNav.paddingBottom
                )

                supportActionBar?.setDisplayShowTitleEnabled(false)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                actionBarToggle = ActionBarDrawerToggle(this, drawerLayout, toolbarNav, R.string.open, R.string.close)
                drawerLayout.addDrawerListener(actionBarToggle)
                actionBarToggle.syncState()
                navigationView.setNavigationItemSelectedListener(this)
                drawerLayout.setScrimColor(Color.parseColor("#88000000"))
                navigationDrawerInitialized = true
            }

            scheduleNavigationHeaderUpdate()

        } finally {
            isUpdatingUI = false
            startupTrace?.step("setupNavigationDrawer:end")
        }
    }

    private fun scheduleNavigationHeaderUpdate() {
        if (pendingNavigationHeaderUpdate) return
        pendingNavigationHeaderUpdate = true
        binding.root.post {
            pendingNavigationHeaderUpdate = false
            if (isDestroyed || isFinishing) return@post
            startupTrace?.step("navigation header update:start")
            updateNavigationHeader()
            startupTrace?.step("navigation header update:end")
        }
    }

    private fun updateNavigationHeader() {
        val avatarImageView = findViewById<ImageView>(R.id.avatar_image_view)
        val titleTextView = findViewById<TextView>(R.id.title_text_view)
        val subtitleTextView = findViewById<TextView>(R.id.subtitle_text_view).also { it.isSelected = true }
        val account = getPrimaryAccount()
        val avatar = account?.let { getAvatar(it.id) }

        titleTextView.text = account?.getAccountName().orEmpty()
        subtitleTextView.text = account?.jid.orEmpty()

        if (avatar != null) {
            Glide.with(this).load(avatar.fileUri).into(avatarImageView)
            avatarImageView.requestLayout()
        } else {
            avatarImageView.setImageDrawable(null)
        }
    }


    private fun initViews() {
        shapeView = findViewById(R.id.shape_view)
    }

    private fun hasStoredAccount(): Boolean {
        return realm.query<AccountStorageItem>("enabled = true").first().find() != null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        shapeView?.setDrawable(MaskManager.mask)
    }

    private fun initializeAppForLoggedInUser(savedInstanceState: Bundle?) {
        setupNavigationDrawer()
        updateUiDependingOnMode(isDualScreenMode())
        startupTrace?.step("dual screen update")
        setupEdgeToEdge()
        startupTrace?.step("edge-to-edge ready")
        setMask()
        startupTrace?.step("mask applied")

        if (savedInstanceState == null) { // Only set up if not restoring state
            if (supportFragmentManager.findFragmentById(R.id.application_container) == null) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.application_container, ChatListView())
                    addToBackStack("chat_list_root")
                }
                startupTrace?.step("chat list fragment committed")
            }
        } else {
            setupIconChat(chatListViewModel.showUnreadOnly.value ?: false)
            startupTrace?.step("restored chat icon state")
        }

        schedulePostLaunchUiSetup()
        startupTrace?.step("post launch ui scheduled")
    }

    private fun schedulePostLaunchUiSetup() {
        if (pendingPostLaunchUiSetup) return
        pendingPostLaunchUiSetup = true
        binding.root.post {
            pendingPostLaunchUiSetup = false
            if (isDestroyed || isFinishing) return@post
            startupTrace?.step("post launch ui:start")
            setChatSettings()
            startupTrace?.step("chat settings applied")
            handleUnread()
            startupTrace?.step("unread handler ready")
            subscribeToViewModelData()
            startupTrace?.step("view model subscriptions ready")
        }
    }



    private fun getPrimaryAccount(): AccountDto? {
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = PrimaryAccountSelector.selectPrimaryAccountId(
            realmAccounts.map { it.primary to it.order }
        ) ?: return null
        return realmAccounts.firstOrNull { it.primary == primaryAccount }?.toAccountDto()
    }

    private fun getAvatar(id: String): AvatarDto? {
        val realmAvatar = realm.query(AvatarStorageItem::class, "primary = '$id'").first().find()
        return realmAvatar?.toAvatarDto()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (actionBarToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
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

    @RequiresApi(Build.VERSION_CODES.O)
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
        val currentFragment = supportFragmentManager.findFragmentById(R.id.application_container)
        if (currentFragment !is ChatListView) {
            closeDetail()
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.application_container, ChatListView())
                addToBackStack("chat_list_root")
            }
        }
    }


    private fun handleUnread() {
        val unreadMessages = binding.toolbarNav.findViewById<ImageView>(R.id.unread)
        unreadMessages.setOnClickListener{
            showUnreadChats(!chatListViewModel.showUnreadOnly.value!!)
        }

    }
    //    private fun handleContactAddition() {
//        binding.toolbarNav.findViewById<ImageView>(R.id.add).setOnClickListener {
//            if (chatListViewModel.chatIsEmpty()) chatListViewModel.addSomeChats()
//            else showNewChat()
//        }
//    }
    private fun handleNotificationsNavigation() {

        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation
        // Check if there's an active chat in the detail container
        val currentDetailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val isChatActive = currentDetailFragment is ChatView

        if (activeFragment !is NotificationFragment) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Dual-screen mode: Add CallFiltersFragment to main container and CallsFragment to detail
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, NotificationFragment())
                    .addToBackStack("main_notification_frag") // Add to backstack to preserve prior state
                    .commit()

                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.detail_container, NotificationPanelFragment())
                    .addToBackStack("alternative_notification_frag") // Add to backstack to preserve chat
                    .commit()
            } else {
                // Single-screen mode: Replace main container with CallsFragment
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, NotificationPanelFragment())
                    .addToBackStack("main_notification_frag") // Add to backstack
                    .commit()
            }

            binding.toolbarNav.isVisible = false
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        setupIconChat(false)



    }

    private fun handleSavedMessagesNavigation() {

        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation
        // Check if there's an active chat in the detail container
        val currentDetailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val isChatActive = currentDetailFragment is ChatView

        if (activeFragment !is SavedMessagesFragment) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Dual-screen mode: Add CallFiltersFragment to main container and CallsFragment to detail
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, SavedMessagesFragment())
                    .addToBackStack("main_saved_messages_frag") // Add to backstack to preserve prior state
                    .commit()

                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.detail_container, SavedMessagesPanelFragment())
                    .addToBackStack("alternative_saved_messages_frag") // Add to backstack to preserve chat
                    .commit()
            } else {
                // Single-screen mode: Replace main container with CallsFragment
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, SavedMessagesFragment())
                    .addToBackStack("main_saved_messages_frag") // Add to backstack
                    .commit()
            }

            binding.toolbarNav.isVisible = false
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        setupIconChat(false)


    }

    private fun handleArchiveNavigation() {

        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation
        // Check if there's an active chat in the detail container
        val currentDetailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val isChatActive = currentDetailFragment is ChatView

        if (activeFragment !is ArchiveFragment) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Dual-screen mode: Add CallFiltersFragment to main container and CallsFragment to detail
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, ArchiveFragment())
                    .addToBackStack("main_archive_frag") // Add to backstack to preserve prior state
                    .commit()

                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.detail_container, ArchivePanelFragment())
                    .addToBackStack("alternative_archive_frag") // Add to backstack to preserve chat
                    .commit()
            } else {
                // Single-screen mode: Replace main container with CallsFragment
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, ArchiveFragment())
                    .addToBackStack("main_archive_frag") // Add to backstack
                    .commit()
            }

            binding.toolbarNav.isVisible = false
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        setupIconChat(false)

    }

    private fun handleCallsNavigation() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation
        if (activeFragment !is CallsFragment) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Dual-screen mode: Add CallFiltersFragment to main container and CallsFragment to detail
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, CallFiltersFragment())
                    .addToBackStack("call_filters") // Add to backstack to preserve prior state
                    .commit()

                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.detail_container, CallsFragment())
                    .addToBackStack("calls") // Add to backstack to preserve chat
                    .commit()
            } else {
                // Single-screen mode: Replace main container with CallsFragment
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, CallsFragment())
                    .addToBackStack("calls") // Add to backstack
                    .commit()
            }

            binding.toolbarNav.isVisible = false
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        setupIconChat(false)
    }

    override fun goBackFromCalls() {
        supportFragmentManager.popBackStack("calls", 0)
    }

    private fun handleContactsNavigation() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation
        // Check if there's an active chat in the detail container
        val currentDetailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val isChatActive = currentDetailFragment is ChatView

        if (activeFragment !is ContactsFragment) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Dual-screen mode: Add CallFiltersFragment to main container and CallsFragment to detail
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, ContactsPanelFragment())
                    .addToBackStack("main_contact_frag") // Add to backstack to preserve prior state
                    .commit()

                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.detail_container, ContactsFragment())
                    .addToBackStack("alternative_contact_frag") // Add to backstack to preserve chat
                    .commit()
            } else {
                // Single-screen mode: Replace main container with CallsFragment
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, ContactsFragment())
                    .addToBackStack("main_contact_frag") // Add to backstack
                    .commit()
            }

            binding.toolbarNav.isVisible = false
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        setupIconChat(false)

    }

    private fun handleDiscoverNavigation() {
        closeDetail()
        if (activeFragment !is DiscoverFragment) {
            replaceFragment(DiscoverFragment())

            binding.toolbarNav.isVisible = false
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerLayout.closeDrawer(GravityCompat.START)
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

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if the drawer is open first
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                val dialogFragment = supportFragmentManager.fragments.find { it is DialogFragment && it.dialog?.isShowing == true }
                if (dialogFragment != null) {
                    (dialogFragment as DialogFragment).dismiss()
                    return
                }

                // Existing navigation logic
                val currentFragment = supportFragmentManager.findFragmentById(R.id.application_container)
                Log.d("ApplicationActivity", "onBackPressed: Current fragment = ${currentFragment?.javaClass?.simpleName}, Back stack count = ${supportFragmentManager.backStackEntryCount}")

                if (supportFragmentManager.backStackEntryCount > 0) {
                    Log.d("ApplicationActivity", "Popping to chat_list_root")
                    supportFragmentManager.popBackStack("chat_list_root", 0)
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    binding.toolbarNav.isVisible = true
                    setupNavigationDrawer()
                } else if (currentFragment == null || currentFragment !is ChatListView) {
                    Log.d("ApplicationActivity", "Replacing with ChatListFragment")
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace(R.id.application_container, ChatListView())
                        addToBackStack("chat_list_root")
                    }
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    binding.toolbarNav.isVisible = true
                    actionBarToggle.syncState()
                } else {
                    Log.d("ApplicationActivity", "Already on ChatListFragment, finishing")
                    finish()
                }
            }
        })
    }
    override fun onResume() {
        super.onResume()
        // keyboard insets handled by setupEdgeToEdge()
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

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.slidingPaneLayout) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            // Store for fragments that read from DisplayManager
            DisplayManager.setHeightStatusBar(systemBars.top)
            DisplayManager.setHeightNavBar(systemBars.bottom)

            // Toolbar top padding — toolbar background extends behind status bar,
            // but toolbar content (icons, title) sits below it
            binding.toolbarNav.setPadding(
                binding.toolbarNav.paddingLeft, systemBars.top,
                binding.toolbarNav.paddingRight, binding.toolbarNav.paddingBottom
            )

            // Bottom: use keyboard height when visible, otherwise nav bar height
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)
            binding.mainContainer.setPadding(0, 0, 0, bottomInset)
            binding.detailContainer.setPadding(0, 0, 0, bottomInset)

            // Update delimiter height for dual-screen mode
            setDelimiters(systemBars.top)

            windowInsets
        }
    }

    fun getStatusBarHeight(): Int = DisplayManager.getHeightStatusBar()
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

        val gradientDraw = ChatSettingsVisuals.gradientDrawable(gradient)
        binding.detailContainer.setBackgroundResource(gradientDraw)
        val designDrawable = ChatSettingsVisuals.patternDrawable(designType)
        binding.fr.setBackgroundResource(designDrawable)
    }



    @RequiresApi(Build.VERSION_CODES.O)
    override fun logOut() {
        if (isLoggingOut) {
            Log.w("ApplicationActivity", "Logout already in progress, skipping")
            return
        }
        isLoggingOut = true
        try {
            val primaryAccount = getPrimaryAccount()
            if (primaryAccount == null) {
                Log.w("ApplicationActivity", "No primary account found to log out")
                Toast.makeText(this, "No account found to log out", Toast.LENGTH_SHORT).show()
                goToOnboarding()
                return
            }
            val jid = primaryAccount.jid.trim().lowercase()
            Log.d("ApplicationActivity", "Attempting logout for jid $jid")
            if (AccountManager.logout(jid)) {
                Log.d("ApplicationActivity", "Logout successful for jid $jid")
                if (AccountManager.loadFirstAccount() == null) {
                    Log.d("ApplicationActivity", "No accounts remain, navigating to OnBoardingActivity")
                    goToOnboarding()
                } else {
                    Log.d("ApplicationActivity", "Other accounts remain, updating UI")
                    updateAccountUI()
                }
            } else {
                Log.w("ApplicationActivity", "Logout failed for jid $jid, forcing session reset")
                AccountManager.users.clear()
                if (AccountManager.loadFirstAccount() == null) {
                    Log.d("ApplicationActivity", "No accounts remain after forced reset, navigating to OnBoardingActivity")
                    goToOnboarding()
                } else {
                    Log.d("ApplicationActivity", "Other accounts remain after forced reset, updating UI")
                    updateAccountUI()
                }
            }
        } finally {
            isLoggingOut = false
        }
    }

    fun updateAccountUI() {
        if (isLoggingOut || isUpdatingUI) {
            Log.w("ApplicationActivity", "Skipping updateAccountUI during logout or UI update")
            return
        }
        isUpdatingUI = true
        try {
            Log.d("ApplicationActivity", "Updating UI for current account")
            // Refresh the navigation drawer
            setupNavigationDrawer()
            // Ensure the main fragment is shown (e.g., ChatListFragment)
            if (supportFragmentManager.findFragmentById(R.id.application_container) !is ChatListView) {
                Log.d("ApplicationActivity", "Replacing fragment with ChatListFragment")
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.application_container, ChatListView())
                    addToBackStack("chat_list_root")
                }
            } else {
                Log.d("ApplicationActivity", "ChatListFragment already active")
            }
        } finally {
            isUpdatingUI = false
        }
    }

    private fun subscribeToViewModelData() {
        viewModel.initUnreadMessagesCountListener()
        viewModel.unreadMessage.observe(this) {
            // handler.postDelayed(showBadge, 300)
        }
    }

    private fun setupIconChat(unreadChats: Boolean) {
        val menuItem = binding.toolbarNav.findViewById<ImageView>(R.id.unread)
        if (unreadChats) {
            menuItem.setImageResource(R.drawable.unread)
        } else {
            menuItem.setImageResource(R.drawable.unread_outline)
        }
    }
    private fun goToOnboarding() {
        val intent = Intent(applicationContext, OnBoardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        moveFragmentsOnOrientationChange(newConfig.orientation)
        updateUiDependingOnMode(isDualScreenMode())
    }

    private fun moveFragmentsOnOrientationChange(newOrientation: Int) {
        val detailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val appFragment = supportFragmentManager.findFragmentById(R.id.application_container)

        Log.d("ApplicationActivity", "Orientation: ${if (newOrientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"}")
        Log.d("ApplicationActivity", "Detail: ${detailFragment?.javaClass?.simpleName}, App: ${appFragment?.javaClass?.simpleName}")

        when {
            appFragment is CallsFragment || detailFragment is CallsFragment -> adjustCallsFragments(newOrientation)
            appFragment is ContactsFragment || detailFragment is ContactsFragment -> adjustContactsFragments(newOrientation)
            appFragment is NotificationPanelFragment || detailFragment is NotificationPanelFragment -> adjustNotificationsFragments(newOrientation)
            appFragment is SavedMessagesPanelFragment || detailFragment is SavedMessagesPanelFragment -> adjustSavedMessagesFragments(newOrientation)
            // No changes for other fragments
        }

        // Update toolbar and drawer visibility, and navigation icon
        updateToolbarAppearance(appFragment)
    }

    private fun adjustCallsFragments(orientation: Int) {
        val detailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val appFragment = supportFragmentManager.findFragmentById(R.id.application_container)

        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (detailFragment !is CallsFragment || appFragment !is CallFiltersFragment) {
                    val callsArgs = (detailFragment as? CallsFragment ?: appFragment as? CallsFragment)?.arguments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is CallFiltersFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions()

                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.detail_container, CallsFragment().apply { arguments = callsArgs }, "calls")
                        .addToBackStack("calls")
                        .commit()

                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, CallFiltersFragment(), "call_filters")
                        .addToBackStack("call_filters")
                        .commit()
                }
                if (binding.slidingPaneLayout.isSlideable && !binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.openPane()
                }
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                if (appFragment !is CallsFragment || detailFragment != null) {
                    val callsArgs = (detailFragment as? CallsFragment ?: appFragment as? CallsFragment)?.arguments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is CallsFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions()

                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, CallsFragment().apply { arguments = callsArgs }, "calls")
                        .addToBackStack("calls")
                        .commit()
                }
                if (binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.closePane()
                }
            }
        }
        updateToolbarAppearance(appFragment)

    }

    private fun adjustContactsFragments(orientation: Int) {
        val detailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val appFragment = supportFragmentManager.findFragmentById(R.id.application_container)

        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Ensure ContactsFragment in detail_container, ContactsPanelFragment in application_container
                if (detailFragment !is ContactsFragment || appFragment !is ContactsPanelFragment) {
                    // Capture state if ContactsFragment exists
                    val contactsArgs = (detailFragment as? ContactsFragment ?: appFragment as? ContactsFragment)?.arguments

                    // Clear existing fragments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is ContactsPanelFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions() // Ensure removals complete

                    // Add new instances
                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.detail_container, ContactsFragment().apply { arguments = contactsArgs }, "contacts")
                        .addToBackStack("contacts")
                        .commit()

                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, ContactsPanelFragment(), "contacts_panel")
                        .addToBackStack("contacts_panel")
                        .commit()
                }
                if (binding.slidingPaneLayout.isSlideable && !binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.openPane()
                }
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                // Ensure ContactsFragment in application_container, detail_container empty
                if (appFragment !is ContactsFragment || detailFragment != null) {
                    // Capture state if ContactsFragment exists
                    val contactsArgs = (detailFragment as? ContactsFragment ?: appFragment as? ContactsFragment)?.arguments

                    // Clear existing fragments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is ContactsFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions() // Ensure removals complete

                    // Add new instance
                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, ContactsFragment().apply { arguments = contactsArgs }, "contacts")
                        .addToBackStack("contacts")
                        .commit()
                }
                if (binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.closePane()
                }
            }
        }

        // Update toolbar appearance after adjustment
        updateToolbarAppearance(appFragment)
    }

    private fun adjustNotificationsFragments(orientation: Int) {
        val detailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val appFragment = supportFragmentManager.findFragmentById(R.id.application_container)

        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Ensure NotificationPanelFragment in detail_container, NotificationsFragment in application_container
                if (detailFragment !is NotificationPanelFragment || appFragment !is NotificationFragment) {
                    // Capture state if NotificationPanelFragment exists
                    val notificationArgs = (detailFragment as? NotificationPanelFragment ?: appFragment as? NotificationPanelFragment)?.arguments

                    // Clear existing fragments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is NotificationFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions()

                    // Add new instances
                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.detail_container, NotificationPanelFragment().apply { arguments = notificationArgs }, "notification_panel")
                        .addToBackStack("notification_panel")
                        .commit()

                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, NotificationFragment(), "notifications")
                        .addToBackStack("notifications")
                        .commit()
                }
                if (binding.slidingPaneLayout.isSlideable && !binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.openPane()
                }
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                // Ensure NotificationPanelFragment in application_container, detail_container empty
                if (appFragment !is NotificationPanelFragment || detailFragment != null) {
                    // Capture state if NotificationPanelFragment exists
                    val notificationArgs = (detailFragment as? NotificationPanelFragment ?: appFragment as? NotificationPanelFragment)?.arguments

                    // Clear existing fragments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is NotificationPanelFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions()

                    // Add new instance
                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, NotificationPanelFragment().apply { arguments = notificationArgs }, "notification_panel")
                        .addToBackStack("notification_panel")
                        .commit()
                }
                if (binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.closePane()
                }
            }
        }
        updateToolbarAppearance(appFragment)

    }

    private fun adjustSavedMessagesFragments(orientation: Int) {
        val detailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val appFragment = supportFragmentManager.findFragmentById(R.id.application_container)

        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Ensure SavedMessagesPanelFragment in detail_container, SavedMessagesFragment in application_container
                if (detailFragment !is SavedMessagesPanelFragment || appFragment !is SavedMessagesFragment) {
                    // Capture state if SavedMessagesPanelFragment exists
                    val savedMessagesArgs = (detailFragment as? SavedMessagesPanelFragment ?: appFragment as? SavedMessagesPanelFragment)?.arguments

                    // Clear existing fragments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is SavedMessagesFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions()

                    // Add new instances
                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.detail_container, SavedMessagesPanelFragment().apply { arguments = savedMessagesArgs }, "saved_messages_panel")
                        .addToBackStack("saved_messages_panel")
                        .commit()

                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, SavedMessagesFragment(), "saved_messages")
                        .addToBackStack("saved_messages")
                        .commit()
                }
                if (binding.slidingPaneLayout.isSlideable && !binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.openPane()
                }
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                // Ensure SavedMessagesPanelFragment in application_container, detail_container empty
                if (appFragment !is SavedMessagesPanelFragment || detailFragment != null) {
                    // Capture state if SavedMessagesPanelFragment exists
                    val savedMessagesArgs = (detailFragment as? SavedMessagesPanelFragment ?: appFragment as? SavedMessagesPanelFragment)?.arguments

                    // Clear existing fragments
                    if (detailFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(detailFragment)
                            .commit()
                    }
                    if (appFragment != null && appFragment !is SavedMessagesPanelFragment) {
                        supportFragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .remove(appFragment)
                            .commit()
                    }
                    supportFragmentManager.executePendingTransactions()

                    // Add new instance
                    supportFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.application_container, SavedMessagesPanelFragment().apply { arguments = savedMessagesArgs }, "saved_messages_panel")
                        .addToBackStack("saved_messages_panel")
                        .commit()
                }
                if (binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.closePane()
                }
            }
        }
        updateToolbarAppearance(appFragment)

    }

    private fun updateToolbarAppearance(appFragment: Fragment?) {
        binding.toolbarNav.isVisible = appFragment is ChatListView
        drawerLayout.setDrawerLockMode(
            if (binding.toolbarNav.isVisible) DrawerLayout.LOCK_MODE_UNLOCKED
            else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        )
        // Remove navigation icon if not ChatListFragment
        if (appFragment !is ChatListView) {
            binding.toolbarNav.navigationIcon = null
        } else {
            // Restore navigation icon for ChatListFragment (assuming it uses the drawer icon)
            binding.toolbarNav.setNavigationIcon(null) // Adjust to your original icon
            actionBarToggle.syncState() // Ensure drawer toggle works
        }
    }

    private fun showUnreadChats(showUnread: Boolean) {
        if (activeFragment is ChatListView) chatListViewModel.toggleUnreadOnly()
        setupIconChat(showUnread)
    }

    private fun launchFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.application_container, fragment)
        }
    }

    fun showReconnectingSnackbar(attempt: Int = 1, maxAttempts: Int = 10) {
        if (isFinishing || isDestroyed) return

        // Используем корневой View – можно привязать к CoordinatorLayout, но в нашем layout его нет,
        // поэтому привязываемся к android.R.id.content (декор-контейнер)
        val rootView = findViewById<ViewGroup>(android.R.id.content) ?: binding.root

        // Создаём кастомный Snackbar с текстом и ProgressBar
        reconnectSnackbar?.dismiss()
        reconnectSnackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_INDEFINITE).apply {
            // Инфлейтим кастомный layout
            val customSnackbarLayout = layoutInflater.inflate(R.layout.snackbar_reconnect, null)
            val textView = customSnackbarLayout.findViewById<TextView>(R.id.snackbar_text)
            val progressBar = customSnackbarLayout.findViewById<ProgressBar>(R.id.snackbar_progress)

            textView.text = if (attempt == 1) {
                "Переподключение..."
            } else {
                "Переподключение... Попытка $attempt из $maxAttempts"
            }
            progressBar.isIndeterminate = true

            // Заменяем стандартную View Snackbar на нашу
            (view as Snackbar.SnackbarLayout).removeAllViews()
            (view as Snackbar.SnackbarLayout).addView(customSnackbarLayout, 0)

            // Цвет фона и отступы
            view.setBackgroundColor(Color.TRANSPARENT)
            (customSnackbarLayout.parent as View).setBackgroundColor(
                ContextCompat.getColor(this@ApplicationActivity, R.color.grey_600)
            )
        }
        reconnectSnackbar?.show()
    }

    fun hideReconnectingSnackbar() {
        reconnectSnackbar?.dismiss()
        reconnectSnackbar = null
    }

    /**
     * Called after a successful reconnect. Removes all cached chat fragments so
     * they are recreated fresh (with up-to-date data) the next time the user opens them.
     */
    fun clearChatStack() {
        if (isFinishing || isDestroyed) return
        val fm = supportFragmentManager
        fm.commit {
            setReorderingAllowed(true)
            for (chatId in chatFragmentStack) {
                fm.findFragmentByTag(chatTag(chatId))?.let { remove(it) }
            }
        }
        chatFragmentStack.clear()
        currentDetailTag = null
        if (binding.slidingPaneLayout.isOpen) {
            binding.slidingPaneLayout.close()
        }
        Log.d("ApplicationActivity", "clearChatStack: chat fragment stack cleared after reconnect")
    }

    override fun launchDetail(fragment: Fragment) {
        val fm = supportFragmentManager
        val tag = "detail_${System.nanoTime()}"
        val currentVisible = currentDetailTag?.let { fm.findFragmentByTag(it) }
        fm.commit {
            setReorderingAllowed(true)
            currentVisible?.let {
                // Chat fragments are kept alive (hidden); non-chat fragments are replaced (removed)
                if (currentDetailTag!!.startsWith(CHAT_TAG_PREFIX)) hide(it) else remove(it)
            }
            // Clean up any lingering non-hidden, non-chat fragments in detail_container
            // (handles race with delayed removal in closeDetail)
            fm.fragments.forEach { f ->
                if (f !== currentVisible && !f.isHidden && f.id == R.id.detail_container
                    && f.tag?.startsWith(CHAT_TAG_PREFIX) != true) {
                    remove(f)
                }
            }
            add(R.id.detail_container, fragment, tag)
        }
        currentDetailTag = tag
        binding.slidingPaneLayout.openPane()
    }

    override fun launchDetailInStack(fragment: Fragment) {
        val fm = supportFragmentManager
        // Hide (not remove) the current detail fragment so it survives back stack pop
        val current = currentDetailTag?.let { fm.findFragmentByTag(it) }
            ?: fm.findFragmentById(R.id.detail_container)?.takeIf { !it.isHidden }
        fm.commit {
            setReorderingAllowed(true)
            current?.let { hide(it) }
            add(R.id.detail_container, fragment)
            addToBackStack(null)
        }
    }

    override fun launchFragmentInStack(fragment: Fragment) {
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
        val currentFragment = supportFragmentManager.findFragmentById(R.id.application_container)
        Log.d("ApplicationActivity", "goBack: Current fragment = ${currentFragment?.javaClass?.simpleName}, Back stack count = ${supportFragmentManager.backStackEntryCount}")

        // Check for visible DialogFragments
        val dialogFragment = supportFragmentManager.fragments.find { it is DialogFragment && it.dialog?.isShowing == true }
        if (dialogFragment != null) {
            Log.d("ApplicationActivity", "Dismissing DialogFragment: ${dialogFragment.javaClass.simpleName}")
            (dialogFragment as DialogFragment).dismiss()
            return
        }

        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        if (supportFragmentManager.backStackEntryCount > 0) {
            // Pop back to chat_list_root
            supportFragmentManager.popBackStack("chat_list_root", 0)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            binding.toolbarNav.isVisible = true
            setupNavigationDrawer()

            // Hide/remove the visible detail fragment and close the pane
            val tag = currentDetailTag
            // Use the tracked tag if available; fall back to findFragmentById for fragments
            // opened via launchDetailInStack (replace+backstack, so currentDetailTag is null)
            val detailFrag = if (tag != null) {
                supportFragmentManager.findFragmentByTag(tag)
            } else {
                supportFragmentManager.findFragmentById(R.id.detail_container)?.takeIf { !it.isHidden }
            }
            if (detailFrag != null) {
                supportFragmentManager.beginTransaction().apply {
                    if (tag != null && tag.startsWith(CHAT_TAG_PREFIX)) hide(detailFrag) else remove(detailFrag)
                }.commit()
                Log.d("ApplicationActivity", "goBack: Hid/removed detail fragment")
            }
            currentDetailTag = null
            if (binding.slidingPaneLayout.isOpen) {
                binding.slidingPaneLayout.closePane()
                Log.d("ApplicationActivity", "goBack: Closed SlidingPaneLayout")
            }

            // Add listener to handle post-back-stack state
            supportFragmentManager.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
                override fun onBackStackChanged() {
                    val restoredFragment = supportFragmentManager.findFragmentById(R.id.application_container)
                    Log.d("ApplicationActivity", "Back stack changed, restored fragment = ${restoredFragment?.javaClass?.simpleName}")
                    // Re-sync currentDetailTag from the visible chat fragments
                    syncCurrentDetailTag()
                    val restoredDetailFragment = currentDetailTag?.let { supportFragmentManager.findFragmentByTag(it) }
                    if (!isPortrait && restoredDetailFragment is ChatView) {
                        binding.slidingPaneLayout.openPane()
                    }
                    supportFragmentManager.removeOnBackStackChangedListener(this)
                }
            })
        } else if (currentFragment !is ChatListView) {
            Log.d("ApplicationActivity", "Replacing with ChatListFragment")
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.application_container, ChatListView())
                addToBackStack("chat_list_root")
            }
            closeDetail()
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            binding.toolbarNav.isVisible = true
            setupNavigationDrawer()
        } else {
            Log.d("ApplicationActivity", "Already on ChatListFragment, closing detail")
            closeDetail()
        }
    }


    override fun closeDetail() {
        val tag = currentDetailTag
        val fm = supportFragmentManager
        when {
            tag != null -> {
                // Slide pane closed first, then hide/remove fragment after animation
                val isChat = tag.startsWith(CHAT_TAG_PREFIX)
                currentDetailTag = null
                if (binding.slidingPaneLayout.isOpen) {
                    binding.slidingPaneLayout.close()
                    val frag = fm.findFragmentByTag(tag)
                    if (frag != null && !frag.isHidden) {
                        binding.slidingPaneLayout.postDelayed({
                            if (!isDestroyed && !isFinishing) {
                                // Chat fragments are hidden (kept for reuse); others are removed
                                fm.commit { if (isChat) hide(frag) else remove(frag) }
                            }
                        }, 500)
                    }
                } else {
                    fm.findFragmentByTag(tag)?.let { frag ->
                        if (!frag.isHidden) fm.commit { if (isChat) hide(frag) else remove(frag) }
                    }
                }
            }
            else -> {
                // Fallback for fragments opened outside our tag tracking
                val detailFrag = fm.findFragmentById(R.id.detail_container)
                if (detailFrag != null) {
                    fm.beginTransaction().remove(detailFrag).commit()
                    if (binding.slidingPaneLayout.isOpen) binding.slidingPaneLayout.close()
                } else if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                }
            }
        }
    }
    override fun close() {
        if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
    }

    override fun closePanel() {
        if (binding.slidingPaneLayout.isOpen) binding.slidingPaneLayout.close()

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
        val chatId = chatParams.id
        val newTag = chatTag(chatId)
        val fm = supportFragmentManager
        val existingFrag = fm.findFragmentByTag(newTag)
        val currentVisible = currentDetailTag?.let { fm.findFragmentByTag(it) }

        fm.commit {
            setReorderingAllowed(true)
            // Hide whatever is currently visible in the detail pane
            if (currentVisible != null && currentVisible !== existingFrag) {
                hide(currentVisible)
            }
            if (existingFrag != null) {
                // Chat is cached — bring it back to the front
                show(existingFrag)
                chatFragmentStack.remove(chatId)
                chatFragmentStack.addLast(chatId)
            } else {
                // New chat — add it to the container
                add(R.id.detail_container, ChatView.newInstance(chatParams), newTag)
                chatFragmentStack.addLast(chatId)
                // Evict the oldest entry when the stack exceeds the limit
                if (chatFragmentStack.size > MAX_CHAT_STACK) {
                    val evictedId = chatFragmentStack.removeFirst()
                    fm.findFragmentByTag(chatTag(evictedId))?.let { remove(it) }
                }
            }
        }
        currentDetailTag = newTag
        binding.slidingPaneLayout.openPane()
    }


    override fun showReorderAccountsFragment() {
        launchDetail(ReorderAccountsFragment())
    }

    override fun showNewChat() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation

        if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
            (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)
        ) {
            val newChat = NewChatFragment()
            newChat.show(supportFragmentManager, "New Chat")

        } else launchDetail(NewChatFragment())
    }
    override fun showNewContact() {
        launchDetailInStack(NewContactFragment.newInstance())
    }

    override fun showNewGroup(incognito: Boolean) {
        launchDetailInStack(NewGroupFragment.newInstance(incognito))
    }

    override fun showChatFragment() {
        launchDetailInStack(ChatListView())
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
        launchDetailInStack(ContactAccountFragment.newInstance(params))
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

        launchDetailInStack(ChatListToForwardFragment.newInstance(forwardMessage, jid))
    }

    override fun showStatusFragment() {
        launchDetailInStack(StatusFragment())
    }

    override fun showChatInStack(chatParams: ChatParams) {
        launchDetailInStack(ChatView.newInstance(chatParams))
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
        // keyboard insets handled by setupEdgeToEdge()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            CHAT_LIST_UNREAD_KEY,
            viewModel.showUnreadOnly
        )
        outState.putStringArrayList(KEY_CHAT_STACK, ArrayList(chatFragmentStack))
        currentDetailTag?.let { outState.putString(KEY_CURRENT_DETAIL_TAG, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentActivity == this) {
            currentActivity = null
        }
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
