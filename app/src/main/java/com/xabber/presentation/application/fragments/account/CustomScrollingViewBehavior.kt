package com.xabber.presentation.application.fragments.account

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.appbar.AppBarLayout;

class CustomScrollingViewBehavior : AppBarLayout.ScrollingViewBehavior {
   constructor() {
    }

   constructor(context: Context, attrs: AttributeSet) {

    }

    override fun onRequestChildRectangleOnScreen(
        parent: CoordinatorLayout,
        child: View,
        rectangle: Rect,
        immediate: Boolean
    ): Boolean {
        return super.onRequestChildRectangleOnScreen(parent, child, rectangle, immediate)
    }
    }

