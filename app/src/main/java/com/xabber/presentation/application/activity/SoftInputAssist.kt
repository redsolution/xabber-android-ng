package com.xabber.presentation.application.activity

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import android.widget.FrameLayout

/**
 * This class helps to open the soft keyboard in fullscreen mode
 */

class SoftInputAssist(window: Window) {
    private var rootView: View?
    private var contentContainer: ViewGroup?
    private var viewTreeObserver: ViewTreeObserver? = null
    private val listener = OnGlobalLayoutListener { possiblyResizeChildOfContent() }
    private val contentAreaOfWindowBounds = Rect()
    private val rootViewLayout: FrameLayout.LayoutParams
    private var usableHeightPrevious = 0

    init {
        contentContainer = window.findViewById<View>(android.R.id.content) as ViewGroup
        rootView = contentContainer?.getChildAt(0)
        rootViewLayout = rootView?.layoutParams as FrameLayout.LayoutParams
    }

    fun onResume() {
        if (viewTreeObserver == null || !viewTreeObserver!!.isAlive) {
            viewTreeObserver = rootView?.viewTreeObserver
        }
        viewTreeObserver?.addOnGlobalLayoutListener(listener)
    }

    private fun possiblyResizeChildOfContent() {
        contentContainer?.getWindowVisibleDisplayFrame(contentAreaOfWindowBounds)
        val usableHeightNow = contentAreaOfWindowBounds.bottom
        if (usableHeightNow != usableHeightPrevious) {
            rootViewLayout.height = usableHeightNow
            rootView?.layout(
                contentAreaOfWindowBounds.left,
                0,
                contentAreaOfWindowBounds.right,
                contentAreaOfWindowBounds.bottom
            )
            rootView?.requestLayout()
            usableHeightPrevious = usableHeightNow
        }
    }

    fun onPause() {
        if (viewTreeObserver != null)
            if (viewTreeObserver!!.isAlive)
                viewTreeObserver?.removeOnGlobalLayoutListener(listener)
    }

    fun onDestroy() {
        rootView = null
        contentContainer = null
        viewTreeObserver = null
    }

}
