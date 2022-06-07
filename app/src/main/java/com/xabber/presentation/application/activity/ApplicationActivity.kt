package com.xabber.presentation.application.activity

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.data.util.dp
import com.xabber.data.xmpp.account.AccountStorageItem
import com.xabber.data.xmpp.presences.ResourceStorageItem
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chatlist.*
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.fragments.contacts.EditContactFragment
import com.xabber.presentation.application.fragments.contacts.NewContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.message.MessageFragment
import com.xabber.presentation.application.fragments.settings.SettingsFragment
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.query

class ApplicationActivity : AppCompatActivity(), ApplicationNavigator {
    private val viewModel: ApplicationViewModel by viewModels()

    //  private val messageViewModel: MessageViewModel by viewModels()
    private var bitmap: Bitmap? = null
    private val binding: ActivityApplicationBinding by lazy {
        ActivityApplicationBinding.inflate(
            layoutInflater
        )
    }

    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (checkUserIsRegister()) {
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
            overridePendingTransition(R.animator.appearance, R.animator.disappearance)
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


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun showChatFragment() {
        if (slidingPaneLayoutIsOpen()) launchDetailInStack(ChatListFragment())
        Log.d("sliding", "activity ${slidingPaneLayoutIsOpen()}")

    }

    override fun showMessage(jid: String) {
        launchDetail(MessageFragment.newInstance(jid))
    }

    override fun showAccount() {
        launchDetail(AccountFragment())
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
        launchDetail(EditContactFragment.newInstance(name))
    }

    override fun showChatSettings() {
        launchDetail(ChatSettingsFragment())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.unreadCount.value?.let { outState.putInt(AppConstants.UNREAD_MESSAGES_COUNT, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        //  requestCameraPermissionLauncher.unregister()

    }


    override fun slidingPaneLayoutIsOpen(): Boolean = binding.slidingPaneLayout.isOpen
}


