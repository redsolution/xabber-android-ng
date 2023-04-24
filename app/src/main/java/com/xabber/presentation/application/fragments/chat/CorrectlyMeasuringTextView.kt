package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.util.AttributeSet
import com.xabber.utils.custom.CorrectlyTouchEventTextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CorrectlyMeasuringTextView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CorrectlyTouchEventTextView(context, attrs, defStyleAttr) {

    override fun onMeasure(wms: Int, hms: Int) {
        super.onMeasure(wms, hms)
        try {
            val l = layout
            if (l.lineCount <= 1) {
                return
            }
            var maxw = 0
            for (i in l.lineCount - 1 downTo 0) {
                maxw = max(
                    maxw,
                    l.paint.measureText(text, l.getLineStart(i), l.getLineEnd(i)).roundToInt()
                )
            }
            super.onMeasure(
                min(
                    maxw + paddingLeft + paddingRight,
                    measuredWidth
                ) or MeasureSpec.EXACTLY, measuredHeight or MeasureSpec.EXACTLY
            )
        } catch (ignore: Exception) {
        }
    }

}
