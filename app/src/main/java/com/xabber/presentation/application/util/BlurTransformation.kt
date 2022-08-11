package com.xabber.presentation.application.util

import android.graphics.*
import android.os.Build
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.xabber.presentation.application.util.FastBlur.blur
import java.security.MessageDigest

class BlurTransformation(
    private val radius: Int,
    private val sampling: Int,
    private val color: Int
) : BitmapTransformation() {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val scaledWidth = width / sampling
        val scaledHeight = height / sampling
        var bitmap = pool[scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888]
        bitmap.density = toTransform.density
        val canvas = Canvas(bitmap)
        canvas.scale(1 / sampling.toFloat(), 1 / sampling.toFloat())
        val paint = Paint()
        paint.flags = Paint.FILTER_BITMAP_FLAG
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val cmf = ColorMatrixColorFilter(cm)
        paint.colorFilter = cmf
        val veil = Paint()
        veil.color = color
        veil.alpha = 127
        canvas.drawBitmap(toTransform, 0f, 0f, paint)
        canvas.drawRect(Rect(0, 0, toTransform.width, toTransform.height), veil)
        bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                RSBlur.blur(bitmap, radius)
            } catch (e: RuntimeException) {
                blur(bitmap, radius, true)!!
            }
        } else blur(bitmap, radius, true)!!
        return bitmap
    }

    override fun equals(other: Any?): Boolean {
        return other is BlurTransformation && other.radius == radius && other.sampling == sampling && other.color == color
    }

    override fun hashCode(): Int {
        return ID.hashCode() + radius * 1000 + sampling * 10 + color * 10
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + radius + sampling + color).toByteArray(CHARSET))
    }

    companion object {
        private const val ID = "com.xabber.android.ui.helper.BlurTransformation."
    }
}