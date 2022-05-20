package com.xabber.presentation.onboarding.contract

import androidx.fragment.app.Fragment

fun Fragment.navigator(): Navigator = requireActivity() as Navigator

interface Navigator {


    fun startSigninFragment()

    fun startSignupNicknameFragment()

    fun startSignupUserNameFragment()

    fun startSignupPasswordFragment()

    fun startSignupAvatarFragment()

    fun goToApplicationActivity(isSignedIn: Boolean)

    fun goBack()

    fun openCamera()

    fun openGallery()

    fun registerAccount()

}