package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.LayoutRes
import com.google.android.material.appbar.MaterialToolbar
import com.xabber.R
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.contract.navigator

abstract class DetailBaseFragment(@LayoutRes contentLayoutId: Int) : BaseFragment(contentLayoutId) {

    private var toolbar: MaterialToolbar? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        if (!DisplayManager.isDualScreenMode()) {
            toolbar?.setNavigationIcon(R.drawable.ic_arrow_left_white)
            toolbar?.setNavigationOnClickListener {
                navigator().goBack()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }

}
