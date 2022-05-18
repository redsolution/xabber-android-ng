package com.xabber.presentation.application.contract

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Fragment.applicationToolbarChanger(): ApplicationToolbarChanger = requireActivity() as ApplicationToolbarChanger

interface ApplicationToolbarChanger {

    fun setToolbar(titleResId: Int)

    fun setAction(fragmentAction: FragmentAction?)
}

class FragmentAction(
    @DrawableRes val iconRes : Int,
    @StringRes val textRes: Int,
    val onAction: Runnable
)