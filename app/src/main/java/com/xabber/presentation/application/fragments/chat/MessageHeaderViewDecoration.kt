package com.xabber.presentation.application.fragments.chat

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.xabber.R

class MessageHeaderViewDecoration : ItemDecoration() {

//    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
//    annotation class DateState {
//        companion object {
//            var SCROLL_ACTIVE = 0
//            var SCROLL_IDLE = 1
//            var SCROLL_IDLE_NO_ANIMATION = 2
//            var INITIATED_ANIMATION = 3
//            var ANIMATING = 4
//            var FINISHED_ANIMATING = 5
//        }
//    }
//
//    private val paintFont: Paint
//    private val drawable: Drawable
//    private val handler = Handler()
//    private var parent: RecyclerView? = null
//    private var headerViewXMargin = 0
//    private val stickyDrawableTopBound = 2 * backgroundDrawableYMargin
//    private val stickyDrawableBottomBound = dateLayoutHeight
//
//
//    @DateState
//    private var currentDateState = 0
//    private var attached = false
//    private var alpha = 255
//    private var originTime: Long = 0
//    private var frameTime: Long = 0
//    private val scrollListener: RecyclerView.OnScrollListener =
//        object : RecyclerView.OnScrollListener() {
//            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
//                super.onScrollStateChanged(recyclerView, newState)
//                when (newState) {
//                    RecyclerView.SCROLL_STATE_IDLE -> {
//                        currentDateState = DateState.SCROLL_IDLE
//                        recyclerView.invalidate()
//                    }
//                    RecyclerView.SCROLL_STATE_SETTLING, RecyclerView.SCROLL_STATE_DRAGGING -> {
//                        currentDateState = DateState.SCROLL_ACTIVE
//                        alpha = 255
//                        handler.removeCallbacks(runAlphaAnimation)
//                    }
//                }
//            }
//        }
//    private val runAlphaAnimation = Runnable {
//        currentDateState = DateState.ANIMATING
//        parent!!.invalidate()
//    }
//
//    // Adding a scroll listener, saving the RecyclerView reference
//    // and setting current scroll state if possible.
//    // Mainly needed for the transparency animation.
//    private fun attachRecyclerViewData(parent: RecyclerView) {
//        parent.removeOnScrollListener(scrollListener)
//        attached = true
//        parent.addOnScrollListener(scrollListener)
//        this.parent = parent
//        if (parent.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
//            currentDateState = DateState.SCROLL_IDLE
//        }
//    }
//
//    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
//        super.onDrawOver(c, parent, state)
//        if (!attached) attachRecyclerViewData(parent)
//        var i = 0
//        while (i < parent.childCount) {
//            val child = parent.getChildAt(i)
//            val holder = parent.getChildViewHolder(child)
//            if (i != 0) {
//                if (holder is BasicMessageVH && (holder as BasicMessageVH).getNeedDate()) {
//                    // Draw a Date view for all visible messages that require one.
//                    // Since the position is != 0, these dates will not behave in the same way
//                    // as the sticky date, they will simply be directly tied to the message.
//                    drawDateMessageHeader(c, parent, child, holder as BasicMessageVH)
//                }
//                if (holder is MessageVH && holder.isUnread()) {
//                    drawUnreadMessageHeader(c, parent, child, holder)
//                }
//            }
//            if (i == 0) {
//                i = measureFirstChildren(c, parent, child, holder as BasicMessageVH, 0)
//            }
//            i++
//        }
//    }
//
//    // A recursive check that measures whether we can draw the date as a sticky properly or not.
//    // Since this check starts in a for loop, we return the iteration at which we stopped here to the main loop
//    // To skip the iterations we already checked here.
//    private fun measureFirstChildren(
//        c: Canvas,
//        parent: RecyclerView,
//        originalChild: View,
//        holderMessage: BasicMessageVH,
//        currentLoopIteration: Int
//    ): Int {
//        // Check if we need to draw an "Unread messages" header above originalChild message
//        var currentLoopIteration = currentLoopIteration
//        if (needToDrawUnreadHeader(holderMessage)) {
//            drawUnreadMessageHeader(c, parent, originalChild, holderMessage as MessageVH)
//        }
//        if (parent.childCount > currentLoopIteration + 1) {
//            val nextChild = parent.getChildAt(currentLoopIteration + 1)
//            val nextHolder = parent.getChildViewHolder(nextChild)
//            if (nextHolder is BasicMessageVH) {
//                // Check if the date of the originalChild is
//                // the same as the date of the nextChild
//                if (holderMessage.getDate().equals((nextHolder as BasicMessageVH).getDate())) {
//                    // if same, make sure we have enough space to draw the sticky header
//                    return if (checkIfStickyHeaderFitsAboveNextChild(nextChild)) {
//                        drawDateStickyHeader(c, parent, originalChild, holderMessage, true)
//
//                        // We only try to examine nextChild for unread message header if we will not
//                        // recursively call measureFirstChild again, to avoid a double check
//                        // (as nextChild in the first loop, and as originalChild in the next one).
//                        if (needToDrawUnreadHeader(nextHolder as BasicMessageVH)) {
//                            drawUnreadMessageHeader(c, parent, nextChild, nextHolder as MessageVH)
//                        }
//
//                        // after drawing it, leave the recursive call with the current loop + 1,
//                        // since we checked both the originalChild (currentLoopIteration)
//                        // and nextChild (currentLoopIteration + 1)
//                        currentLoopIteration + 1
//                    } else {
//                        // Since sticky doesn't fit, we can't do much at this loop.
//                        // Just return the call to the method, while iterating the current loop by 1
//                        measureFirstChildren(
//                            c,
//                            parent,
//                            nextChild,
//                            nextHolder as BasicMessageVH,
//                            currentLoopIteration + 1
//                        )
//                    }
//                } else {
//                    // If the dates are different, then that means that the next child
//                    // is the first message with a different date, i.e. it needs a date header.
//                    drawDateMessageHeader(c, parent, nextChild, nextHolder as BasicMessageVH)
//                    // We did what we needed with nextChild, so we bump the loop iteration by 1,
//                    // but since we didn't do anything with the originalChild, we can't return yet.
//
//                    // Same as above nextHolder Unread check. Just avoiding repeated same checks.
//                    if (needToDrawUnreadHeader(nextHolder as BasicMessageVH)) {
//                        drawUnreadMessageHeader(c, parent, nextChild, nextHolder as MessageVH)
//                    }
//                    currentLoopIteration++
//                }
//            }
//        }
//        // Here we draw the sticky date that isn't forced to be drawn at the same position.
//        // Either when we ran out of items to check, or the nextChild is of a different date than originalChild.
//        drawDateStickyHeader(c, parent, originalChild, holderMessage, false)
//        return currentLoopIteration
//    }
//
//    // Check if the sticky date of child(0) will be above the lower bound of child(1)
//    // This is important if we have 2 message views with a small height with the same date.
//    private fun checkIfStickyHeaderFitsAboveNextChild(nextChild: View): Boolean {
//        return nextChild.bottom > stickyDrawableBottomBound + backgroundDrawableYMargin
//    }
//
//    // Check if we should make the date view disappear or not
//    private fun shouldAnimateAlpha(date: Rect, messageTopBound: Int): Boolean {
//        // The bigger alphaThreshold constant is, the bigger the area that date
//        // has to occupy within the bounds of message to start disappearing
//        return date.bottom - messageTopBound > alphaThreshold
//    }
//
//    private fun needToDrawUnreadHeader(holder: BasicMessageVH): Boolean {
//        return holder is MessageVH && (holder as MessageVH).isUnread()
//    }
//
//    // Draws a date that appears at the top of chat window, either as a sticky date
//    // that stays in one place, or a date of the partially visible message
//    private fun drawDateStickyHeader(
//        c: Canvas,
//        parent: RecyclerView,
//        child: View,
//        holder: BasicMessageVH,
//        forceDrawAsSticky: Boolean
//    ) {
//        val width = measureText(paintFont, holder.getDate())
//        headerViewXMargin = (parent.measuredWidth - width) / 2
//        val drawableBounds = Rect()
//        drawableBounds.left = headerViewXMargin - backgroundDrawableXPadding
//        drawableBounds.right = headerViewXMargin + width + backgroundDrawableXPadding
//
//        // Check to see if the bottom of the first view is less than the full size of date layout.
//        //
//        // ** We add the margin to try and compensate for the message bubble's own
//        // ** margins and make the date look like it is within the bounds of the message's background drawable,
//        // ** since the visible border of the message background drawable and the View border are visibly different
//        if (!forceDrawAsSticky && child.bottom < stickyDrawableBottomBound + backgroundDrawableYMargin) {
//            //draw a moving date that didn't reach the sticky position yet.
//            drawableBounds.bottom = child.bottom - backgroundDrawableYMargin
//            drawableBounds.top = child.bottom - dateLayoutHeight + backgroundDrawableYMargin
//        } else {
//            //draw a normal sticky date with fixed position
//            drawableBounds.bottom = stickyDrawableBottomBound
//            drawableBounds.top = stickyDrawableTopBound
//        }
//        when (currentDateState) {
//            DateState.SCROLL_IDLE -> {
//                // check if the date covers message bounds
//                // if so, start a runnable that sets currentDateState to
//                // DateState.INITIATED_ANIMATION and post
//                // a delayed runnable to the handler
//                // if not, set state to SCROLL_IDLE_NO_ANIMATION;
//                val childTopBound = child.top
//                currentDateState = if (shouldAnimateAlpha(drawableBounds, childTopBound)) {
//                    handler.postDelayed(runAlphaAnimation, 500)
//                    DateState.INITIATED_ANIMATION
//                } else {
//                    DateState.SCROLL_IDLE_NO_ANIMATION
//                }
//            }
//            DateState.SCROLL_IDLE_NO_ANIMATION -> {}
//            DateState.INITIATED_ANIMATION -> {}
//            DateState.ANIMATING -> {
//                // drawing the current frame of alpha transition
//                if (alpha == 255) {
//                    originTime = System.currentTimeMillis()
//                    alpha -= 17
//                } else {
//                    frameTime = System.currentTimeMillis() - originTime
//                    originTime = System.currentTimeMillis()
//                    if (frameTime <= 0) {
//                        frameTime = 17
//                    }
//                }
//                alpha -= frameTime.toInt()
//                if (alpha <= 0) {
//                    alpha = 0
//                    currentDateState = DateState.FINISHED_ANIMATING
//                } else {
//                    handler.postDelayed(runAlphaAnimation, 17)
//                }
//            }
//            DateState.FINISHED_ANIMATING ->                 // set alpha = 0 in case of future redraws,
//                // no more invalidation calls.
//                alpha = 0
//            DateState.SCROLL_ACTIVE ->                 // reset alpha back to 255, handler is cleared in the scroll state listener.
//                alpha = 255
//        }
//        drawString(c, holder.getDate(), drawableBounds, alpha)
//    }
//
//    // Draws a date that appears on top of the first message of the day.
//    // This date nearly always stays directly tied to the message position.
//    private fun drawDateMessageHeader(
//        c: Canvas,
//        parent: RecyclerView,
//        child: View,
//        holder: BasicMessageVH
//    ) {
//        val width = measureText(paintFont, holder.getDate())
//        // additional vertical offset for the Date header.
//        var additionalOffset = 0
//        headerViewXMargin = (parent.measuredWidth - width) / 2
//        val drawableBounds = Rect()
//        if (needToDrawUnreadHeader(holder)) {
//            additionalOffset = dateLayoutHeight
//        }
//        drawableBounds.left = headerViewXMargin - backgroundDrawableXPadding
//        drawableBounds.right = headerViewXMargin + width + backgroundDrawableXPadding
//        drawableBounds.bottom = child.top - backgroundDrawableYMargin - additionalOffset
//
//        // Check if the background drawable's vertical position is closer to the top than
//        // the position of the sticky drawable.
//        // This happens because the sticky position has a doubled top margin and no bottom margin.
//        if (drawableBounds.bottom < stickyDrawableBottomBound) {
//            // If it's position is closer to the top, then make sure that it is drawn as a sticky date.
//            drawableBounds.bottom = stickyDrawableBottomBound
//            drawableBounds.top = stickyDrawableTopBound
//        } else {
//            drawableBounds.top =
//                child.top - backgroundDrawableHeight - backgroundDrawableYMargin - additionalOffset
//        }
//        drawString(c, holder.getDate(), drawableBounds, 255)
//    }
//
//    private fun drawUnreadMessageHeader(
//        c: Canvas,
//        parent: RecyclerView,
//        child: View,
//        holder: MessageVH
//    ) {
//        val width = measureText(paintFont, unread)
//        var alpha: Int
//        headerViewXMargin = (parent.measuredWidth - width) / 2
//        val drawableBounds = Rect()
//        drawableBounds.left = headerViewXMargin - backgroundDrawableXPadding
//        drawableBounds.right = headerViewXMargin + width + backgroundDrawableXPadding
//        drawableBounds.top = child.top - backgroundDrawableHeight - backgroundDrawableYMargin
//        drawableBounds.bottom = child.top - backgroundDrawableYMargin
//
//        // if top of unread is too close to the top, we
//        // change alpha depending on how close it is to the top
//        if (drawableBounds.top > stickyDrawableBottomBound + backgroundDrawableHeight / 2) {
//            alpha = 255
//        } else {
//            alpha =
//                (drawableBounds.top - stickyDrawableBottomBound) * 255 / (backgroundDrawableHeight / 2)
//            if (alpha <= 0) {
//                alpha = 0
//            } else if (alpha > 255) {
//                alpha = 255
//            }
//        }
//        drawString(c, unread, drawableBounds, alpha)
//    }
//
//    // Drawing the date itself with provided parameters
//    private fun drawString(c: Canvas, string: String, bounds: Rect, alpha: Int) {
//        paintFont.alpha = alpha
//        drawable.alpha = alpha
//        drawable.bounds = bounds
//        drawable.draw(c)
//        c.drawText(
//            string,
//            headerViewXMargin.toFloat(),
//            (bounds.bottom - dateTextBaseline).toFloat(),
//            paintFont
//        )
//    }
//
//    private fun measureText(
//        paint: Paint,
//        text: CharSequence,
//        start: Int = 0,
//        end: Int = text.length
//    ): Int {
//        return paint.measureText(text, start, end).toInt()
//    }
//
//    // Setting the additional top offset for the views that
//    // require some header decoration to be attached above the message
//    override fun getItemOffsets(
//        outRect: Rect,
//        view: View,
//        parent: RecyclerView,
//        state: RecyclerView.State
//    ) {
//        val holder = parent.getChildViewHolder(view)
//        var topOffset = 0
////        if (holder is BasicMessageVH && (holder as BasicMessageVH).getNeedDate()) {
////            topOffset += dateLayoutHeight
////        }
////        if (holder is MessageVH && holder.isUnread()) {
////            topOffset += dateLayoutHeight
////        }
//        outRect[0, topOffset, 0] = 0
//    }

//    companion object {
//        private val backgroundDrawableHeight: Int = dipToPx(24f, Application.getInstance())
//        private val backgroundDrawableXPadding: Int = dipToPx(8f, Application.getInstance())
//        private val backgroundDrawableYMargin: Int = dipToPx(3.64f, Application.getInstance())
//        private val dateLayoutHeight = 2 * backgroundDrawableYMargin + backgroundDrawableHeight
//        private val alphaThreshold = dateLayoutHeight * 6 / 10
//        private val dateTextBaseline = backgroundDrawableHeight * 3 / 11
//        private val unread: String =
//            Application.getInstance().getResources().getString(R.string.unread_messages)
//    }
//
//    init {
//        drawable = Application.getInstance().getResources().getDrawable(
//            if (SettingsManager.interfaceTheme() === SettingsManager.InterfaceTheme.dark) R.drawable.rounded_background_grey_transparent_dark else R.drawable.rounded_background_grey_transparent
//        )
//        paintFont = Paint()
//        paintFont.color = .getColor(R.color.white)
//        paintFont.textSize = AndroidUtilsKt.spToPxFloat(14f, Application.getInstance()) + 1f
//        paintFont.typeface = Typeface.DEFAULT_BOLD
//        paintFont.isAntiAlias = true
//        handler = Handler()
//    }
}