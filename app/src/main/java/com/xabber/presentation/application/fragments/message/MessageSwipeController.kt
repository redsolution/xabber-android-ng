package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import kotlin.math.abs
import kotlin.math.min

class MessageSwipeController(private val context: Context, private val swipeControllerActions: SwipeControllerActions) :
    ItemTouchHelper.Callback() {

    private lateinit var imageDrawable: Drawable


    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private lateinit var mView: View
    private var dX = 0f

    private var replyButtonProgress: Float = 0.toFloat()
    private var lastReplyButtonAnimationTime: Long = 0
    private var swipeBack = false
    private var isVibrate = false
    private var startTracking = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        mView = viewHolder.itemView
        imageDrawable = context.getDrawable(R.drawable.ic_reply)!!
        return ItemTouchHelper.Callback.makeMovementFlags(ACTION_STATE_IDLE, LEFT)

    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {

        if (actionState == ACTION_STATE_SWIPE) {
            setTouchListener(recyclerView, viewHolder)
        }

        if (mView.translationX < convertTodp(130) || dX < this.dX) {
            super.onChildDraw(c, recyclerView, viewHolder, dX/3, dY, actionState, isCurrentlyActive)
            this.dX = dX
            startTracking = true
        }
        currentItemViewHolder = viewHolder
        drawReplyButton(Canvas())
        swipeControllerActions.showReplyUI(viewHolder.adapterPosition)

    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        recyclerView.setOnTouchListener { _, event ->
            swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (swipeBack) {
                if (abs(mView.translationX) >= this@MessageSwipeController.convertTodp(50)) {

                    drawReplyButton(Canvas())

                }
            }

                false
            }
        }


    private fun drawReplyButton(canvas: Canvas) {
     //   if (currentItemViewHolder == null) {
     //       return
    //    }

//        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//        if (Build.VERSION.SDK_INT >= 26) {
//            v.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
//        } else {
//            v.vibrate(10)
//
//        }

        val translationX = mView.translationX
        val newTime = System.currentTimeMillis()
        val dt = min(17, newTime - lastReplyButtonAnimationTime)
        lastReplyButtonAnimationTime = newTime
        val showing = translationX >= convertTodp(30)
        if (showing) {
            if (replyButtonProgress > 1.0f) {
                replyButtonProgress -= dt / 180.0f
                if (replyButtonProgress < 1.0f) {
                    replyButtonProgress = 1.0f
                } else {
                    mView.invalidate()
                }
            }
        } else if (translationX >= 0.0f) {
            replyButtonProgress = 0f
            startTracking = false
            isVibrate = false
        } else {
            if (replyButtonProgress < 0.0f) {
                replyButtonProgress += dt / 180.0f
                if (replyButtonProgress < 0.1f) {
                    replyButtonProgress = 0f
                } else {
                    mView.invalidate()
                }
            }
        }
        val alpha: Int
        val scale: Float
        if (showing) {
            scale = if (replyButtonProgress <= 0.8f) {
                1.2f * (replyButtonProgress / 0.8f)
            } else {
                1.2f - 0.2f * ((replyButtonProgress + 0.8f) / 0.2f)
            }
            alpha = Math.min(255f, 255 * (replyButtonProgress / 0.8f)).toInt()
        } else {
            scale = replyButtonProgress
            alpha = Math.min(255f, 255 * replyButtonProgress).toInt()
        }


        imageDrawable.alpha = alpha
        if (startTracking) {
            if (!isVibrate && mView.translationX >= convertTodp(100)) {
                mView.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                isVibrate = true
            }
        }

        val x: Int = if (mView.translationX > convertTodp(130)) {
            convertTodp(130) / 4
        } else {
            (mView.translationX / 4).toInt()
        }

        val y = (mView.top + mView.measuredHeight / 3).toFloat()
        imageDrawable.colorFilter =
            PorterDuffColorFilter(ContextCompat.getColor(context, R.color.cyan_100), PorterDuff.Mode.MULTIPLY)

        imageDrawable.setBounds(
            (x + convertTodp(18) * scale).toInt(),
            (y + convertTodp(18) * scale).toInt(),
            (x - convertTodp(18) * scale).toInt(),
            (y - convertTodp(18) * scale).toInt()
        )
       imageDrawable.draw(canvas)
        imageDrawable.setBounds(
            (x + convertTodp(12) * scale).toInt(),
            (y + convertTodp(11) * scale).toInt(),
            (x - convertTodp(12) * scale).toInt(),
            (y - convertTodp(10) * scale).toInt()
        )
        imageDrawable.draw(canvas)
        imageDrawable.alpha = 255
    }

    private fun convertTodp(pixel: Int): Int {
        return AndroidUtils.dp(pixel.toFloat(), context)
    }

}

