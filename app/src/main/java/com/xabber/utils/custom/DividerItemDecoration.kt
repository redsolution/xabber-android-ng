package com.xabber.utils.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.xabber.R
import com.xabber.presentation.application.fragments.chatlist.ChatListBaseFragment.ChatListAvatarState
import com.xabber.presentation.application.manage.LogManager
import kotlin.math.roundToInt

class DividerItemDecoration(context: Context, orientation: Int) : ItemDecoration() {
    private var mDivider: Drawable?
    private var skipDividerOnLastItem = false
    private var chatListOffsetMode = ChatListAvatarState.NOT_SPECIFIED
    private var mOrientation = 0
    private val mBounds = Rect()

    init {
        val a = context.obtainStyledAttributes(ATTRS)
        mDivider = a.getDrawable(0)
        if (mDivider == null) {
            LogManager.d("@android:attr/listDivider was not set in the theme used for this "
                        + "DividerItemDecoration. Please set that attribute all call setDrawable()")
        }
        a.recycle()
        setOrientation(orientation)
    }

    fun skipDividerOnLastItem(skip: Boolean) {
        skipDividerOnLastItem = skip
    }

    fun setChatListOffsetMode(offsetMode: ChatListAvatarState) {
        chatListOffsetMode = offsetMode
    }

    fun setDrawable(drawable: Drawable) {
        mDivider = drawable
    }

    private fun setOrientation(orientation: Int) {
        require(!(orientation != HORIZONTAL && orientation != VERTICAL)) { "Invalid orientation. It should be either HORIZONTAL or VERTICAL" }
        mOrientation = orientation
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager == null || mDivider == null) {
            return
        }
        if (mOrientation == VERTICAL) {
            drawVertical(c, parent)
        } else {
            drawHorizontal(c, parent)
        }
    }

    private fun drawVertical(canvas: Canvas, parent: RecyclerView) {
        canvas.save()
        val left: Int
        val right: Int
        var tempLeft: Int
        if (parent.clipToPadding) {
            tempLeft = parent.paddingLeft
            right = parent.width - parent.paddingRight
            canvas.clipRect(
                tempLeft, parent.paddingTop, right,
                parent.height - parent.paddingBottom
            )
        } else {
            tempLeft = 0
            right = parent.width
        }
        if (chatListOffsetMode !== ChatListAvatarState.NOT_SPECIFIED) {
            if (chatListOffsetMode === ChatListAvatarState.SHOW_AVATARS) {
                tempLeft += (parent.context.resources.displayMetrics.density * 72f).toInt()
            } else if (chatListOffsetMode === ChatListAvatarState.DO_NOT_SHOW_AVATARS) {
                tempLeft += (parent.context.resources.displayMetrics.density * 36f).toInt()
            }
        }
        left = tempLeft
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            if (skipDividerOnLastItem) {
                if (parent.adapter != null)
                    if (parent.getChildAdapterPosition(child) == parent.adapter!!.itemCount - 1)
                        continue
            }
            parent.getDecoratedBoundsWithMargins(child, mBounds)
            val bottom = mBounds.bottom + child.translationY.roundToInt()
            val top =
                bottom - 1 //mDivider.getIntrinsicHeight()
            mDivider?.setBounds(left, top, right, bottom)
            mDivider?.draw(canvas)
        }
        canvas.restore()
    }

    private fun drawHorizontal(canvas: Canvas, parent: RecyclerView) {
        canvas.save()
        val top: Int
        val bottom: Int
        if (parent.clipToPadding) {
            top = parent.paddingTop
            bottom = parent.height - parent.paddingBottom
            canvas.clipRect(
                parent.paddingLeft, top,
                parent.width - parent.paddingRight, bottom
            )
        } else {
            top = 0
            bottom = parent.height
        }
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            if (skipDividerOnLastItem) {
                if (parent.adapter != null)
                    if (parent.getChildAdapterPosition(child) == parent.adapter!!.itemCount - 1) {
                        continue
                    }
            }
            parent.layoutManager?.getDecoratedBoundsWithMargins(child, mBounds)
            val right = mBounds.right + child.translationX.roundToInt()
            val left = if (mDivider != null) right - mDivider!!.intrinsicWidth else right
            mDivider?.setBounds(left, top, right, bottom)
            mDivider?.draw(canvas)
        }
        canvas.restore()
    }

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (mDivider == null) {
            outRect.setEmpty()
            return
        }
        if (skipDividerOnLastItem) {
            if (parent.adapter != null)
                if (parent.getChildAdapterPosition(view) == parent.adapter!!.itemCount - 1) {
                    outRect.setEmpty()
                    return
                }
        }
        if (chatListOffsetMode !== ChatListAvatarState.NOT_SPECIFIED) { // i.e. we have specified the offset mode, meaning this is ChatList
            outRect.setEmpty()
            return
        }
        if (mOrientation == VERTICAL && mDivider != null) {
            outRect[0, 0, 0] = mDivider!!.intrinsicHeight
        } else {
            outRect[0, 0, mDivider!!.intrinsicWidth] = 0
        }
    }

    companion object {
        const val HORIZONTAL = LinearLayout.HORIZONTAL
        const val VERTICAL = LinearLayout.VERTICAL
        private val ATTRS = intArrayOf(R.attr.standard_divider_drawable)
    }
}
