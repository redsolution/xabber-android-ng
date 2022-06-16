package com.xabber.presentation.application.fragments.chat

import android.widget.PopupWindow
import androidx.viewpager.widget.ViewPager

/**
 * @author Ankush Sachdeva (sankush@yahoo.co.in).
 */
class EmojiconsPopup: PopupWindow(), ViewPager.OnPageChangeListener, EmojiconRecent {
    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

    }

    override fun onPageSelected(position: Int) {

    }

    override fun onPageScrollStateChanged(state: Int) {
    }
}