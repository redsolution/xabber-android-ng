package com.xabber.presentation.application.contract

import android.widget.Toolbar
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Fragment.applicationToolbarChanger(): ApplicationToolbarChanger = requireActivity() as ApplicationToolbarChanger

interface ApplicationToolbarChanger {

    fun setTitle(titleResId: Int)

    fun showNavigationView(isShow: Boolean)


}

class FragmentAction(
    @DrawableRes val iconRes : Int,
    @StringRes val textRes: Int,

)