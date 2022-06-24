package com.xabber.presentation.onboarding.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.ActivityOnboardingBinding
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.onboarding.contract.Navigator
import com.xabber.presentation.onboarding.contract.ToolbarChanger
import com.xabber.presentation.onboarding.fragments.signin.SigninFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupAvatarFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupNicknameFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupPasswordFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupUserNameFragment
import com.xabber.presentation.onboarding.fragments.start.StartFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** OnBoarding Activity allows the user to log in or register in the application
 *
 */

class OnBoardingActivity : AppCompatActivity(), Navigator, ToolbarChanger {
    private val binding: ActivityOnboardingBinding by lazy {
        ActivityOnboardingBinding.inflate(layoutInflater)
    }

    private val viewModel: OnboardingViewModel by viewModels()
    private var nickName: String? = null
    private var userName: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.onboardingToolbar.title = ""
        setSupportActionBar(binding.onboardingToolbar)
        subscribeToDataFromFragments()
        if (savedInstanceState == null) addStartFragment()
    }

    private fun subscribeToDataFromFragments() {
        viewModel.nickName.observe(this) {
            nickName = it
        }
        viewModel.username.observe(this) {
            userName = it
        }
        viewModel.password.observe(this) {
            password = it
        }
    }

    private fun addStartFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.onboarding_container, StartFragment()
        ).commit()
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance)
            .addToBackStack(null)
            .replace(R.id.onboarding_container, fragment).commit()
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


    override fun goToApplicationActivity(isSignedIn: Boolean) {
        val intent = Intent(this, ApplicationActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(R.animator.appearance, R.animator.disappearance)
    }

    override fun openBottomSheetDialogFragment(dialog: BottomSheetDialogFragment) {
        dialog.show(supportFragmentManager, AppConstants.DIALOG_TAG)
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun registerAccount() {
        CoroutineScope(Dispatchers.Main).launch {
            if (userName != null) viewModel.registerAccount(userName!!)
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

    override fun finishActivity() {
        finish()
    }

}



