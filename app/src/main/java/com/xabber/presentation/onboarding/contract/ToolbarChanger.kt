package com.xabber.presentation.onboarding.contract

import androidx.fragment.app.Fragment

fun Fragment.toolbarChanger(): ToolbarChanger = requireActivity() as ToolbarChanger
interface ToolbarChanger {

    fun setTitle(titleResId: Int)

    fun clearTitle()

    fun showArrowBack(isVisible: Boolean)

}
