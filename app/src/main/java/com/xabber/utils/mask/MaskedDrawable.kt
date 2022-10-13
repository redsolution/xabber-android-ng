package com.xabber.utils.mask

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

abstract class MaskedDrawable : Drawable() {
    abstract fun setPictureBitmap(pictureBitmap: Bitmap?)
    abstract fun setMaskBitmap(maskBitmap: Bitmap?)
}
