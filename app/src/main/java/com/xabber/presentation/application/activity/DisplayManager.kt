package com.xabber.presentation.application.activity

import android.content.res.Configuration
import android.content.res.Resources
import androidx.fragment.app.Fragment
import com.xabber.utils.dp

object DisplayManager : Fragment() {
    private const val MAX_WIDTH = 800
    private const val MAX_HEIGHT = 900
    private var heightStatusBar = 0

    fun getWidthDp(): Int =
        (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()

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

}
