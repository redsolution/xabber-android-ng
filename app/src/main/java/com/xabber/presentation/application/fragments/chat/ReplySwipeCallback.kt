package com.xabber.presentation.application.fragments.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.presentation.application.fragments.chat.message.SystemMessageVH
import com.xabber.presentation.application.manage.LogManager
import com.xabber.utils.dp
import kotlin.math.max
import kotlin.math.min

/**
 * [ReplySwipeCallback] is a [ItemTouchHelper.Callback] implementation that handles swipe gestures
 * in a [RecyclerView] for the purpose of displaying a reply arrow when swiping on an item.
 * It provides functionality for animating the arrow, detecting swipe actions, and invoking
 * callbacks when the swipe is completed.
 *
 * @property context The [Context] used for retrieving resources.
 * @property swipeListener A lambda function that is invoked when a full swipe action **/

class ReplySwipeCallback(
    private val context: Context,
    private val swipeListener: (Int) -> Unit
) :
    ItemTouchHelper.Callback(), OnTouchListener {
    private var currentReplyArrowState = ReplyArrowState.GONE
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
    private val fullSize = 36.dp
    private val paddingRight = 28
    private val maxSwipeDistanceRatio = 0.18f
    private val activeSwipeDistanceRatio = 0.13f

    private val currentIconSize: Int
        get() {
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
                else -> {}
            }
            return iconSize
        }

    interface SwipeAction {
        fun onFullSwipe(position: Int)
    }

    init {
        addAnimationSteps()
    }

    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return if (viewHolder is SystemMessageVH) 0 else makeMovementFlags(
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (recyclerView != null) {
            swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && touchListenerIsEnabled) {
                when (event.action) {
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                        dXReleasedAt = dXReal
                        touchListenerIsEnabled = false
                        setItemsClickable(recyclerView!!, true)
                        if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN && currentItemViewHolder != null) {
                            swipeListener(currentItemViewHolder!!.absoluteAdapterPosition)
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
                                recyclerView?.performHapticFeedback(
                                    HapticFeedbackConstants.KEYBOARD_TAP,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                setItemsClickable(recyclerView!!, false)
                            }
                        } else {
                            if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN) {
                                currentReplyArrowState = ReplyArrowState.ANIMATING_OUT
                            }
                        }
                    }
                }
            }
        } else LogManager.d("${this.javaClass}: RecyclerView not initialized")
        return false
    }

    private fun drawReplyArrow(c: Canvas, viewHolder: RecyclerView.ViewHolder) {
        if (currentReplyArrowState == ReplyArrowState.GONE) return
        val itemView = viewHolder.itemView
        calculateBounds(itemView)
        val replyIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.reply_circle)
        replyIcon?.setBounds(left, top, right, bottom)
        replyIcon?.draw(c)
    }

    private fun calculateBounds(itemView: View) {
        val height = itemView.bottom - itemView.top
        val centerY = itemView.top + height / 2
        val centerX = itemView.right + (dXModified * 0.5).toInt()
        isAnimating = false
        val currentSize = currentIconSize
        left = centerX - currentSize / 2
        right = centerX + currentSize / 2
        top = centerY - currentSize / 2
        bottom = centerY + currentSize / 2
        val rightMax = itemView.right - paddingRight
        val leftMax = right - fullSize
        val topMax = centerY - fullSize / 2
        val bottomMax = centerY + fullSize / 2
        if (isAnimating) recyclerView?.postInvalidateDelayed(
            15,
            leftMax,
            topMax,
            rightMax,
            bottomMax
        )
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
            -min(recyclerView?.width ?: 0, recyclerView?.height ?: 0) * maxSwipeDistanceRatio
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


    private fun setItemsClickable(
        recyclerView: RecyclerView,
        isClickable: Boolean
    ) {
        for (i in 0 until recyclerView.childCount) {
            recyclerView.getChildAt(i).isClickable = isClickable
        }
    }

    fun onDraw(canvas: Canvas) {
        if (currentItemViewHolder != null && currentReplyArrowState != ReplyArrowState.GONE) {
            drawReplyArrow(canvas, currentItemViewHolder!!)
        }
    }

    private fun addAnimationSteps() {
        scaleAnimationSteps.clear()
        scaleAnimationSteps.add(0f)
        scaleAnimationSteps.add(0.15f)
        scaleAnimationSteps.add(0.32f)
        scaleAnimationSteps.add(0.51f)
        scaleAnimationSteps.add(0.72f)
        scaleAnimationSteps.add(0.95f)
        scaleAnimationSteps.add(1.15f)
        scaleAnimationSteps.add(1f)
    }

    internal enum class ReplyArrowState {
        GONE, ANIMATING_IN, VISIBLE, ANIMATING_OUT
    }

}
