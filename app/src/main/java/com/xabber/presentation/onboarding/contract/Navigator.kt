package com.xabber.presentation.onboarding.contract

import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

fun Fragment.navigator(): Navigator = requireActivity() as Navigator

interface Navigator {

    fun openSigninFragment()

    fun openSignupNicknameFragment()

    fun openSignupUserNameFragment()

    fun openSignupPasswordFragment()

    fun openSignupAvatarFragment()

    fun goToApplicationActivity(isSignedIn: Boolean)

    fun openBottomSheetDialogFragment(dialog: BottomSheetDialogFragment)

    fun goBack()

    fun registerAccount()

    fun finishActivity()

}