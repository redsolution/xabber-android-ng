package com.xabber.onboarding.activity

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.xabber.*
import com.xabber.application.activity.ApplicationActivity
import com.xabber.onboarding.fragments.start.StartFragment
import com.xabber.onboarding.fragments.signin.SigninFragment
import com.xabber.databinding.ActivityOnboardingBinding
import com.xabber.onboarding.contract.Navigator
import com.xabber.onboarding.contract.ResultListener
import com.xabber.onboarding.contract.ToolbarChanger
import com.xabber.onboarding.fragments.signup.*

class OnBoardingActivity : AppCompatActivity(), Navigator, ToolbarChanger {
    private var binding: ActivityOnboardingBinding? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        setSupportActionBar(binding?.onboardingToolbar)
        if (savedInstanceState == null) addStartFragment()
    }

    private fun addStartFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.onboarding_container, StartFragment()
        ).commit()

    }

    private fun launchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().addToBackStack(null)
            .replace(R.id.onboarding_container, fragment).commit()
    }


    override fun startSignupNicknameFragment() {
        launchFragment(SignupNicknameFragment())
    }

    override fun startSignupUserNameFragment() {
        launchFragment(SignupUserNameFragment())
    }

    override fun startSignupPasswordFragment() {
        launchFragment(SignupPasswordFragment.newInstance(UserParams("cat", "hhh")))
    }

    override fun startSignupAvatarFragment() {
        launchFragment(SignupAvatarFragment())
    }

    override fun startSigninFragment() {
       launchFragment(SigninFragment())
    }


    override fun goApplicationActivity(userName: String) {
        val intent = Intent(this, ApplicationActivity::class.java)
        intent.putExtra("key", userName)
        startActivity(intent)
        finish()
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun <T : Parcelable> showResult(result: T) {

    }

    override fun <T : Parcelable> giveResult(
        clazz: Class<T>,
        owner: LifecycleOwner,
        listener: ResultListener<T>
    ) {

    }

    override fun setTitle(titleResId: Int) {
        binding?.onboardingToolbar?.setTitle(titleResId)
    }

    override fun clearTitle() {
        binding?.onboardingToolbar?.setTitle("")
    }

    override fun setShowBack(isVisible: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(isVisible)
        supportActionBar?.setDisplayShowHomeEnabled(isVisible)
    }
}