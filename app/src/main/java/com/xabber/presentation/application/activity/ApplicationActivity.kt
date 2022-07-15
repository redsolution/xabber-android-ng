package com.xabber.presentation.application.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Surface
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.data.xmpp.account.Account
import com.xabber.data.xmpp.account.AccountStorageItem
import com.xabber.data.xmpp.presences.ResourceStorageItem
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.account.ReorderAccountsFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatViewModel
import com.xabber.presentation.application.fragments.chatlist.*
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.fragments.contacts.EditContactFragment
import com.xabber.presentation.application.fragments.contacts.NewContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.settings.SettingsFragment
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.dp
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.query

class ApplicationActivity : AppCompatActivity(), ApplicationNavigator {
    private val binding: ActivityApplicationBinding by lazy {
        ActivityApplicationBinding.inflate(
            layoutInflater
        )
    }

    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)
    private val viewModel: ApplicationViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private var recordAudioPermissionGranted = false
    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotRecordAudioPermissionResult
    )


    private fun onGotRecordAudioPermissionResult(granted: Boolean) {
        return if (granted) {
            recordAudioPermissionGranted = true
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                recordAudioPermissionGranted = false
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            } else {
                recordAudioPermissionGranted = false
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val maskName = getSharedPreferences(
            AppConstants.MASK_KEY,
            Context.MODE_PRIVATE
        ).getString(AppConstants.MASK_KEY, "circle")
        val mask = when (maskName) {
            "Circle" -> Mask.Circle
            "Hexagon" -> Mask.Hexagon
            "Pentagon" -> Mask.Pentagon
            "Squircle" -> Mask.Squircle
            "Octagon" -> Mask.Octagon
            "Rounded" -> Mask.Rounded
            else -> Mask.Star
        }
        Log.d("shared", "$mask")
        UiChanger.setMask(mask)
        if (true) {
            if (getWidthWindowSizeClass() == WidthWindowSize.MEDIUM || getWidthWindowSizeClass() == WidthWindowSize.EXPANDED) setContainerWidth()
            if (savedInstanceState == null) {
                launchFragment(ChatListFragment.newInstance(""))
            } else {
                val unreadMessagesCount =
                    savedInstanceState.getInt(AppConstants.UNREAD_MESSAGES_COUNT)
                showBadge(unreadMessagesCount)
            }
        } else {
            val intent = Intent(this, OnBoardingActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.appearance, R.anim.disappearance)
        }
        subscribeViewModelData()
        initBottomNavigation()

    }

    private fun checkUserIsRegister(): Boolean {
        val config =
            RealmConfiguration.Builder(setOf(AccountStorageItem::class, ResourceStorageItem::class))
                .build()
        val realm = Realm.open(config)
        val accountCollection = realm
            .query<AccountStorageItem>()
            .find()
        val comparsionResult = accountCollection.size > 0
        realm.close()
        return comparsionResult
    }

    private fun getWidthWindowSizeClass(): WidthWindowSize {
        val widthDp =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val widthWindowSize = when {
            widthDp >= 900f -> WidthWindowSize.EXPANDED
            widthDp >= 600f && widthDp < 900f -> WidthWindowSize.MEDIUM
            else -> WidthWindowSize.COMPACT
        }
        return widthWindowSize
    }

    private fun setContainerWidth() {
        val widthPx = Resources.getSystem().displayMetrics.widthPixels
        val density = Resources.getSystem().displayMetrics.density
        val widthDp = widthPx / density

        binding.mainContainer.updateLayoutParams<SlidingPaneLayout.LayoutParams> {
            val maxSize = 400
            val newWidth = (widthDp / 100 * 40.dp).toInt()
            this.width =
                if (Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && getWidthWindowSizeClass() == WidthWindowSize.EXPANDED) {
                    val landWidth = (widthDp / 100 * 40.dp).toInt()
                    if (landWidth > maxSize) maxSize else landWidth
                } else
                    newWidth
        }
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
                        launchFragment(ChatListFragment.newInstance("name.surname@redsolution.com"))
                    } else {
                        if (viewModel.unreadCount.value != null && viewModel.unreadCount.value != 0) {
                            menuItem.setIcon(R.drawable.ic_chat_alert)
                        }
                    }
                }
                R.id.calls -> if (activeFragment !is CallsFragment) launchFragment(CallsFragment())
                R.id.contacts -> if (activeFragment !is ContactsFragment) launchFragment(
                    ContactsFragment()
                )
                R.id.discover -> if (activeFragment !is DiscoverFragment) launchFragment(
                    DiscoverFragment()
                )
                R.id.settings -> if (activeFragment !is SettingsFragment) launchFragment(
                    SettingsFragment()
                )
            }
            true
        }
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

    override fun showMessage(jid: String) {
        launchDetail(ChatFragment.newInstance(jid))
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

    override fun showEditContact(name: String) {
        launchDetailInStack(EditContactFragment.newInstance(name))
    }

    override fun showChatSettings() {
        launchDetail(ChatSettingsFragment())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.unreadCount.value?.let { outState.putInt(AppConstants.UNREAD_MESSAGES_COUNT, it) }
    }

    @SuppressLint("CommitPrefEdits")
    override fun onDestroy() {
        super.onDestroy()
        requestRecordAudioPermissionLauncher.unregister()
        getSharedPreferences(AppConstants.MASK_KEY, Context.MODE_PRIVATE).edit()
            .putString(AppConstants.MASK_KEY, UiChanger.getMask().name).apply()
        Log.d("shared", "${UiChanger.getMask().name}")
    }

    override fun requestPermissionToRecord(): Boolean {
        requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        return recordAudioPermissionGranted
    }


    override fun slidingPaneLayoutIsOpen(): Boolean = binding.slidingPaneLayout.isOpen


    override fun lockScreenRotation(isLock: Boolean) {
        this.requestedOrientation =
            if (isLock) {
                val display: Display = this.windowManager.defaultDisplay
                val rotation = display.rotation
                val size = Point()
                display.getSize(size)
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                    if (size.x > size.y) {
                        //rotation is 0 or 180 deg, and the size of x is greater than y,
                        //so we have a tablet
                        if (rotation == Surface.ROTATION_0) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        }
                    } else {
                        //rotation is 0 or 180 deg, and the size of y is greater than x,
                        //so we have a phone
                        if (rotation == Surface.ROTATION_0) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        }
                    }
                } else {
                    if (size.x > size.y) {
                        //rotation is 90 or 270, and the size of x is greater than y,
                        //so we have a phone
                        if (rotation == Surface.ROTATION_90) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        }
                    } else {
                        //rotation is 90 or 270, and the size of y is greater than x,
                        //so we have a tablet
                        if (rotation == Surface.ROTATION_90) {
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                }
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
    }
}


