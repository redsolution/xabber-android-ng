package com.xabber

import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.LifecycleOwner

fun Fragment.navigate(): Navigator = requireActivity() as Navigator
typealias ResultListener <T> = (T) -> Unit
interface Navigator {

    fun startSignUpFragment()

    fun startSignInFragment()

    fun goMainActivity(userName: String = "")

    fun goBack()

    fun <T : Parcelable > showResult (result: T)
    fun<T : Parcelable > giveResult (clazz : Class<T>, owner : LifecycleOwner, listener : ResultListener <T> )
}