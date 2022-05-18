package com.xabber.presentation.application.activity

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.xabber.R
import com.xabber.data.util.dp
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.contract.ApplicationToolbarChanger
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.*
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.fragments.contacts.EditContactFragment
import com.xabber.presentation.application.fragments.contacts.NewContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.message.MessageFragment
import com.xabber.presentation.application.fragments.settings.SettingsFragment
import com.xabber.presentation.onboarding.activity.OnBoardingActivity

class ApplicationActivity : AppCompatActivity(), ApplicationNavigator {
    private val isSignedIn = true
    private val viewModel: ApplicationViewModel by viewModels()
    private var isShowUnreadMessages = false
    private var count = 0
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

        if (isSignedIn) {
            val widthWindowSize = getWidthWindowSizeClass()
            val heightWindowSize = getHeightWindowSizeClass()
            if (Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (heightWindowSize == HeightWindowSize.MEDIUM || heightWindowSize == HeightWindowSize.EXPANDED) setContainerWidth()
            } else if (widthWindowSize == WidthWindowSize.MEDIUM || widthWindowSize == WidthWindowSize.EXPANDED) setContainerWidth()
            if (savedInstanceState == null) {
                launchFragment(ChatFragment.newInstance(""))
            } else {
                isShowUnreadMessages = savedInstanceState.getBoolean("showUnread")
                Log.d("Saved", "пришло $isShowUnreadMessages")
                count = savedInstanceState.getInt("unreadCount")
                binding.groupUnraedMessages.isVisible = isShowUnreadMessages
                binding.unreadAllMessagesCount.text = count.toString()
            }
        } else {
            val intent = Intent(this, OnBoardingActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(R.animator.appearance, R.animator.disappearance)
        }
        viewModel.unreadCount.observe(this) {
            count = it
            binding.groupUnraedMessages.isVisible = it > 0
            binding.unreadAllMessagesCount.text = it.toString()
            if (it < 1) binding.bottomNavBar.menu.getItem(0).setIcon(R.drawable.ic_material_chat_24)
        }

        initBottomNavigation()

    }

    fun getWidthWindowSizeClass(): WidthWindowSize {
        val widthDp =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val widthWindowSize = when {
            widthDp >= 900f -> WidthWindowSize.EXPANDED
            widthDp >= 600f && widthDp < 900f -> WidthWindowSize.MEDIUM
            else -> WidthWindowSize.COMPACT
        }
        return widthWindowSize
    }

    fun getHeightWindowSizeClass(): HeightWindowSize {
        val heightDp =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val heightWindowSizeClass = when {
            heightDp < 480f -> HeightWindowSize.COMPACT
            heightDp < 900f -> HeightWindowSize.MEDIUM
            else -> HeightWindowSize.EXPANDED
        }
        return heightWindowSizeClass
    }


    private fun setContainerWidth() {
        if (getWidthWindowSizeClass() == WidthWindowSize.MEDIUM || getWidthWindowSizeClass() == WidthWindowSize.EXPANDED) {
            val widthPx = Resources.getSystem().displayMetrics.widthPixels
            val density = Resources.getSystem().displayMetrics.density
            val widthDp = widthPx / density

            binding.mainContainer.updateLayoutParams<SlidingPaneLayout.LayoutParams> {
                val maxSize = 400
                val newWidth = (widthDp / 100 * 40.dp).toInt()
                this.width =
                    if (Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        val landWidth = (widthDp / 100 * 40.dp).toInt()
                        if (landWidth > maxSize) maxSize else landWidth
                    } else
                        newWidth
            }
        }
    }

    private fun initBottomNavigation() {
        binding.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> {
                    if (activeFragment !is ChatFragment) {
                        launchFragment(ChatFragment.newInstance("name.surname@redsolution.com"))
                    } else {
                        if (count > 0) {
                            isShowUnreadMessages = !isShowUnreadMessages
                            viewModel.setShowUnreadValue(isShowUnreadMessages)
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun goBack() {
        onBackPressed()
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


    override fun closeDetail() {
        Log.d("Saved", "${supportFragmentManager.backStackEntryCount}")
        if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
        else {
            supportFragmentManager.beginTransaction()
                .remove(supportFragmentManager.findFragmentById(R.id.detail_container)!!).commit()
            if (binding.slidingPaneLayout.isOpen) binding.slidingPaneLayout.close()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("Saved", "ушло $isShowUnreadMessages")
        viewModel.showUnread.value?.let { outState.putBoolean("showUnread", it) }
        viewModel.unreadCount.value?.let { outState.putInt("unreadCount", it) }
    }
}


