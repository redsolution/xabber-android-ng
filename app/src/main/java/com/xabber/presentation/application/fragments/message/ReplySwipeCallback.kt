package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import kotlin.math.max
import kotlin.math.min

class ReplySwipeCallback(context: Context) : ItemTouchHelper.Callback(), View.OnTouchListener {
    private var swipeListener: SwipeAction? = null
    private var currentReplyArrowState = ReplyArrowState.GONE

    private val replyIcon: Drawable =
        ContextCompat.getDrawable(context, R.drawable.reply)!!
    private val fullSize = 72
    private val paddingRight = 28
    private val maxSwipeDistanceRatio = 0.18f
    private val activeSwipeDistanceRatio = 0.13f

    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private var touchListenerIsSet = false
    private var touchListenerIsEnabled = false
    private var swipeEnabled = true
    private var swipeBack = false
    private var isAnimating = false
    private val scaleAnimationSteps = ArrayList<Float>(8)
    private var currentAnimationStep = 0

    private var left = 0
    private var top = 0
    private var right = 0
    private var bottom = 0

    private var recyclerView: RecyclerView? = null
    private var dXReleasedAt = 0f
    private var dXModified = 0f
    private var dXReal = 0f
    private var actionState = 0
    private var isCurrentlyActive = false

    interface SwipeAction {
        fun onFullSwipe(position: Int)
    }

    fun replySwipeCallback() {
        addAnimationSteps()
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(
            0,
            if (swipeEnabled) ItemTouchHelper.LEFT else 0
        )
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return defaultValue / 2
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        setTouchListener(recyclerView)
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive) {
            touchListenerIsEnabled = true
        }
        updateViewHolderState(actionState, isCurrentlyActive)
        updateTouchData(dX)
        currentItemViewHolder = viewHolder
        super.onChildDraw(
            c,
            recyclerView,
            viewHolder,
            dXModified,
            dY,
            actionState,
            isCurrentlyActive
        )
    }

    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
    }

    private fun drawReplyArrow(c: Canvas, viewHolder: RecyclerView.ViewHolder) {
        if (currentReplyArrowState == ReplyArrowState.GONE) return
        val itemView = viewHolder.itemView
        calculateBounds(itemView)
        replyIcon.setBounds(left, top, right, bottom)
        replyIcon.draw(c)
    }

    private fun calculateBounds(itemView: View) {
        val height = itemView.bottom - itemView.top
        val centerY = itemView.top + height / 2
        val centerX = itemView.right + (dXModified * 0.5).toInt()
        isAnimating = false
        val currentSize = getCurrentIconSize()
        left = centerX - currentSize / 2
        right = centerX + currentSize / 2
        top = centerY - currentSize / 2
        bottom = centerY + currentSize / 2
        val rightMax = itemView.right - paddingRight
        val leftMax = right - fullSize
        val topMax = centerY - fullSize / 2
        val bottomMax = centerY + fullSize / 2
        if (isAnimating) recyclerView!!.postInvalidateDelayed(
            15,
            leftMax,
            topMax,
            rightMax,
            bottomMax
        )
    }

    private fun getCurrentIconSize(): Int {
        var iconSize = 0
        when (currentReplyArrowState) {
            ReplyArrowState.ANIMATING_IN -> if (currentAnimationStep < 7) {
                iconSize = (fullSize * scaleAnimationSteps[currentAnimationStep]).toInt()
                isAnimating = true
                currentAnimationStep++
            } else {
                currentAnimationStep = 7
                iconSize = fullSize
                isAnimating = false
                currentReplyArrowState = ReplyArrowState.VISIBLE
            }
            ReplyArrowState.ANIMATING_OUT -> if (currentAnimationStep > 0) {
                if (currentAnimationStep == 6) currentAnimationStep--
                iconSize = (fullSize * scaleAnimationSteps[currentAnimationStep]).toInt()
                isAnimating = true
                currentAnimationStep--
            } else {
                currentAnimationStep = 0
                iconSize = 0
                isAnimating = false
                currentReplyArrowState = ReplyArrowState.GONE
                currentItemViewHolder = null
            }
            ReplyArrowState.VISIBLE -> {
                iconSize = fullSize
                isAnimating = false
            }
        }
        return iconSize
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(recyclerView: RecyclerView) {
        if (!touchListenerIsSet) {
            recyclerView.setOnTouchListener(this)
            this.recyclerView = recyclerView
            touchListenerIsSet = true
        }
    }

    private fun updateViewHolderState(actionState: Int, isCurrentlyActive: Boolean) {
        this.actionState = actionState
        this.isCurrentlyActive = isCurrentlyActive
    }

    private fun updateTouchData(dX: Float) {
        dXReal = dX
        dXModified = updateModifiedTouchData(dX)
    }

    private fun updateModifiedTouchData(dXReal: Float): Float {
        val dXModified: Float
        val dXThreshold =
            -min(recyclerView!!.width, recyclerView!!.height) * maxSwipeDistanceRatio
        dXModified = if (isCurrentlyActive) {
            max(dXReal, dXThreshold)
        } else {
            if (dXReleasedAt < dXThreshold) {
                dXReal / dXReleasedAt * dXThreshold
            } else {
                dXReal
            }
        }
        return dXModified
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        swipeBack =
            event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && touchListenerIsEnabled) {
            when (event.action) {
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    dXReleasedAt = dXReal
                    touchListenerIsEnabled = false
                    setItemsClickable(recyclerView, true)
                    if (swipeListener != null) {
                        if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN && currentItemViewHolder != null) {
                            swipeListener!!.onFullSwipe(currentItemViewHolder!!.adapterPosition)
                        }
                    }
                    currentReplyArrowState =
                        if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN) ReplyArrowState.ANIMATING_OUT else ReplyArrowState.GONE
                }
                else -> {
                    dXReleasedAt = 0f
                    if (dXModified < -min(
                            recyclerView!!.width,
                            recyclerView!!.height
                        ) * activeSwipeDistanceRatio
                    ) {
                        if (currentReplyArrowState == ReplyArrowState.GONE) {
                            currentReplyArrowState = ReplyArrowState.ANIMATING_IN
                            currentAnimationStep = 0
                            recyclerView!!.performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                            setItemsClickable(recyclerView, false)
                        }
                    } else {
                        if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN) {
                            currentReplyArrowState = ReplyArrowState.ANIMATING_OUT
                        }
                    }
                }
            }
        }
        return false
    }

    private fun setItemsClickable(
        recyclerView: RecyclerView?,
        isClickable: Boolean
    ) {
        for (i in 0 until recyclerView!!.childCount) {
            recyclerView.getChildAt(i).isClickable = isClickable
        }
    }

    fun onDraw(c: Canvas) {
        if (currentItemViewHolder != null && currentReplyArrowState != ReplyArrowState.GONE) {
            drawReplyArrow(c, currentItemViewHolder!!)
        }
    }

    private fun addAnimationSteps() {
        scaleAnimationSteps.clear()
        scaleAnimationSteps.add(0f) //0
        scaleAnimationSteps.add(0.15f) //15ms
        scaleAnimationSteps.add(0.32f) //30ms
        scaleAnimationSteps.add(0.51f) //45ms
        scaleAnimationSteps.add(0.72f) //60ms
        scaleAnimationSteps.add(0.95f) //75ms
        scaleAnimationSteps.add(1.15f) //90ms
        scaleAnimationSteps.add(1f) //105ms/end
    }

    internal enum class ReplyArrowState {
        GONE, ANIMATING_IN, VISIBLE, ANIMATING_OUT
    }

}