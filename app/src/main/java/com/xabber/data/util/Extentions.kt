package com.xabber.data.util

import android.content.res.Resources

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

val Int.px: Float
    get() = ((this - 0.5f) / Resources.getSystem().displayMetrics.density)