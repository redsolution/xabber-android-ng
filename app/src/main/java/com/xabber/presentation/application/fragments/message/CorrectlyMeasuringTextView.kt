package com.xabber.presentation.application.fragments.message

import android.content.Context
import android.util.AttributeSet
import com.xabber.presentation.custom.CorrectlyTouchEventTextView

class CorrectlyMeasuringTextView(
    context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int
) : CorrectlyTouchEventTextView(context, attrs, defStyleAttr) {

    override fun onMeasure(wms: Int, hms: Int) {
        super.onMeasure(wms, hms)
        try {
            val l = layout;
            if (l.lineCount <= 1) {
                return
            }
            var maxw = 0
            var i = l.lineCount
            while(i >= 0) {
                maxw = Math.max(
                    maxw,
                    Math.round(l.paint.measureText(text, l.getLineStart(i), l.getLineEnd(i)))
                )
                i--
            }
            super.onMeasure(
                Math.min(
                    maxw + paddingLeft + paddingRight,
                    measuredWidth
                ) or MeasureSpec.EXACTLY, getMeasuredHeight() or MeasureSpec.EXACTLY
            )
        } catch (ignore: Exception) {

        }
    }
}