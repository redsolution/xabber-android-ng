package com.xabber.utils.mask

import android.graphics.*
import kotlin.math.max

class MaskedDrawableBitmapShader : MaskedDrawable() {
    private var mPictureBitmap: Bitmap? = null
    private var mMaskBitmap: Bitmap? = null
    private val mPaintShader = Paint()
    private var mBitmapShader: BitmapShader? = null
    override fun setMaskBitmap(maskBitmap: Bitmap?) {
        mMaskBitmap = maskBitmap
        updateScaleMatrix()
    }

    override fun setPictureBitmap(pictureBitmap: Bitmap?) {
        mPictureBitmap = pictureBitmap
        mBitmapShader = BitmapShader(
            mPictureBitmap!!,
            Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT
        )
        mPaintShader.shader = mBitmapShader
        updateScaleMatrix()
    }

    override fun draw(canvas: Canvas) {
        if (mPictureBitmap == null || mMaskBitmap == null) {
            return
        }
        canvas.drawBitmap(mMaskBitmap!!, 0f, 0f, mPaintShader)
    }

    private fun updateScaleMatrix() {
        if (mPictureBitmap == null || mMaskBitmap == null) {
            return
        }
        val maskW = mMaskBitmap!!.width
        val maskH = mMaskBitmap!!.height
        val pictureW = mPictureBitmap!!.width
        val pictureH = mPictureBitmap!!.height
        val wScale = maskW / pictureW.toFloat()
        val hScale = maskH / pictureH.toFloat()
        val scale = max(wScale, hScale)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((maskW - pictureW * scale) / 2f, (maskH - pictureH * scale) / 2f)
        mBitmapShader!!.setLocalMatrix(matrix)
    }

    override fun setAlpha(alpha: Int) {
        mPaintShader.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mPaintShader.colorFilter = cf
    }

    override fun getOpacity(): Int {
        return PixelFormat.UNKNOWN
    }

    override fun getIntrinsicWidth(): Int {
        return if (mMaskBitmap != null) mMaskBitmap!!.width else super.getIntrinsicWidth()
    }

    override fun getIntrinsicHeight(): Int {
        return if (mMaskBitmap != null) mMaskBitmap!!.height else super.getIntrinsicHeight()
    }

}
