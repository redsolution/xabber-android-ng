package com.xabber.presentation.application.activity

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.xabber.R
import com.xabber.data.util.dp
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
import com.xabber.presentation.application.util.WindowSize
import com.xabber.presentation.onboarding.activity.OnBoardingActivity


class ApplicationActivity : AppCompatActivity(), ApplicationNavigator {
    private val isSignedIn = true
    private var binding: ActivityApplicationBinding? = null
    lateinit var userName: String
    private val activeFragment: Fragment
        get() = supportFragmentManager.findFragmentById(R.id.application_container)!!

    //   private val fragmentListener = object : FragmentManager.FragmentLifecycleCallbacks() {
    //    override fun onFragmentViewCreated(
    //       fm: FragmentManager,
    //      f: Fragment,
    //     v: View,
    //     savedInstanceState: Bundle?
    //  ) {
    //       super.onFragmentViewCreated(fm, f, v, savedInstanceState)
    //       updateUi()
    //   }
    //  }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isSignedIn) {
            val intent = Intent(this, OnBoardingActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivityForResult(intent, 1)
          //  finish()
          //  overridePendingTransition(0, 0)
        } else {
            if (savedInstanceState == null) launchFragment(ChatFragment())
            binding = ActivityApplicationBinding.inflate(layoutInflater)
            setContentView(binding!!.root)
        }
        //   setContainersWidth()


        //     if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || getWidthWindowType() != WindowSize.COMPACT) {
        //                   supportFragmentManager.beginTransaction().replace(R.id.detail_container, MessageFragment.newInstance("")).commit()
        //               }
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
        binding?.detailContainer?.isVisible = true
        binding?.applicationContainer?.updateLayoutParams<ConstraintLayout.LayoutParams> {
            this.width =
                when (getWidthWindowType()) {
                    WindowSize.EXPANDED -> 360.dp
                    WindowSize.MEDIUM -> 0.dp
                    WindowSize.COMPACT -> 0.dp
                }
        }
    }

    fun hideDetailContainer() {
        binding?.applicationContainer?.updateLayoutParams<ConstraintLayout.LayoutParams> {
            this.width = 0.dp
        }
        binding?.detailContainer?.isVisible = false
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
