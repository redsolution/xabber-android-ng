package com.xabber.presentation.application.fragments

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.xabber.presentation.application.contract.navigator

abstract class DetailBaseFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(callback)
    }

    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().closeDetail()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
         callback.remove()
    }


}