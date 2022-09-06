package com.xabber.presentation

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.xabber.R
import com.xabber.presentation.application.activity.DisplayManager

abstract class BaseFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {
    var appbar: AppBarLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appbar = view.findViewById(R.id.appbar)
        if (appbar != null) appbar!!.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
    }

}
