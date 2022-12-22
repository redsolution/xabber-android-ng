package com.xabber.presentation.onboarding.contract

import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

fun Fragment.navigator(): OnboardingNavigator = requireActivity() as OnboardingNavigator

interface OnboardingNavigator {

    fun openSigninFragment()

    fun openSignupNicknameFragment()

    fun openSignupUserNameFragment()

    fun openSignupPasswordFragment()

    fun openSignupAvatarFragment()

    fun goToApplicationActivity()

    fun openBottomSheetDialogFragment(dialog: BottomSheetDialogFragment)

    fun goBack()

    fun finishActivity()

}
