package com.xabber.utils.blur

import android.graphics.Bitmap
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.fragment.app.Fragment
import com.xabber.presentation.XabberApplication

object RSBlur {

    fun blur(bitmap: Bitmap, radius: Int): Bitmap {
        val rs = RenderScript.create(XabberApplication.applicationContext())
        val input = Allocation.createFromBitmap(
            rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
            Allocation.USAGE_SCRIPT
        )
        val output = Allocation.createTyped(rs, input.type)
        val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        blur.setInput(input)
        blur.setRadius(radius.toFloat())
        blur.forEach(output)
        output.copyTo(bitmap)

        rs.destroy()
        input.destroy()
        output.destroy()
        blur.destroy()

        return bitmap
    }
}
