package com.xabber.presentation.application.fragments.chat

import android.graphics.*
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.util.Preconditions
import com.bumptech.glide.util.Util
import com.xabber.presentation.application.fragments.chat.RoundedBorders
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest

class RoundedBorders(roundingRadius: Int, borderThickness: Int) : BitmapTransformation() {
    private val roundingRadius: Int
    private val borderThickness: Int
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        Preconditions.checkArgument(roundingRadius > 0, "roundingRadius must be greater than 0.")
        val paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#40979797")
        paint.strokeWidth = borderThickness.toFloat()
        val rect = RectF(
            0f, 0f, toTransform.width.toFloat(), toTransform.height
                .toFloat()
        )
        val canvas = Canvas(toTransform)
        //canvas.drawColor(Color.MAGENTA);
        canvas.drawRoundRect(rect, roundingRadius.toFloat(), roundingRadius.toFloat(), paint)
        return toTransform
    }

    override fun equals(o: Any?): Boolean {
        if (o is RoundedBorders) {
            val other = o
            return !(roundingRadius != other.roundingRadius || borderThickness != other.borderThickness)
        }
        return false
    }

    override fun hashCode(): Int {
        return Util.hashCode(
            ID.hashCode(),
            Util.hashCode(
                roundingRadius,
                Util.hashCode(borderThickness)
            )
        )
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
        val radiusData = ByteBuffer.allocate(4).putInt(roundingRadius).array()
        messageDigest.update(radiusData)
        val borderData = ByteBuffer.allocate(4).putInt(borderThickness).array()
        messageDigest.update(borderData)
    }

    companion object {
        private const val ID = "com.redsolution.xabber.RoundedBorders"
        private val ID_BYTES = ID.toByteArray(Charset.forName("UTF-8"))
    }

    init {
        Preconditions.checkArgument(roundingRadius > 0, "roundingRadius must be greater than 0.")
        Preconditions.checkArgument(
            borderThickness >= 0,
            "borderThickness must be greater than or equal to 0"
        )
        this.roundingRadius = roundingRadius
        this.borderThickness = borderThickness
    }
}