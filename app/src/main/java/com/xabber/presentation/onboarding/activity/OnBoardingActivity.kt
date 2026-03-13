package com.xabber.presentation.onboarding.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.ActivityOnboardingBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.onboarding.contract.OnboardingNavigator
import com.xabber.presentation.onboarding.contract.ToolbarChanger
import com.xabber.presentation.onboarding.fragments.connectionprogress.ConnectionProgressFragment
import com.xabber.presentation.onboarding.fragments.signin.SigninFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupAvatarFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupNicknameFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupPasswordFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupUserNameFragment
import com.xabber.presentation.onboarding.fragments.start.StartFragment

/** OnBoarding Activity allows the user to log in or register in the application
 *  This action only works in portrait mode (see manifest)
 */

class OnBoardingActivity : AppCompatActivity(), OnboardingNavigator, ToolbarChanger {
    private val binding: ActivityOnboardingBinding by lazy {
        ActivityOnboardingBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge()
        binding.onboardingToolbar.title = ""
        setSupportActionBar(binding.onboardingToolbar)
        if (savedInstanceState == null) launchStartFragment()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            windowInsets
        }
    }

    private fun launchStartFragment() {
        supportFragmentManager.commit {
            replace(R.id.onboarding_container, StartFragment())
        }
    }

    override fun setTitle(titleResId: Int) {
        binding.onboardingToolbar.setTitle(titleResId)
    }

    override fun clearTitle() {
        binding.onboardingToolbar.title = ""
    }

    override fun showArrowBack(isVisible: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(isVisible)
        supportActionBar?.setDisplayShowHomeEnabled(isVisible)
    }

    override fun goBack() {
        supportFragmentManager.popBackStack()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.onboarding_container, fragment).addToBackStack(null)
        }
    }

    override fun openSignupNicknameFragment() {
        openFragment(SignupNicknameFragment())
    }

    override fun openSignupUserNameFragment() {
        openFragment(SignupUserNameFragment())
    }

    override fun openSignupPasswordFragment() {
        openFragment(SignupPasswordFragment())
    }

    override fun openSignupAvatarFragment() {
        openFragment(SignupAvatarFragment())
    }

    override fun openSigninFragment() {
        openFragment(SigninFragment())
    }

    override fun goToApplicationActivity() {
        val intent = Intent(applicationContext, ApplicationActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.appearance, R.anim.disappearance)
    }

    override fun openBottomSheetDialogFragment(dialog: BottomSheetDialogFragment) {
        dialog.show(supportFragmentManager, AppConstants.DIALOG_TAG)
    }

    override fun finishActivity() {
        finish()
    }


    override fun openConnectionProgressFragment(jid: String, username: String, password: String) {
        binding.root.postDelayed({
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,  // enter animation for new fragment
                    R.anim.slide_out_left,  // exit animation for current fragment
                    R.anim.slide_in_left,   // popEnter for when returning
                    R.anim.slide_out_right  // popExit for when returning
                )
                .setReorderingAllowed(true)
                .replace(R.id.onboarding_container, ConnectionProgressFragment.newInstance(jid, username, password))
                .addToBackStack(null)
                .commit()
        }, 200)
    }

}
