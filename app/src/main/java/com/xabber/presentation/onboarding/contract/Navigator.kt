package com.xabber.presentation.onboarding.contract

import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner

fun Fragment.navigator(): Navigator = requireActivity() as Navigator
typealias ResultListener <T> = (T) -> Unit

interface Navigator {


    fun startSigninFragment()

    fun startSignupNicknameFragment()

    fun startSignupUserNameFragment()

    fun startSignupPasswordFragment()

    fun startSignupAvatarFragment()

    fun goToApplicationActivity(userName: String = "")

    fun goBack()

    fun <T : Parcelable> showResult(result: T)
    fun <T : Parcelable> giveResult(
        clazz: Class<T>,
        owner: LifecycleOwner,
        listener: ResultListener<T>
    )
}