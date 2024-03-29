package com.xabber.utils.custom

import android.graphics.Paint
import android.graphics.Path

interface ClipManager {
    fun createMask(width: Int, height: Int): Path
    val shadowConvexPath: Path?
    fun setupClipLayout(width: Int, height: Int)
    val paint: Paint?
    fun requiresBitmap(): Boolean
}
