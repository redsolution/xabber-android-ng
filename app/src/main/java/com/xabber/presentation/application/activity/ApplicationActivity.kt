package com.xabber.presentation.application.activity

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.xabber.R
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.account.TestFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatSettingsFragment
import com.xabber.presentation.application.fragments.chat.NewGroupFragment
import com.xabber.presentation.application.fragments.chat.SpecialNotificationsFragment
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.fragments.contacts.EditContactFragment
import com.xabber.presentation.application.fragments.contacts.NewContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.message.MessageFragment
import com.xabber.presentation.application.fragments.message.NewChatFragment
import com.xabber.presentation.application.fragments.settings.SettingsFragment
import com.xabber.presentation.application.util.WindowSize


class ApplicationActivity : AppCompatActivity(), ApplicationNavigator {

    private var binding: ActivityApplicationBinding? = null
    lateinit var userName: String
    private val activeFragment: Fragment
        get() = supportFragmentManager.findFragmentById(R.id.application_container)!!

    private val fragmentListener = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) {
            super.onFragmentViewCreated(fm, f, v, savedInstanceState)
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplicationBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        if (savedInstanceState == null) launchFragment(ChatFragment())
        //      launchFragment(TestFragment())
        initBottomNavigation()
    }


    fun getWidthWindowType(): WindowSize {
        val widthDp =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val widthWindowSize = when {
            widthDp >= 900f -> WindowSize.EXPANDED
            widthDp >= 600f && widthDp < 900f -> WindowSize.MEDIUM
            else -> WindowSize.COMPACT
        }
        return widthWindowSize
    }


    fun setContainersWidth() {
        //   binding.detailsContentContainerWrapper.updateLayoutParams<ConstraintLayout.LayoutParams> {
        //      this.horizontalWeight =
        when (getWidthWindowType()) {
            WindowSize.EXPANDED -> 6F
            WindowSize.MEDIUM -> 6F
            WindowSize.COMPACT -> 0F
        }

    }

    private fun updateUi() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)

            binding?.bottomNavBar?.isVisible = false
            binding?.shadow?.isVisible = false

        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)

            binding?.bottomNavBar?.isVisible = true
            binding?.shadow?.isVisible = true
        }
    }

    private fun initBottomNavigation() {
        binding!!.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> {
                    when (resources.configuration.orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> binding?.container?.isVisible = false
                        Configuration.ORIENTATION_LANDSCAPE -> binding?.container?.isVisible = true
                    }
                    if (activeFragment !is ChatFragment) {
                        launchFragment(ChatFragment.newInstance("name.surname@redsolution.com"))
                    } else {
                        Toast.makeText(this, "Button press", Toast.LENGTH_SHORT).show()
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
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance)
            .replace(R.id.application_container, fragment).commit()
    }

    private fun launchFragmentInStack(fragment: Fragment) {
        supportFragmentManager.beginTransaction().setCustomAnimations(
            R.animator.in_right,
            R.animator.out_left,
            R.animator.in_left,
            R.animator.out_right
        ).replace(R.id.application_container, fragment).addToBackStack(null).commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun showMessage(jid: String) {
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> launchFragmentInStack(
                MessageFragment.newInstance(
                    jid
                )
            )
            Configuration.ORIENTATION_LANDSCAPE -> {
                binding?.container?.isVisible = true
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, MessageFragment.newInstance(jid)).commit()
            }
        }

    }

    override fun showAccount() {
        launchFragmentInStack(AccountFragment())
    }

    override fun showContacts() {
        launchFragment(ContactsFragment())
    }

    override fun showNewChat() {
        launchFragmentInStack(NewChatFragment())
    }

    override fun showNewContact() {
        launchFragmentInStack(NewContactFragment())
    }

    override fun showNewGroup(incognito: Boolean) {
        launchFragmentInStack(NewGroupFragment.newInstance(incognito))
    }

    override fun showSpecialNotificationSettings() {
        launchFragmentInStack(SpecialNotificationsFragment())
    }

    override fun showEditContact(name: String) {
        launchFragmentInStack(EditContactFragment.newInstance(name))
    }

    override fun showChatSettings() {
        launchFragmentInStack(ChatSettingsFragment())
    }

    override fun hideFragment(isVisible: Boolean) {
        binding?.container?.isVisible = isVisible
    }


}
