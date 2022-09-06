package com.xabber.utils.blur

import android.graphics.Bitmap
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.fragment.app.Fragment

object RSBlur : Fragment() {

    fun blur(bitmap: Bitmap, radius: Int): Bitmap {

        val rs = RenderScript.create(context)
        rs.messageHandler = RenderScript.RSMessageHandler()
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

        if (rs != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                RenderScript.releaseAllContexts();
            } else {
                rs.destroy()
            }
        }
        input?.destroy()
        output?.destroy()
        blur?.destroy()

        return bitmap
    }
}
