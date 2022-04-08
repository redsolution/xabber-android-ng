package com.xabber.onboarding.activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.xabber.*
import com.xabber.application.activity.ApplicationActivity
import com.xabber.onboarding.fragments.StartFragment
import com.xabber.onboarding.fragments.SignInFragment
import com.xabber.onboarding.fragments.SignUpFragment
import com.xabber.databinding.ActivityOnboardingBinding

class OnBoardingActivity : AppCompatActivity(), Navigator {
    private var binding: ActivityOnboardingBinding? = null

    private val fragment: Fragment
        get() = supportFragmentManager.findFragmentById(R.id.container_auth)!!

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
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        setSupportActionBar(binding?.toolbarOnboarding)
        supportActionBar?.title = "Sign"
        if (savedInstanceState == null) startOnboardingFragment()
    }

    private fun updateUi() {
        val fr = fragment
        if (fr is ChoiceTitleToolbar) {
          binding?.toolbarOnboarding?.title = fr.getTitle()
        } else {
           binding?.toolbarOnboarding?.title = ""
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setDisplayShowHomeEnabled(false)
        }

    }

    private fun startOnboardingFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.container_auth, StartFragment()
        ).commit()

    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentListener)
    }

    override fun startSignUpFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.container_auth,
            SignUpFragment.newInstance()
        ).addToBackStack(null).commit()
    }

    override fun startSignInFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.container_auth,
            SignInFragment()
        ).addToBackStack(null).commit()
        Log.d("Xabber login", "Sign in")
    }

    override fun goMainActivity(userName: String) {
        val intent = Intent(this, ApplicationActivity::class.java)
        intent.putExtra("key", userName)
        startActivity(intent)
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun <T : Parcelable> showResult(result: T) {

    }

    override fun <T : Parcelable> giveResult(
        clazz: Class<T>,
        owner: LifecycleOwner,
        listener: ResultListener<T>
    ) {

    }
}