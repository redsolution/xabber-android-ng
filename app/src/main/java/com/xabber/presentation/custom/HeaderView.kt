package com.xabber.presentation.custom

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
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

    private fun setupViews() {
        setGuidanceView(R.layout.anim_view)
        pullImage = findViewById(R.id.line)
        tvDescription = findViewById(R.id.tv)
//        this.setPadding(0, 20, 0, 20)
//        pullImage = ImageView(context)
//        pullImage?.setImageResource(R.drawable.ic_baseline_arrow_downward_24)
//        pullImage?.setBackgroundColor(Color.TRANSPARENT)
//        val imageParams = LayoutParams(
//            24,
//           24,
//            Gravity.START or Gravity.BOTTOM
//        )
//        imageParams.marginStart = 30
//        addView(pullImage, imageParams)
//
//        tvDescription = TextView(context)
//        val textViewParams = LayoutParams(
//            LayoutParams.WRAP_CONTENT,
//            LayoutParams.WRAP_CONTENT,
//            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
//        )
//        tvDescription?.textSize = 20f
//        tvDescription?.gravity = Gravity.CENTER
//        addView(tvDescription, textViewParams)
    }

    fun setGuidanceView(view: View?) {
        if (view == null) return
        removeAllViews()
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(view, params)
    }

    fun setGuidanceView(@LayoutRes layoutResID: Int) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(layoutResID, null) ?: return
        removeAllViews()
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(view, params)
    }

    fun setText(text: String?) {
        tvDescription = findViewById(R.id.tv)
        tvDescription?.text = text
    }

    fun setTextColor(colorRes: Int) {
        tvDescription?.setTextColor(colorRes)
    }

    fun setTextSize(size: Float) {
        tvDescription?.textSize = size
    }

    fun startAnimation() {
        pullImage?.height?.plus(100)
        //   if (circleProgressBar != null) circleProgressBar!!.start()
    }

    fun setStartEndTrim(startAngle: Float, endAngle: Float) {
        //    if (circleProgressBar != null) circleProgressBar!!.setStartEndTrim(startAngle, endAngle)
    }

    fun stopAnimation() {
        //  if (circleProgressBar != null) circleProgressBar!!.stop()
    }

    fun setProgressRotation() {
        Log.d("ppp", "progress $rotation")
pullImage = findViewById(R.id.slider)
       // setRadius(8f)
//if (rotation > 150) {
//    val a = rotation * 0.008
//    pullImage!!.animate().scaleY(a.toFloat())
//    pullImage!!.animate().translationY(-rotation/1f).start()

       pullImage!!.isVisible = true
}
    //   pullImag(rotation/100)

        //  if (circleProgressBar != null) circleProgressBar!!.animation.


    fun setHeight() {
        //    circleProgressBar!!.height = circleProgressBar!!.height
    }

    fun setRadius(radius: Float) {
        pullImage = findViewById(R.id.line)

    }

}
