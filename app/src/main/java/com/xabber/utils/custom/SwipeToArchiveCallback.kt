package com.xabber.utils.custom

import android.graphics.Canvas
import android.util.TypedValue
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.presentation.application.fragments.chatlist.ChatListAdapter
import com.xabber.utils.dp

class SwipeToArchiveCallback(private val adapter: ChatListAdapter) :
    ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private val offset = 20

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) =
        0.3f   // процент смешения при котором произойдет Swipe

    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder) = 300f

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
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_arcived)!!
        val itemView = viewHolder.itemView
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.action_with_chat_background, typedValue, true)
        val background = ContextCompat.getDrawable(context, R.color.transparent)!!
        val backgroundOffset = offset
        val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
        val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
        val iconBottom = iconTop + icon.intrinsicHeight

        val cornerRadius = if (dX < 0) 4.dp.toFloat() else 0f

        // Устанавливаем значение cardCornerRadius для CardView
        (viewHolder.itemView as CardView).radius = cornerRadius

        if (dX < 0) {
            val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
            val iconRight = itemView.right - iconMargin
            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            background.setBounds(
                itemView.right + dX.toInt() - backgroundOffset,
                itemView.top,
                itemView.right + dX.toInt(),
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
