package com.xabber.utils.mask

import android.content.res.Resources
import android.graphics.BitmapFactory
import com.xabber.presentation.XabberApplication

object MaskPrepare {

    fun getDrawableMask(resources: Resources, drawable: Int, mask: Int): MaskedDrawable {
        val mPictureBitmap =
            BitmapFactory.decodeResource(resources, drawable)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, mask)
                .extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        return maskedDrawable
    }
}