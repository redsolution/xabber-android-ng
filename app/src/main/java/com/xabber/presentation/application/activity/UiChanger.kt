package com.xabber.presentation.application.activity

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object UiChanger {
    private var mask: Mask? = null
    private var accountColor: Int? = null

    fun getMask(): Mask = mask!!

    fun setMask(newMask: Mask) {
        mask = newMask
    }


    fun getAccountColor(): Int? = accountColor

    fun setAccountColor(color: Int) {
        accountColor = color
    }
}