package com.xabber.presentation.application.fragments.chatlist

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R

class SwipeToArchiveCallback(private val adapter: ChatListAdapter) :
    ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private val offset = 20

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onChildDraw(
    c: Canvas,
    recyclerView: RecyclerView,
    viewHolder: RecyclerView.ViewHolder,
    dX: Float,
    dY: Float,
    actionState: Int,
    isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(
            c,
            recyclerView,
            viewHolder,
            dX,
            dY,
            actionState,
            isCurrentlyActive
        )

        val context = recyclerView.context
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_arcived_white)!!
        val itemView = viewHolder.itemView
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.action_with_chat_background, typedValue, true)
        val d = GradientDrawable()
        d.setColor(Color.GRAY)
        d.cornerRadius = -24f
        val background = ContextCompat.getDrawable(context, R.color.grey_400)!!
        val backgroundOffset = offset
        val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
        val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
        val iconBottom = iconTop + icon.intrinsicHeight

        if (dX < 0) {
            val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
            val iconRight = itemView.right - iconMargin
            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            background.setBounds(
                itemView.right + dX.toInt() - backgroundOffset,
                itemView.top,
                itemView.right,
                itemView.bottom
            )
        } else background.setBounds(0, 0, 0, 0)

        background.draw(c)
        icon.draw(c)
    }




    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.onSwipeChatItem(viewHolder.absoluteAdapterPosition)
    }
}
