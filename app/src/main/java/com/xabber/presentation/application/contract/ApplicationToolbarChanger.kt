package com.xabber.presentation.application.contract

import android.view.Menu
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Fragment.toolbarChanger(): ApplicationToolbarChanger = requireActivity() as ApplicationToolbarChanger

interface ApplicationToolbarChanger {

    fun setTitle(titleResId: Int)

    fun setAction(vararg fragmentAction: FragmentAction?)

}

class FragmentAction(
    @DrawableRes val iconRes : Int? = null,
    @StringRes val textRes: Int,
    val onAction: Runnable
)