package com.xabber.presentation.application.fragments.chat.message

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.xabber.R
import com.xabber.presentation.XabberApplication.Companion.applicationContext
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.utils.StringUtils.getDateStringForMessage
import com.xabber.utils.dp
import com.xabber.utils.spToPxFloat

class MessageHeaderViewDecoration(context: Context) : ItemDecoration() {

    private val paintFont = Paint().apply {
        color = Color.WHITE
        textSize = spToPxFloat(14f, applicationContext()) + 1f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    private val drawable: Drawable? = ContextCompat.getDrawable(
        context,
        R.drawable.rounded_background_grey_transparent_dark
    )
    private val backgroundDrawableHeight = 24.dp
    private val backgroundDrawableXPadding = 8.dp
    private val backgroundDrawableYMargin = 3.64f.toInt().dp
    private val dateLayoutHeight = 2 * backgroundDrawableYMargin + backgroundDrawableHeight
    private val dateTextBaseline = backgroundDrawableHeight * 3 / 11

    // Cache: sentDate → (dateString, measuredWidth) to avoid recomputing every frame
    private val dateStringCache = HashMap<Long, Pair<String, Int>>(16)
    // Reusable Rect to avoid allocation per draw call
    private val tmpRect = Rect()

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val adapter = parent.adapter as? MessageAdapter ?: return

        var previousDate: String? = null

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val layoutPosition = parent.getChildAdapterPosition(child)
            if (layoutPosition == RecyclerView.NO_POSITION) continue

            val message = adapter.getMessageItem(layoutPosition) ?: continue
            val (currentDate, textWidth) = getDateAndWidth(message.sentDate)

            if (currentDate != previousDate) {
                drawDateMessageHeader(c, parent, child, currentDate, textWidth)
                previousDate = currentDate
            }
        }
    }

    /** Returns cached (dateString, measuredWidth) for a given sentDate timestamp */
    private fun getDateAndWidth(sentDate: Long): Pair<String, Int> {
        return dateStringCache.getOrPut(sentDate) {
            val dateStr = getDateStringForMessage(sentDate)
            val width = paintFont.measureText(dateStr).toInt()
            dateStr to width
        }
    }

    private fun drawDateMessageHeader(
        c: Canvas,
        parent: RecyclerView,
        child: View,
        dateText: String,
        textWidth: Int
    ) {
        val headerViewXMargin = (parent.measuredWidth - textWidth) / 2

        tmpRect.set(
            headerViewXMargin - backgroundDrawableXPadding,
            child.top - backgroundDrawableHeight - backgroundDrawableYMargin,
            headerViewXMargin + textWidth + backgroundDrawableXPadding,
            child.top - backgroundDrawableYMargin
        )

        drawString(c, dateText, tmpRect, 255)
    }

    private fun drawString(canvas: Canvas, text: String, bounds: Rect, alpha: Int) {
        paintFont.alpha = alpha
        drawable?.alpha = alpha
        drawable?.bounds = bounds
        drawable?.draw(canvas)
        canvas.drawText(
            text,
            (bounds.left + backgroundDrawableXPadding).toFloat(),
            (bounds.bottom - dateTextBaseline).toFloat(),
            paintFont
        )
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val holder = parent.getChildViewHolder(view)
        var topOffset = 0
        if (holder is MessageViewHolder && holder.needDate) {
            topOffset += dateLayoutHeight
        }
        outRect.top = topOffset
    }
}
