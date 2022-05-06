package com.xabber.presentation.application.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
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
    private var isSignedIn = false
    private var binding: ActivityApplicationBinding? = null
    lateinit var userName: String
    private val activeFragment: Fragment
        get() = supportFragmentManager.findFragmentById(R.id.application_container)!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplicationBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        if (savedInstanceState == null) {launchFragment(ChatFragment())
         //   isSignedIn = true
         //   val intent = Intent(this, OnBoardingActivity::class.java)
        //    startActivity(intent)
        }
     //   else

        initBottomNavigation()
    }


    private fun initBottomNavigation() {
        binding!!.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> {
                    if (activeFragment !is ChatFragment) {
                        //     setContainersWidth()
                        launchFragment(ChatFragment.newInstance("name.surname@redsolution.com"))
                    } else {
                        Toast.makeText(this, "Button press", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.calls -> if (activeFragment !is CallsFragment) {
                    //  hideDetailContainer()
                    launchFragment(CallsFragment())
                }
                R.id.contacts -> if (activeFragment !is ContactsFragment) {
                    //    setContainersWidth()
                    launchFragment(
                        ContactsFragment()
                    )
                }
                R.id.discover -> if (activeFragment !is DiscoverFragment) {
                    //   hideDetailContainer()
                    launchFragment(
                        DiscoverFragment()
                    )
                }
                R.id.settings -> if (activeFragment !is SettingsFragment) {
                    //   hideDetailContainer()
                    launchFragment(
                        SettingsFragment()
                    )
                }
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
                supportFragmentManager.beginTransaction()
                    .replace(R.id.detail_container, MessageFragment.newInstance(jid)).commit()
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

    }


}
