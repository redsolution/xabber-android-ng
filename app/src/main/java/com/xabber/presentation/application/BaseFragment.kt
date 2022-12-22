package com.xabber.presentation.application

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.xabber.R
import com.xabber.model.xmpp.account.AccountViewModel
import com.xabber.presentation.application.activity.DisplayManager

abstract class BaseFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {
    private var appbar: AppBarLayout? = null
    private val accountViewModel = AccountViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appbar = view.findViewById(R.id.appbar)
        setupAppbarPadding()
        val color = if (accountViewModel.primaryColor.value != null) accountViewModel.primaryColor.value else R.color.blue_500
        if (color != null) setupColor(color)
        subscribeToData()
    }

    private fun setupAppbarPadding() {
        if (appbar != null) appbar!!.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
    }

    private fun setupColor(color: Int) {
        appbar?.setBackgroundResource(R.color.blue_500)
    }

    private fun subscribeToData() {
        accountViewModel.primaryColor.observe(viewLifecycleOwner) {
            setupColor(it)
        }
    }

}
