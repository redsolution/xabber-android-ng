package com.xabber.presentation.application.activity

import android.graphics.*
import android.graphics.BlurMaskFilter.Blur
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.xabber.R
import com.xabber.data.dto.ChatDto
import com.xabber.databinding.ActivityApplicationBinding
import com.xabber.presentation.application.contract.ApplicationNavigator
import com.xabber.presentation.application.contract.ApplicationToolbarChanger
import com.xabber.presentation.application.contract.FragmentAction
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.calls.CallsFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.NewGroupFragment
import com.xabber.presentation.application.fragments.chat.SpecialNotificationsFragment
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.fragments.contacts.EditContactFragment
import com.xabber.presentation.application.fragments.contacts.NewContactFragment
import com.xabber.presentation.application.fragments.discover.DiscoverFragment
import com.xabber.presentation.application.fragments.message.MessageFragment
import com.xabber.presentation.application.fragments.message.NewChatFragment
import com.xabber.presentation.application.fragments.settings.SettingsFragment
import com.xabber.presentation.onboarding.contract.ResultListener


class ApplicationActivity : AppCompatActivity(), ApplicationNavigator, ApplicationToolbarChanger {

    private var binding: ActivityApplicationBinding? = null
    lateinit var userName: String
    private val activeFragment: Fragment
        get() = supportFragmentManager.findFragmentById(R.id.application_container)!!

    private val fragmentListner = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) {
            super.onFragmentViewCreated(fm, f, v, savedInstanceState)
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplicationBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

//setSupportActionBar(binding?.applicationToolbar)
        userName = intent.getStringExtra("key").toString()
        if (savedInstanceState == null) {
            startChatFragment()
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentListner, false)
        initToolbar()
        initBottomNavigation()
    }

    private fun initToolbar() {
        //  binding?.imSearch?.setOnClickListener {
        //      binding?.searchView?.visibility = View.VISIBLE
        //      binding?.searchView?.isIconified = false
        //      binding?.searchView?.onActionViewExpanded()


        //      binding?.imBack?.visibility = View.VISIBLE
        //      binding?.applicationToolbar?.setBackgroundColor(resources.getColor(R.color.white))
        //      binding?.imSearch?.visibility = View.GONE
        //      binding?.imPlus?.visibility = View.GONE
        //       binding?.avatarContainer?.visibility = View.GONE
        //       binding?.tvTitle?.visibility = View.GONE
        //      binding?.avatarStatus?.visibility = View.GONE


        //  }

        //    binding?.imBack?.setOnClickListener {
        //       binding?.searchView?.visibility = View.GONE
        //       binding?.imBack?.visibility = View.GONE
        //        binding?.imSearch?.visibility = View.VISIBLE
        //        binding?.imPlus?.visibility = View.VISIBLE
        //       binding?.avatarContainer?.visibility = View.VISIBLE
        //      binding?.tvTitle?.visibility = View.VISIBLE
        //      binding?.avatarStatus?.visibility = View.VISIBLE
        //      binding?.applicationToolbar?.setBackgroundColor(resources.getColor(R.color.blue_300))
        //  binding?.shadowToolbar?.visibility = View.GONE
    }

    //    binding?.imPlus?.setOnClickListener {
    //        binding?.bottomNavBar?.visibility = View.GONE
    //         goToMessage()
    //     }


    private fun initBottomNavigation() {
        binding!!.bottomNavBar.setOnItemSelectedListener { menuItem ->
            val fragment = activeFragment
            when (menuItem.itemId) {
                R.id.chats -> launchFragment(ChatFragment.newInstance(""))
                R.id.calls -> launchFragment(CallsFragment())
                R.id.contacts -> launchFragment(ContactsFragment())
                R.id.discover -> launchFragment(DiscoverFragment())
                R.id.settings -> launchFragment(SettingsFragment())
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
        binding?.bottomNavBar?.visibility = View.VISIBLE
    }

    private fun launchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance)
            .replace(R.id.application_container, fragment).commit()
    }

    private fun launchFragmentInStack(fragment: Fragment) {

    }

    override fun goBack() {
        onBackPressed()
    }


    override fun goToMessage(chat: ChatDto) {
        supportFragmentManager.beginTransaction().replace(
            R.id.application_container, MessageFragment()
        ).addToBackStack(null).commit()
    }

    override fun goToAccount() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(
                null
            )
            .replace(R.id.application_container, AccountFragment()).commit()
    }

    override fun goToNewMessage() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(
                null
            )
            .replace(R.id.application_container, NewChatFragment()).commit()
    }

    override fun startNewContactFragment() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(
                null
            )
            .replace(R.id.application_container, NewContactFragment()).commit()
    }

    override fun startNewGroupFragment(incognito: Boolean) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(
                null
            )
            .replace(R.id.application_container, NewGroupFragment.newInstance(incognito)).commit()
    }

    override fun startSpecialNotificationsFragment() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(
                null
            )
            .replace(R.id.application_container, SpecialNotificationsFragment()).commit()
    }

    override fun startEditContactFragment() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(
                null
            )
            .replace(R.id.application_container, EditContactFragment()).commit()
    }

    override fun <T : Parcelable> showResult(result: T) {

    }

    override fun <T : Parcelable> giveResult(
        clazz: Class<T>,
        owner: LifecycleOwner,
        listener: ResultListener<T>
    ) {

    }

    override fun setShowBack(isVisible: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(isVisible)
        supportActionBar?.setDisplayShowHomeEnabled(isVisible)
    }

    override fun setTitle(titleResId: Int) {
        //      binding?.tvTitle?.setText(titleResId)
    }

    override fun showNavigationView(isShow: Boolean) {
        binding?.bottomNavBar?.isVisible = isShow
        binding?.shadow?.isVisible = isShow
    }

    override fun toolbarIconChange(fragmentAction: FragmentAction) {
        //    binding?.applicationToolbar?.menu?.clear()
        val iconDrawable =
            DrawableCompat.wrap(ContextCompat.getDrawable(this, fragmentAction.iconRes)!!)

    }

    override fun changeToolbar(toolbar: androidx.appcompat.widget.Toolbar) {

        setSupportActionBar(toolbar)
    }

    override fun startAccountFragment() {
  supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(
                null
            )
            .replace(R.id.application_container, AccountFragment()).commit()
    }

}
