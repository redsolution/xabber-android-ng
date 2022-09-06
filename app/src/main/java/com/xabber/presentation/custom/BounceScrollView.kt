package com.xabber.presentation.custom

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

class BounceScrollView : ScrollView {
    private val MAX_Y_OVER_SCROLL_DISTANCE = 40
    private var mMaxYOverScrollDistance: Int
            = (context.resources.displayMetrics.density * MAX_Y_OVER_SCROLL_DISTANCE).toInt()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)



    override fun overScrollBy(deltaX: Int,
                              deltaY: Int,
                              scrollX: Int,
                              scrollY: Int,
                              scrollRangeX: Int,
                              scrollRangeY: Int,
                              maxOverScrollX: Int,
                              maxOverScrollY: Int,
                              isTouchEvent: Boolean): Boolean {

        //функция вызывается при "оттягивании" view

        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, mMaxYOverScrollDistance, isTouchEvent)
    }
}