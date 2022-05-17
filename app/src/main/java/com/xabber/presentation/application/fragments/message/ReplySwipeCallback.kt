package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.content.res.Resources.getSystem
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R

class ReplySwipeCallback(private val context: Context, swipeControllerActions: SwipeControllerActions
) :
    ItemTouchHelper.Callback(), View.OnTouchListener{


    private val swipeListener  = swipeControllerActions

    private var currentReplyArrowState = ReplyArrowState.GONE
    private val fullSize = dipToPxFloat(24f, context)
    private val paddingRight = dipToPxFloat(12f, context)
    private val replyIcon: Drawable =
        ResourcesCompat.getDrawable(context.resources, R.drawable.reply, null)!!



    private val MAX_SWIPE_DISTANCE_RATIO = 0.18f;
    private val ACTIVE_SWIPE_DISTANCE_RATIO = 0.13f;
    private var swipeBack = false
    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private var touchListenerIsSet = false
    private var touchListenerIsEnabled = false
    private var swipeEnabled = true
    var isAnimating = false
    private var currentAnimationStep = 0
    private val scaleAnimationSteps : ArrayList<Float> get() = addAnimationSteps()

    private var left = 0
    private var top = 0
    private var right = 0
    private var bottom = 0

    private var recyclerView: RecyclerView? = null
    private var dXReleasedAt: Float = 0f
    private var dXModified = 0f
    private var dXReal = 0f
    private var actionState = 0
    private var isCurrentlyActive = false

    interface SwipeAction {
        fun onFullSwipe(position: Int)
    }


    private fun addAnimationSteps() : ArrayList<Float> {
        val sc = ArrayList<Float>()
        sc.clear()
        sc.add(0f) //0
        sc.add(0.15f) //15ms
        sc.add(0.32f) //30ms
        sc.add(0.51f) //45ms
        sc.add(0.72f) //60ms
        sc.add(0.95f) //75ms
        sc.add(1.15f) //90ms
        sc.add(1f) //105ms/end
        return sc
    }

    enum class ReplyArrowState {
        GONE,
        ANIMATING_IN,
        VISIBLE,
        ANIMATING_OUT {

        }
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        //   if (viewHolder is SystemMessageVH) return 0;
        return makeMovementFlags(0, ItemTouchHelper.LEFT)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

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
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        setTouchListener(recyclerView)

        if (actionState == ACTION_STATE_SWIPE && isCurrentlyActive) {
            touchListenerIsEnabled = true;
        }

        updateViewHolderState(actionState, isCurrentlyActive);
        updateTouchData(dX);
        currentItemViewHolder = viewHolder;
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
    }


    private fun updateViewHolderState(actionState: Int, isCurrentlyActive: Boolean) {
        this.actionState = actionState
        this.isCurrentlyActive = isCurrentlyActive
    }

    private fun drawReplyArrow(c: Canvas, viewHolder: RecyclerView.ViewHolder) {
        if (currentReplyArrowState == ReplyArrowState.GONE) return;

        val itemView = viewHolder.itemView;

        calculateBounds(itemView);

        replyIcon?.setBounds(left, top, right, bottom);
        replyIcon?.draw(c);
    }

    private fun calculateBounds(itemView: View) {
        var height = itemView.bottom - itemView.getTop();
        var centerY = itemView.top + height / 2;
        var centerX = itemView.right + (dXModified * 0.5).toInt()
        isAnimating = false;

        var currentSize = getCurrentIconSize();

        left = centerX - currentSize / 2;
        right = centerX + currentSize / 2;

        top = centerY - currentSize / 2;
        bottom = centerY + currentSize / 2;

        val rightMax = itemView.getRight() - paddingRight;
        val leftMax = right - fullSize;
        val topMax = centerY - fullSize / 2;
        val bottomMax = centerY + fullSize / 2;

        if (isAnimating)
            recyclerView?.postInvalidateDelayed(
                15, leftMax.toInt(),
                topMax.toInt(), rightMax.toInt(), bottomMax.toInt()
            )
    }

    private fun getCurrentIconSize(): Int {
        var iconSize = 0
        when (currentReplyArrowState) {
            ReplyArrowState.ANIMATING_IN -> {
                if (currentAnimationStep < 7) {
                    iconSize = (fullSize * scaleAnimationSteps[currentAnimationStep]).toInt()
                    isAnimating = true
                    currentAnimationStep++
                } else {
                    currentAnimationStep = 7
                    iconSize = fullSize.toInt()
                    isAnimating = false
                    currentReplyArrowState = ReplyArrowState.VISIBLE
                }
            }
            ReplyArrowState.ANIMATING_OUT -> {
                if (currentAnimationStep > 0) {
                    if (currentAnimationStep == 6) currentAnimationStep--;//skip enlarged frame of the animation.
                    iconSize = (fullSize * scaleAnimationSteps[currentAnimationStep]).toInt()
                    isAnimating = true
                    currentAnimationStep--
                } else {
                    currentAnimationStep = 0
                    iconSize = 0
                    isAnimating = false
                    currentReplyArrowState = ReplyArrowState.GONE;
                    currentItemViewHolder = null;
                }
            }
            ReplyArrowState.VISIBLE -> {
                iconSize = fullSize.toInt()
                isAnimating = false
            }
        }
        return iconSize
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(_recyclerView: RecyclerView) {
        if (!touchListenerIsSet) {
            recyclerView?.setOnTouchListener(this);
            recyclerView = _recyclerView;
            touchListenerIsSet = true;
        }
    }

    private fun updateTouchData(dX: Float) {
        this.dXReal = dX
        this.dXModified = updateModifiedTouchData(dX)
    }

    private fun updateModifiedTouchData(dXReal: Float): Float {
        var dXModified = 0f
        var dXThreshold = -Math.min(
            recyclerView!!.getWidth(),
            recyclerView!!.getHeight()
        ) * MAX_SWIPE_DISTANCE_RATIO;

        if (isCurrentlyActive) {
            //View is being actively moved by the user.

            dXModified = Math.max(dXReal, dXThreshold);
        } else {
            //View is in the restoration phase

            if (dXReleasedAt < dXThreshold) {
                //the real delta of finger movement at the time of "release" is bigger than the threshold(max swipe distance of the view).
                //e.g. finger moved 500px, while the view stopped at the threshold of 300px
                //
                //by doing this we can "appropriate" original view's animation's positional data
                //instead of overriding it until it becomes lower than the threshold
                dXModified = (dXReal / dXReleasedAt) * dXThreshold;
            } else {
                //the real delta of finger movement at the time of "release" is smaller than the threshold, so dXModified and dXReal should be equal.
                dXModified = dXReal;
            }
        }
        return dXModified;
    }

      private fun setItemsClickable( recyclerView: RecyclerView,
                                  isClickable: Boolean) {
        for (i in 0 until recyclerView.childCount) {
            recyclerView.getChildAt(i).isClickable = isClickable;
        }
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        swipeBack =
            event!!.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP;

        if (actionState == ACTION_STATE_SWIPE && touchListenerIsEnabled) {
            when (event.action) {
                MotionEvent.ACTION_CANCEL -> {}
                MotionEvent.ACTION_UP -> {
                    dXReleasedAt = dXReal
                    touchListenerIsEnabled = false;
                    recyclerView?.let { setItemsClickable(it, true) }

                    if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN && currentItemViewHolder != null) {
                        swipeListener.showReplyUI(currentItemViewHolder!!.adapterPosition);

                    }
                    if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN) {
                        currentReplyArrowState = ReplyArrowState.ANIMATING_OUT
                    } else {
                        currentReplyArrowState = ReplyArrowState.GONE
                    }
                }
                else -> {
                    dXReleasedAt = 0f
                    if (dXModified < -Math.min(
                            recyclerView!!.getWidth(),
                            recyclerView!!.getHeight()
                        ) * ACTIVE_SWIPE_DISTANCE_RATIO
                    ) {
                        if (currentReplyArrowState == ReplyArrowState.GONE) {
                            currentReplyArrowState = ReplyArrowState.ANIMATING_IN;
                            currentAnimationStep = 0;
                            recyclerView!!.performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            );
                            setItemsClickable(recyclerView!!, false);
                            //if (drawThread == null) {
                            //    Thread drawThread = new Thread(new DrawThread(), "drawThread");
                            //    drawThread.start();
                            //}
                        }
                    } else {
                        if (currentReplyArrowState == ReplyArrowState.VISIBLE || currentReplyArrowState == ReplyArrowState.ANIMATING_IN) {
                            currentReplyArrowState = ReplyArrowState.ANIMATING_OUT;
                        }
                    }

                }
            }
        }
            return false


        }


}


