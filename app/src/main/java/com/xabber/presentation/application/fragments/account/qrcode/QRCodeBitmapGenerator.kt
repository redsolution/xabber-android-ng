package com.xabber.presentation.application.fragments.account.qrcode

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.xabber.presentation.application.manage.ColorManager

class QRCodeBitmapGenerator(private val context: Context) {
    private val displayMetrics = context.resources.displayMetrics
    private val screenWidth = displayMetrics.widthPixels
    private val screenHeight = displayMetrics.heightPixels
    private val width = minOf(screenWidth, screenHeight)
    private val height = maxOf(screenWidth, screenHeight)
    private val maxTextLength = 32

    private fun generateBackgroundBitmap(colorKey: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height - 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colorRes = ColorManager.convertColorLightNameToId(colorKey)
        canvas.drawColor(ContextCompat.getColor(context, colorRes))
        return bitmap
    }

    fun generateQRCodeToSend(name: String, jid: String, size: Int, colorKey: String): Bitmap {
        val qrCodeBitmap = generateQrBitmap(jid, size)
        val backgroundBitmap = generateBackgroundBitmap(colorKey)
        val width = backgroundBitmap.width
        val height = backgroundBitmap.height
        val qrCodeWidth = width / 2
        val qrCodeX = (width - qrCodeWidth) / 2
        val qrCodeY = (height - qrCodeWidth) / 2
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.textSize = 60f
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        val textBounds = Rect()
        val trimmedName =
            if (name.length > maxTextLength) name.substring(0, maxTextLength) + "..." else name
        val trimmedJid = if (jid.length > maxTextLength) jid.substring(0, maxTextLength) + "..." else jid
        textPaint.getTextBounds(trimmedName, 0, trimmedName.length, textBounds)
        val textX = width / 2
        val textY = qrCodeY + qrCodeWidth + textBounds.height()
        val combinedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)
        canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
        canvas.drawBitmap(
            qrCodeBitmap,
            null,
            Rect(qrCodeX, qrCodeY, qrCodeX + qrCodeWidth, qrCodeY + qrCodeWidth),
            null
        )
        canvas.drawText(trimmedName, textX.toFloat(), textY.toFloat() + 100, textPaint)
        canvas.drawText(trimmedJid, textX.toFloat(), textY.toFloat() + 300, textPaint)
        return combinedBitmap
    }

    private fun generateQrBitmap(text: String, size: Int): Bitmap {
        val qrBitmap = getQrBitmap(text, size)
        val roundedQrBitmap = makeRoundedCornerBitmap(qrBitmap)
        val bitmap = Bitmap.createBitmap(size, size, qrBitmap.config)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(roundedQrBitmap, 0f, 0f, null)
        return bitmap
    }

    private fun makeRoundedCornerBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.BLACK
        canvas.drawRoundRect(rectF, 32f, 32f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    fun getQrBitmap(text: String, size: Int): Bitmap {
        val writer = MultiFormatWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val encoder = BarcodeEncoder()
        return encoder.createBitmap(matrix)
    }

}
