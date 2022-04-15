package com.xabber.application.contract

import androidx.fragment.app.Fragment

fun Fragment.applicationToolbarChanger(): ApplicationToolbarChanger = requireActivity() as ApplicationToolbarChanger

interface ApplicationToolbarChanger {

    fun setShowBack(isVisible: Boolean)

    fun setTitle(titleResId: Int)



}