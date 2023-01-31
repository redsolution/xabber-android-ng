package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.xabber.R

class ChatListItemLayout : ViewGroup {
    // Chat name and companions
    private var title: TextView? = null
    private var timeAndSync: LinearLayout? = null

    // Message text and companions
    private var message: TextView? = null
    private var chatData: LinearLayout? = null

    // Avatar
    private var avatar: ImageView? = null
    private var status: ImageView? = null

    // Misc
    private var colorLine: View? = null

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onFinishInflate() {
        super.onFinishInflate()
        cacheLayoutViews()
    }

    private fun cacheLayoutViews() {
        avatar = findViewById(R.id.im_avatar)
        status = findViewById(R.id.im_message_status)
        title = findViewById(R.id.tv_name)
        message = findViewById(R.id.tv_last_message)
        colorLine = findViewById(R.id.account_color_indicator)
        chatData = findViewById(R.id.chat_data)
        timeAndSync = findViewById(R.id.lin_time_and_sync)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthUsedTopLine: Int
        var widthUsedBottomLine: Int
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        if (colorLine!!.visibility != GONE) {
            measureChildWithMargins(colorLine, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }
        measureChildWithMargins(avatar, widthMeasureSpec, 0, heightMeasureSpec, 0)
        widthUsedBottomLine = getMeasuredWidthWithMargins(avatar)
        widthUsedTopLine = widthUsedBottomLine
        if (status!!.visibility != GONE) {
            measureChildWithMargins(status, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }


        // general layout look:

        // | +--+  LouieCarrot@xabber.org             11:05 |
        // | +--o  Hello, how are you?                  (1) |

        // Top Line measure     [ LouieCarrot@xabber.org             11:05 ]
        measureChildWithMargins(timeAndSync, widthMeasureSpec, widthUsedTopLine, heightMeasureSpec, 0)
     //   widthUsedTopLine += time!!.measuredWidth
        measureChildWithMargins(title, widthMeasureSpec, widthUsedTopLine, heightMeasureSpec, 0)


        // Bottom Line measure  [ Hello, how are you?                  (1) ]
            measureChildWithMargins(
                chatData,
                widthMeasureSpec,
                widthUsedBottomLine,
                heightMeasureSpec,
                0
            )
            widthUsedBottomLine += getMeasuredWidthWithMargins(chatData)

        measureChildWithMargins(
            message,
            widthMeasureSpec,
            widthUsedBottomLine,
            heightMeasureSpec,
            0
        )
        setMeasuredDimension(widthSize, heightSize)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val strictLeftPos = paddingLeft
        val strictRightPos = right - left - paddingRight
        val strictTopPos = paddingTop
        val strictBotPos = bottom - top - paddingBottom
        val height = strictBotPos - strictTopPos
        val density = resources.displayMetrics.density
        if (colorLine!= null && colorLine!!.isVisible) {
            layoutView(colorLine, 0, 1, colorLine!!.measuredWidth, colorLine!!.measuredHeight - 1)
        }
        val variableLeftPos = layoutAvatarElements(strictLeftPos, height, density)

        // timeAndSync
        layoutView(
            timeAndSync,
            strictRightPos - timeAndSync!!.measuredWidth,
            (density * 12).toInt(),
            strictRightPos,
            (density * 12).toInt() + timeAndSync!!.measuredHeight
        )


        // chatData
            layoutView(
               chatData,
                strictRightPos - chatData!!.measuredWidth,
                strictBotPos - (density * 12).toInt() - chatData!!.measuredHeight,
                strictRightPos,
                strictBotPos - (density * 12).toInt()
            )

        // chat name
        layoutView(
            title,
            variableLeftPos, (density * 12).toInt(),
            variableLeftPos + title!!.measuredWidth,
            (density * 12).toInt() + title!!.measuredHeight
        )

        // chat message
        layoutView(
            message,
            variableLeftPos,
            strictBotPos - (density * 12).toInt() - message!!.measuredHeight,
            variableLeftPos + message!!.measuredWidth,
            strictBotPos - (density * 12).toInt()
        )
    }

    private fun layoutView(view: View?, left: Int, top: Int, right: Int, bottom: Int) {
        view!!.layout(left, top, right, bottom)
    }

    private fun layoutAvatarElements(left: Int, containerHeight: Int, density: Float): Int {
        val margins = avatar!!.layoutParams as MarginLayoutParams
        val leftWithMargins = left + margins.leftMargin
        val avatarHeight = avatar!!.measuredHeight
        val avatarWidth = avatar!!.measuredWidth
        // avatar is centered vertically
        val topWithMargins =
            (containerHeight - (avatarHeight + margins.topMargin + margins.bottomMargin)) / 2
        avatar!!.layout(
            leftWithMargins,
            topWithMargins,
            leftWithMargins + avatarWidth,
            topWithMargins + avatarHeight
        )
        if (status!!.visibility != GONE) {
            layoutView(
                status,
                leftWithMargins + avatarWidth - status!!.measuredWidth,
                topWithMargins + avatarHeight - status!!.measuredHeight,
                leftWithMargins + avatarWidth,
                topWithMargins + avatarHeight
            )
        }
        return leftWithMargins + avatarWidth + margins.rightMargin
    }

    private fun layoutViewCenterVertical(
        view: View?,
        left: Int,
        width: Int,
        height: Int,
        containerHeight: Int
    ): Int {
        val margins = view!!.layoutParams as MarginLayoutParams
        val leftWithMargins = left + margins.leftMargin
        val topWithMargins =
            (containerHeight - (height + margins.topMargin + margins.bottomMargin)) / 2
        view.layout(
            leftWithMargins,
            topWithMargins,
            leftWithMargins + width,
            topWithMargins + height
        )
        return leftWithMargins + width + margins.rightMargin
    }

    private fun getMeasuredWidthWithMargins(view: View?): Int {
        val lp = view!!.layoutParams as MarginLayoutParams
        return view.measuredWidth + lp.leftMargin + lp.rightMargin
    }

    private fun getMeasuredHeightWithMargins(view: View): Int {
        val lp = view.layoutParams as MarginLayoutParams
        return view.measuredHeight + lp.topMargin + lp.bottomMargin
    }

    /**
     * Validates if a set of layout parameters is valid for a child this ViewGroup.
     */
    override fun checkLayoutParams(p: LayoutParams): Boolean {
        return p is MarginLayoutParams
    }

    /**
     * @return A set of default layout parameters when given a child with no layout parameters.
     */
    override fun generateDefaultLayoutParams(): LayoutParams {
        return MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    /**
     * @return A set of layout parameters created from attributes passed in XML.
     */
    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    /**
     * Called when [.checkLayoutParams] fails.
     *
     * @return A set of valid layout parameters for this ViewGroup that copies appropriate/valid
     * attributes from the supplied, not-so-good-parameters.
     */
    override fun generateLayoutParams(p: LayoutParams): LayoutParams {
        return generateDefaultLayoutParams()
    }
}
