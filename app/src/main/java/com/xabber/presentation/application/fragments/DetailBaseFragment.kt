package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import com.xabber.R
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.presentation.application.contract.navigator

abstract class DetailBaseFragment(@LayoutRes contentLayoutId: Int) : BaseFragment(contentLayoutId)  {

    private var toolbar: Toolbar? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            onNavigateBack()
        }
    }

    /** Override in subclasses to customize back navigation (e.g. pop one back stack entry). */
    protected open fun onNavigateBack() {
        navigator().goBack()
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
                onNavigateBack()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }

}
