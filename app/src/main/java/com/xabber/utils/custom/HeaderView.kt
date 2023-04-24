package com.xabber.utils.custom

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.xabber.R

class HeaderView : FrameLayout {
    private var pullImage: ImageView? = null
    private var tvDescription: TextView? = null

    constructor(context: Context) : super(context) {
        setupViews()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setupViews()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        setupViews()
    }

    fun setText(@StringRes textRes: Int) {
        if (tvDescription != null)
            tvDescription!!.setText(textRes)
    }

    fun setTextColor(colorRes: Int) {
        tvDescription?.setTextColor(colorRes)
    }

    fun setProgressRotation(isv: Boolean) {
        pullImage = findViewById(R.id.tip)
        pullImage!!.isVisible = isv
    }

    fun setColor(color: Int, colorLight: Int, isUp: Boolean) {
        val line = findViewById<ImageView>(R.id.line)
        val tip = findViewById<ImageView>(R.id.tip)
        val slider = findViewById<ImageView>(R.id.slider)

        if (isUp) slider.setImageResource(R.drawable.ic_baseline_arrow_upward_24) else slider.setImageResource(
            R.drawable.ic_baseline_arrow_downward_24
        )
        line.setColorFilter(ContextCompat.getColor(context, colorLight))
        tip.setColorFilter(ContextCompat.getColor(context, colorLight))


        val states = arrayOf(
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled)
        )

        val colors = intArrayOf(
            ContextCompat.getColor(context, color),
            ContextCompat.getColor(context, color)
        )
        slider.backgroundTintList = ColorStateList(states, colors)
    }

    private fun setupViews() {
        setGuidanceView(R.layout.header_view)
        pullImage = findViewById(R.id.line)
        tvDescription = findViewById(R.id.tv)
    }

    private fun setGuidanceView(@LayoutRes layoutResID: Int) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(layoutResID, null) ?: return
        removeAllViews()
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(view, params)
    }

}
