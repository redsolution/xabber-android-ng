package com.xabber.presentation.custom

import android.content.Context
import android.util.TypedValue

object DipUtils {
    fun dipToPx(context: Context, value: Float): Float {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    }
}