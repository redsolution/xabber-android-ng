package com.xabber.presentation.application.fragments.chat.message

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.xabber.R
import com.xabber.presentation.XabberApplication.Companion.applicationContext
import com.xabber.utils.dp
import com.xabber.utils.spToPxFloat

/**
 * Message Item Decoration responsible for drawing "Date" and "Unread Message"
 * headers directly above messages and/or over the chat itself.
 */
class MessageHeaderViewDecoration(context: Context) : ItemDecoration() {

    enum class DateState {
        SCROLL_ACTIVE,
        SCROLL_IDLE,
        SCROLL_IDLE_NO_ANIMATION,
        INITIATED_ANIMATION,
        ANIMATING,
        FINISHED_ANIMATING
    }

    private val unread = context.resources.getString(R.string.unread_messages)
    private val handler = Handler(Looper.getMainLooper())
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
    private var parent: RecyclerView? = null
    private var headerViewXMargin = 0
    private val backgroundDrawableHeight = 24.dp
    private val backgroundDrawableXPadding = 8.dp
    private val backgroundDrawableYMargin = 3.64f.toInt().dp
    private val dateLayoutHeight = 2 * backgroundDrawableYMargin + backgroundDrawableHeight
    private val alphaThreshold = dateLayoutHeight * 6 / 10
    private val dateTextBaseline = backgroundDrawableHeight * 3 / 11
    private val stickyDrawableTopBound = 2 * backgroundDrawableYMargin
    private val stickyDrawableBottomBound = dateLayoutHeight
    private var currentDateState: DateState = DateState.SCROLL_IDLE
    private var attached = false
    private var alpha = 255
    private var originTime: Long = 0
    private var frameTime: Long = 0

    private val scrollListener: RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        currentDateState = DateState.SCROLL_IDLE
                        recyclerView.invalidate()
                    }
                    RecyclerView.SCROLL_STATE_SETTLING, RecyclerView.SCROLL_STATE_DRAGGING -> {
                        currentDateState = DateState.SCROLL_ACTIVE
                        alpha = 255
                        handler.removeCallbacks(runAlphaAnimation)
                    }
                }
            }
        }
    private val runAlphaAnimation = Runnable {
        currentDateState = DateState.ANIMATING
        parent?.invalidate()
    }

    private fun attachRecyclerViewData(parent: RecyclerView) {
        parent.removeOnScrollListener(scrollListener)
        attached = true
        parent.addOnScrollListener(scrollListener)
        this.parent = parent
        if (parent.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            currentDateState = DateState.SCROLL_IDLE
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        if (!attached) attachRecyclerViewData(parent)
        var i = 0
        while (i < parent.childCount) {
            val child = parent.getChildAt(i)
            val holder = parent.getChildViewHolder(child)
            if (i != 0) {
                if (holder is MessageViewHolder && holder.needDate) {
                    drawDateMessageHeader(c, parent, child, holder)
                }
                if (holder is MessageViewHolder && holder.isUnread) {
                    drawUnreadMessageHeader(c, parent, child)
                }
            }
            if (i == 0) {
                if (holder != null) {
                    i = measureFirstChildren(c, parent, child, holder as MessageViewHolder, 0)
                }
            }
            i++
        }
    }

    private fun drawDateMessageHeader(
        c: Canvas,
        parent: RecyclerView,
        child: View,
        holder: MessageViewHolder
    ) {
        val width = measureText(paintFont, holder.date)
        var additionalOffset = 0
        headerViewXMargin = (parent.measuredWidth - width) / 2
        val drawableBounds = Rect()
        if (needToDrawUnreadHeader(holder)) {
            additionalOffset = dateLayoutHeight
        }
        drawableBounds.left = headerViewXMargin - backgroundDrawableXPadding
        drawableBounds.right = headerViewXMargin + width + backgroundDrawableXPadding
        drawableBounds.bottom = child.top - backgroundDrawableYMargin - additionalOffset

        if (drawableBounds.bottom < stickyDrawableBottomBound) {
            drawableBounds.bottom = stickyDrawableBottomBound
            drawableBounds.top = stickyDrawableTopBound
        } else {
            drawableBounds.top =
                child.top - backgroundDrawableHeight - backgroundDrawableYMargin - additionalOffset
        }
        drawString(c, holder.date, drawableBounds, 255)
    }

    private fun measureFirstChildren(
        canvas: Canvas,
        parent: RecyclerView,
        originalChild: View,
        holder: MessageViewHolder,
        loopIteration: Int
    ): Int {
        var currentLoopIteration = loopIteration
        if (needToDrawUnreadHeader(holder)) {
            drawUnreadMessageHeader(canvas, parent, originalChild)
        }
        if (parent.childCount > currentLoopIteration + 1) {
            val nextChild = parent.getChildAt(currentLoopIteration + 1)
            val nextHolder = parent.getChildViewHolder(nextChild)
            if (nextHolder is MessageViewHolder) {
                if (holder.date == nextHolder.date) {
                    return if (checkIfStickyHeaderFitsAboveNextChild(nextChild)) {
                        drawDateStickyHeader(canvas, parent, originalChild, holder, true)
                        if (needToDrawUnreadHeader(nextHolder)) {
                            drawUnreadMessageHeader(canvas, parent, nextChild)
                        }
                        currentLoopIteration + 1
                    } else {
                        measureFirstChildren(
                            canvas,
                            parent,
                            nextChild,
                            nextHolder,
                            currentLoopIteration + 1
                        )
                    }
                } else {
                    drawDateMessageHeader(canvas, parent, nextChild, nextHolder)
                    if (needToDrawUnreadHeader(nextHolder)) {
                        drawUnreadMessageHeader(canvas, parent, nextChild)
                    }
                    currentLoopIteration++
                }
            }
        }
        drawDateStickyHeader(canvas, parent, originalChild, holder, false)
        return currentLoopIteration
    }

    private fun checkIfStickyHeaderFitsAboveNextChild(nextChild: View): Boolean {
        return nextChild.bottom > stickyDrawableBottomBound + backgroundDrawableYMargin
    }

    private fun shouldAnimateAlpha(date: Rect, messageTopBound: Int): Boolean {
        return date.bottom - messageTopBound > alphaThreshold
    }

    private fun needToDrawUnreadHeader(holder: MessageViewHolder): Boolean {
        return holder.isUnread
    }

    private fun drawDateStickyHeader(
        c: Canvas,
        parent: RecyclerView,
        child: View,
        holder: MessageViewHolder,
        forceDrawAsSticky: Boolean
    ) {
        val width = measureText(paintFont, holder.date)
        headerViewXMargin = (parent.measuredWidth - width) / 2
        val drawableBounds = Rect()
        drawableBounds.left = headerViewXMargin - backgroundDrawableXPadding
        drawableBounds.right = headerViewXMargin + width + backgroundDrawableXPadding

        if (!forceDrawAsSticky && child.bottom < stickyDrawableBottomBound + backgroundDrawableYMargin) {
            drawableBounds.bottom = child.bottom - backgroundDrawableYMargin
            drawableBounds.top = child.bottom - dateLayoutHeight + backgroundDrawableYMargin
        } else {
            drawableBounds.bottom = stickyDrawableBottomBound
            drawableBounds.top = stickyDrawableTopBound
        }
        when (currentDateState) {
            DateState.SCROLL_IDLE -> {
                val childTopBound = child.top
                currentDateState = if (shouldAnimateAlpha(drawableBounds, childTopBound)) {
                    handler.postDelayed(runAlphaAnimation, 500)
                    DateState.INITIATED_ANIMATION
                } else {
                    DateState.SCROLL_IDLE_NO_ANIMATION
                }
            }
            DateState.SCROLL_IDLE_NO_ANIMATION -> {}
            DateState.INITIATED_ANIMATION -> {}
            DateState.ANIMATING -> {
                if (alpha == 255) {
                    originTime = System.currentTimeMillis()
                    alpha -= 17
                } else {
                    frameTime = System.currentTimeMillis() - originTime
                    originTime = System.currentTimeMillis()
                    if (frameTime <= 0) {
                        frameTime = 17
                    }
                }
                alpha -= frameTime.toInt()
                if (alpha <= 0) {
                    alpha = 0
                    currentDateState = DateState.FINISHED_ANIMATING
                } else {
                    handler.postDelayed(runAlphaAnimation, 17)
                }
            }
            DateState.FINISHED_ANIMATING ->
                alpha = 0
            DateState.SCROLL_ACTIVE ->
                alpha = 255
        }
        drawString(c, holder.date, drawableBounds, alpha)
    }

    private fun drawUnreadMessageHeader(
        c: Canvas,
        parent: RecyclerView,
        child: View
    ) {
        val width = measureText(paintFont, unread)
        var alpha: Int
        headerViewXMargin = (parent.measuredWidth - width) / 2
        val drawableBounds = Rect()
        drawableBounds.left = headerViewXMargin - backgroundDrawableXPadding
        drawableBounds.right = headerViewXMargin + width + backgroundDrawableXPadding
        drawableBounds.top = child.top - backgroundDrawableHeight - backgroundDrawableYMargin
        drawableBounds.bottom = child.top - backgroundDrawableYMargin

        if (drawableBounds.top > stickyDrawableBottomBound + backgroundDrawableHeight / 2) {
            alpha = 255
        } else {
            alpha =
                (drawableBounds.top - stickyDrawableBottomBound) * 255 / (backgroundDrawableHeight / 2)
            if (alpha <= 0) {
                alpha = 0
            } else if (alpha > 255) {
                alpha = 255
            }
        }
        drawString(c, unread, drawableBounds, alpha)
    }

    private fun drawString(canvas: Canvas, text: String?, bounds: Rect, alpha: Int) {
        paintFont.alpha = alpha
        drawable?.alpha = alpha
        drawable?.bounds = bounds
        drawable?.draw(canvas)
        if (text != null)
            canvas.drawText(
                text,
                headerViewXMargin.toFloat(),
                (bounds.bottom - dateTextBaseline).toFloat(),
                paintFont
            )
    }

    private fun measureText(
        paint: Paint,
        text: CharSequence?,
        start: Int = 0,
        end: Int = text?.length ?: 0
    ): Int = paint.measureText(text.toString(), start, end).toInt()

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
        if (holder is MessageViewHolder && holder.isUnread) {
            topOffset += dateLayoutHeight
        }
        outRect[0, topOffset, 0] = 0
    }

}
