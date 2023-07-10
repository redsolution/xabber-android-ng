package com.xabber.presentation.application.manage

import android.content.res.Configuration
import android.content.res.Resources
import androidx.fragment.app.Fragment
import com.xabber.utils.dp

/**
 * Static class that calculates screen parameters. Fun isDualScreenMode() detects if the device
 * is a phone or tablet (dual-window mode for tablets).
 */

object DisplayManager : Fragment() {
    private const val MAX_WIDTH = 800
    private const val MAX_HEIGHT = 900
    private val displayMetrics = Resources.getSystem().displayMetrics
    private var heightStatusBar = 0

    fun getWidthDp(): Int =
        (displayMetrics.widthPixels / displayMetrics.density).toInt()

    fun isDualScreenMode(): Boolean {
        val widthDp = getWidthDp()
        return (
                widthDp >= MAX_WIDTH && Resources.getSystem().configuration.orientation ==
                        Configuration.ORIENTATION_PORTRAIT) || (
                widthDp >= MAX_HEIGHT && Resources.getSystem().configuration.orientation
                        == Configuration.ORIENTATION_LANDSCAPE)
    }

    fun getMainContainerWidth(): Int = (getWidthDp() / 100 * 40.dp)

    fun getHeightStatusBar(): Int = heightStatusBar

    fun setHeightStatusBar(height: Int) {
        heightStatusBar = height
    }

    fun screenWidth() = displayMetrics.widthPixels

    fun screenHeight() = displayMetrics.heightPixels

}
