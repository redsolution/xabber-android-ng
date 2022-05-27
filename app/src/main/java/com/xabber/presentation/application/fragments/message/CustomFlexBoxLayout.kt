package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.xabber.R

class CustomFlexboxLayout(context: Context, private val attrs: AttributeSet? = null) : RelativeLayout(context, attrs) {

    private lateinit var viewPartMain: TextView
    private lateinit var  viewPartSlave: View

    private val a: TypedArray @SuppressLint("CustomViewStyleable") get() = context.obtainStyledAttributes(attrs, R.styleable.CustomFlexboxLayout, 0, 0);

    private lateinit var viewPartMainLayoutParams: LayoutParams
    private var viewPartMainWidth = 0
    private var viewPartMainHeight = 0

    private lateinit var  viewPartSlaveLayoutParams: LayoutParams
    private var viewPartSlaveWidth = 0
    private var viewPartSlaveHeight = 0


   override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            viewPartMain = this.findViewById(a.getResourceId(R.styleable.CustomFlexboxLayout_viewPartMain, -1));
            viewPartSlave = this.findViewById(a.getResourceId(R.styleable.CustomFlexboxLayout_viewPartSlave, -1));
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        var widthSize = MeasureSpec.getSize(widthMeasureSpec)
        var heightSize = MeasureSpec.getSize(heightMeasureSpec)

        if (viewPartMain == null || viewPartSlave == null || widthSize <= 0) {
            return;
        }

       val availableWidth = widthSize - paddingLeft - paddingRight
       val availableHeight = heightSize - paddingTop - paddingBottom

        viewPartMainLayoutParams = viewPartMain.layoutParams as LayoutParams
        viewPartMainWidth = viewPartMain.measuredWidth + viewPartMainLayoutParams.leftMargin + viewPartMainLayoutParams.rightMargin;
        viewPartMainHeight = viewPartMain.measuredHeight + viewPartMainLayoutParams.topMargin + viewPartMainLayoutParams.bottomMargin;

        viewPartSlaveLayoutParams = viewPartSlave.layoutParams as LayoutParams
        viewPartSlaveWidth = viewPartSlave.measuredWidth + viewPartSlaveLayoutParams.leftMargin + viewPartSlaveLayoutParams.rightMargin;
        viewPartSlaveHeight = viewPartSlave.measuredHeight + viewPartSlaveLayoutParams.topMargin + viewPartSlaveLayoutParams.bottomMargin;

        val viewPartMainLineCount = viewPartMain.lineCount;
       val viewPartMainLastLineWitdh = if (viewPartMainLineCount > 0){
                viewPartMain.layout.getLineWidth(viewPartMainLineCount - 1)
                        + viewPartMainLayoutParams.rightMargin } else 0

        widthSize = paddingLeft + paddingRight
        heightSize = paddingTop + paddingBottom

      when {
          viewPartMainLastLineWitdh + viewPartSlaveWidth > availableWidth -> {
              widthSize += viewPartMainWidth
              heightSize += viewPartMainHeight + viewPartSlaveHeight;
          }
          viewPartMainWidth >= viewPartMainLastLineWitdh + viewPartSlaveWidth -> {
              widthSize += viewPartMainWidth
              heightSize += viewPartMainHeight
          }
          else -> {
              widthSize += viewPartMainLastLineWitdh + viewPartSlaveWidth
              heightSize += viewPartMainHeight
          }
      }

        this.setMeasuredDimension(widthSize, heightSize);
        super.onMeasure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY))
    }

   override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (viewPartMain == null || viewPartSlave == null) {
            return
        }

        viewPartMain.layout(
            paddingLeft,
            paddingTop,
                viewPartMain.width + paddingLeft,
                viewPartMain.height + paddingTop
        )

        viewPartSlave.layout(
                right - left - viewPartSlaveWidth - paddingRight,
                bottom - top - paddingBottom - viewPartSlaveHeight,
                right - left - paddingRight,
                bottom - top - paddingBottom
        )
    }

}
