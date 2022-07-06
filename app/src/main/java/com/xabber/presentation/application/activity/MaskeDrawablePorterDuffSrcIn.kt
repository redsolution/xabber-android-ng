package com.xabber.presentation.application.activity

import android.graphics.*
import android.graphics.drawable.Drawable

class MaskeDrawablePorterDuffSrcIn: Drawable() {
    lateinit var mPictureBitmap: Bitmap
   lateinit var  mMaskBitmap: Bitmap
  lateinit var mBufferBitmap: Bitmap
 lateinit var mBufferCanvas: Canvas
   private val mPaintSrcIn = Paint()



  fun setPictureBitmap( pictureBitmap: Bitmap) {
       mPictureBitmap = pictureBitmap
   }

   fun setMaskBitmap(maskBitmap: Bitmap) {
       mMaskBitmap = maskBitmap
   }

 override fun onBoundsChange( bounds: Rect) {
       super.onBoundsChange(bounds)
       val width = bounds.width()
       val height = bounds.height()

       if (width <= 0 || height <= 0) {
           return
       }
 if (mBufferBitmap != null
           && mBufferBitmap.width == width
           && mBufferBitmap.height == height) {
           return
       }

       mBufferBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
       mBufferCanvas = Canvas(mBufferBitmap)
       redrawBufferCanvas()

   }

  override fun draw( canvas: Canvas) {
       canvas.drawBitmap(mBufferBitmap, 0f, 0f, null)
   }

     private fun redrawBufferCanvas() {
       if (mPictureBitmap == null || mMaskBitmap == null || mBufferCanvas == null) {
           return;
       }

       mBufferCanvas.drawBitmap(mMaskBitmap, 0f, 0f, null);
       mBufferCanvas.drawBitmap(mPictureBitmap, 0f, 0f, mPaintSrcIn);
   }


    override fun setAlpha(alpha: Int) {
        mPaintSrcIn.alpha = alpha
    }

    override fun setColorFilter(p0: ColorFilter?) {
    }

    override fun getOpacity(): Int = PixelFormat.UNKNOWN

    override fun getIntrinsicWidth(): Int {
       return if (mMaskBitmap != null)  mMaskBitmap.width else super.getIntrinsicWidth()
   }

   override fun getIntrinsicHeight(): Int {
       return if (mMaskBitmap != null) mMaskBitmap.height else super.getIntrinsicHeight();
   }


}