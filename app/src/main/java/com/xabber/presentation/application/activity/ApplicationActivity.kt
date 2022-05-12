package com.xabber.presentation.application.activity

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.xabber.R
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.fragments.account.AccountFragment
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
import com.xabber.presentation.onboarding.activity.OnBoardingActivity

class ApplicationActivity : AppCompatActivity(), ApplicationNavigator {
    private val isSignedIn = true
    private val binding: ActivityApplicationBinding by lazy {
        ActivityApplicationBinding.inflate(
            layoutInflater
        )
    }

    private val activeFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.application_container)

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


    fun setContainerWidth() {

//val  displayMetric =getResources().getDisplayMetrics();
//val  screenWidthInDp = displayMetric.widthPixels/displayMetric.density;

        val widthPx = Resources.getSystem().displayMetrics.widthPixels
        val density = Resources.getSystem().displayMetrics.density

        Log.d("iiii", "px = $widthPx, density = $density")
        binding.mainContainer.updateLayoutParams<SlidingPaneLayout.LayoutParams> {
            this.width = 240 * 2
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (isSignedIn) {

            if (savedInstanceState == null) {
                launchFragment(ChatFragment.newInstance(""))
            }
        } else {
            val intent = Intent(this, OnBoardingActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(R.animator.appearance, R.animator.disappearance)
        }
        setContainerWidth()
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentListener, false)
        initBottomNavigation()

    }


    private fun updateUi() {

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            binding.bottomNavBar.isVisible = false
            binding.shadow.isVisible = false
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setDisplayShowHomeEnabled(false)

            binding.bottomNavBar.isVisible = true
            binding.shadow.isVisible = true
        }
    }


    private fun initBottomNavigation() {
        binding.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> {
                    if (activeFragment !is ChatFragment) {
                        launchFragment(ChatFragment.newInstance("name.surname@redsolution.com"))
                    } else {

                        val widthPx = Resources.getSystem().displayMetrics.widthPixels
                        val density = Resources.getSystem().displayMetrics.density

                        Log.d("iiii", "px = $widthPx, density = $density")

                        Toast.makeText(this, "$density", Toast.LENGTH_SHORT).show()
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

    private fun openDetail(fragment: Fragment) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.detail_container, fragment).addToBackStack(null)
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
        openDetail(MessageFragment.newInstance(jid))
    }

    override fun showAccount() {
        openDetail(AccountFragment())
    }

    override fun showContacts() {
        launchFragment(ContactsFragment())
    }

    override fun showNewChat() {
        openDetail(NewChatFragment())
    }

    override fun showNewContact() {
        launchDetailInStack(NewContactFragment())
    }

    override fun showNewGroup(incognito: Boolean) {
        launchDetailInStack(NewGroupFragment.newInstance(incognito))
    }

    override fun showSpecialNotificationSettings() {
        openDetail(SpecialNotificationsFragment())
    }

    override fun showEditContact(name: String) {
        openDetail(EditContactFragment.newInstance(name))
    }

    override fun showChatSettings() {
        openDetail(ChatSettingsFragment())
    }

    override fun closeDetail() {
        supportFragmentManager.beginTransaction()
            .remove(supportFragmentManager.findFragmentById(R.id.detail_container)!!).commit();
          binding.slidingPaneLayout.closePane()
    }

    override fun onDestroy() {
        super.onDestroy()
    }


}


