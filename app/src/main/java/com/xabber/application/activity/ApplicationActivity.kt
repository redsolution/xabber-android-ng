package com.xabber.application.activity

import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.xabber.R
import com.xabber.application.contract.ApplicationNavigator
import com.xabber.application.contract.ApplicationToolbarChanger
import com.xabber.application.fragments.calls.CallsFragment
import com.xabber.application.fragments.chat.ChatFragment
import com.xabber.application.fragments.contacts.ContactsFragment
import com.xabber.application.fragments.discover.DiscoverFragment
import com.xabber.application.fragments.message.MessageFragment
import com.xabber.application.fragments.settings.SettingsFragment
import com.xabber.databinding.ActivityApplicationBinding

class ApplicationActivity : AppCompatActivity(), ApplicationNavigator, ApplicationToolbarChanger {

    private var binding: ActivityApplicationBinding? = null
    lateinit var userName: String
    private val activeFragment: Fragment
        get() = supportFragmentManager.findFragmentById(R.id.application_container)!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplicationBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
//setSupportActionBar(binding?.applicationToolbar)
userName = intent.getStringExtra("key").toString()
        if (savedInstanceState == null) {
            startChatFragment()
        }

        initToolbar()
        initBottomNavigation()
    }

    private fun initToolbar() {
        binding?.imSearch?.setOnClickListener {
            binding?.searchView?.visibility = View.VISIBLE
            binding?.searchView?.isIconified = false
            binding?.searchView?.onActionViewExpanded()


            binding?.imBack?.visibility = View.VISIBLE
            binding?.applicationToolbar?.setBackgroundColor(resources.getColor(R.color.white))
            binding?.imSearch?.visibility = View.GONE
            binding?.imPlus?.visibility = View.GONE
            binding?.avatarContainer?.visibility = View.GONE
            binding?.tvTitle?.visibility = View.GONE
            binding?.avatarStatus?.visibility = View.GONE
           binding?.shadowToolbar?.visibility = View.VISIBLE


        }

        binding?.imBack?.setOnClickListener {
              binding?.searchView?.visibility = View.GONE
             binding?.imBack?.visibility = View.GONE
            binding?.imSearch?.visibility = View.VISIBLE
            binding?.imPlus?.visibility = View.VISIBLE
            binding?.avatarContainer?.visibility = View.VISIBLE
            binding?.tvTitle?.visibility = View.VISIBLE
            binding?.avatarStatus?.visibility = View.VISIBLE
            binding?.applicationToolbar?.setBackgroundColor(resources.getColor(R.color.blue_300))
          //  binding?.shadowToolbar?.visibility = View.GONE
        }

        binding?.imPlus?.setOnClickListener {
            binding?.bottomNavBar?.visibility = View.GONE
            goToMessage()
        }
    }

    private fun initBottomNavigation() {
        binding!!.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.chats -> startChatFragment()
                R.id.calls -> goToCalls()
                R.id.contacts -> goToContacts()
                R.id.discover -> goToDiscover()
                R.id.settings -> goToSettings()
            }
            true
        }
    }



    private fun startChatFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.application_container,
            ChatFragment.newInstance(userName)
        )
            .commit()
    }

    private fun launchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance)
            .replace(R.id.application_container, fragment).commit()
    }

    override fun goBack() {

    }


    override fun goToChat() {
        startChatFragment()
    }

    override fun goToCalls() {
        launchFragment(CallsFragment())
    }

    override fun goToContacts() {
        launchFragment(ContactsFragment())
    }

    override fun goToDiscover() {
        launchFragment(DiscoverFragment())

    }

    override fun goToSettings() {
        launchFragment(SettingsFragment())
    }

    override fun goToMessage() {
      supportFragmentManager.beginTransaction().replace(
          R.id.application_container, MessageFragment()).addToBackStack(null).commit()
    }

    override fun setShowBack(isVisible: Boolean) {

    }

    override fun setTitle(titleResId: Int) {
        binding?.tvTitle?.setText(titleResId)
    }
}
