package com.xabber.presentation.application.fragments.chat

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.presentation.application.fragments.chat.message.SystemMessageMessageVH
import kotlin.math.max
import kotlin.math.min

class ReplySwipeCallback(
    private val replyIcon: Drawable,
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

    interface SwipeAction {
        fun onFullSwipe(position: Int)
    }

    init {
        addAnimationSteps()
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return if (viewHolder is SystemMessageMessageVH) 0 else makeMovementFlags(
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
        val currentSize = currentIconSize
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

    //skip enlarged frame of the animation.
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
                    if (currentAnimationStep == 6) currentAnimationStep-- //skip enlarged frame of the animation.
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
            -min(recyclerView!!.width, recyclerView!!.height) * MAX_SWIPE_DISTANCE_RATIO
        dXModified = if (isCurrentlyActive) {
            //View is being actively moved by the user.
            max(dXReal, dXThreshold)
        } else {
            //View is in the restoration phase
            if (dXReleasedAt < dXThreshold) {
                //the real delta of finger movement at the time of "release" is bigger than the threshold(max swipe distance of the view).
                //e.g. finger moved 500px, while the view stopped at the threshold of 300px
                //
                //by doing this we can "appropriate" original view's animation's positional data
                //instead of overriding it until it becomes lower than the threshold
                dXReal / dXReleasedAt * dXThreshold
            } else {
                //the real delta of finger movement at the time of "release" is smaller than the threshold, so dXModified and dXReal should be equal.
                dXReal
            }
        }
        return dXModified
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        swipeBack =
            event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && touchListenerIsEnabled) {
            when (event.action) {
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    dXReleasedAt = dXReal
                    touchListenerIsEnabled = false
                    setItemsClickable(recyclerView, true)
                    if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN && currentItemViewHolder != null) {
                        swipeListener(currentItemViewHolder!!.absoluteAdapterPosition)
                    }
                    currentReplyArrowState =
                        if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN) ReplyArrowState.ANIMATING_OUT else ReplyArrowState.GONE
                }
                else -> {
                    //We set max swipe distance as 0.2f, but since we don't want the user to drag
                    //each message all the way to the end for a reply, the "active" reply state starts at 0.15f,
                    //and is accompanied by the haptic feedback and the appearance of the reply icon.
                    //animations soon?
                    dXReleasedAt = 0f
                    if (dXModified < -min(
                            recyclerView!!.width,
                            recyclerView!!.height
                        ) * ACTIVE_SWIPE_DISTANCE_RATIO
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

    companion object {
        private const val fullSize = 80
        private const val paddingRight = 28
        private const val MAX_SWIPE_DISTANCE_RATIO = 0.18f
        private const val ACTIVE_SWIPE_DISTANCE_RATIO = 0.13f
    }
}