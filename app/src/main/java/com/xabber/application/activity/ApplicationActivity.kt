package com.xabber.application.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.xabber.R
import com.xabber.application.contract.HasChangeTitle
import com.xabber.application.fragments.ChatFragment
import com.xabber.databinding.ActivityApplicationBinding

class ApplicationActivity : AppCompatActivity() {

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
        setSupportActionBar(binding?.applicationToolbar)
        userName = intent.getStringExtra("key").toString()
        if (savedInstanceState == null) {
            startChatFragment()
        }
    }

    private fun updateUi() {
        val fragment = activeFragment
        if (fragment is HasChangeTitle) {
            binding?.applicationToolbar?.title = fragment.getTitle()
        } else {
            binding?.applicationToolbar?.title = "Xabber"
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setDisplayShowHomeEnabled(false)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentListener)
    }

    private fun startChatFragment() {
        supportFragmentManager.beginTransaction().add(
            R.id.application_container,
            ChatFragment.newInstance(userName)
        )
            .commit()
    }
}
