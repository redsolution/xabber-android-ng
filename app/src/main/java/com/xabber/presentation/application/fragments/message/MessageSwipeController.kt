package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data.util.dp

class MessageSwipeController(
    private val context: Context,
    private val swipeControllerActions: SwipeControllerActions
) :
    ItemTouchHelper.Callback() {

    private lateinit var imageDrawable: Drawable


    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private lateinit var mView: View
    private var dX = 0f
    private var swipeBack = false
    private var startTracking = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
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

        if (mView.translationX < 130.dp || dX < this.dX) {
            super.onChildDraw(
                c,
                recyclerView,
                viewHolder,
                dX / 3,
                dY,
                actionState,
                isCurrentlyActive
            )
            this.dX = dX
            startTracking = true

        }
        currentItemViewHolder = viewHolder


    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        recyclerView.setOnTouchListener { _, event ->
            val a = dX
            swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (swipeBack) {
                if (dX == a) {
                    swipeControllerActions.showReplyUI(viewHolder.adapterPosition)

                }
            }

            false
        }
    }


}

