package com.xabber.presentation.custom

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Build
import android.util.AttributeSet
import android.view.animation.Animation
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.xabber.R

class SHCircleProgressBar : AppCompatImageView {
    private var mListener: Animation.AnimationListener? = null
    private var mShadowRadius = 0
    private var mBackGroundColor = 0
    private var mProgressColor = 0
    private var mProgressStokeWidth = 0
    private var mArrowWidth = 0
    private var mArrowHeight = 0
    private var mProgress = 0
    var max = 0
    private var mDiameter = 0
    private var mInnerRadius = 0
    var isShowArrow = false
    private var mProgressDrawable: Drawable? = null
    private var mBgCircle: ShapeDrawable? = null
    private var mCircleBackgroundEnabled = false
    private var mColors = intArrayOf(Color.BLACK)

    constructor(context: Context) : super(context) {
        init(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs, defStyleAttr)
    }

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.SHCircleProgressBar, defStyleAttr, 0
        )
        val density = getContext().resources.displayMetrics.density
        mBackGroundColor = a.getColor(
            R.styleable.SHCircleProgressBar_background_color, DEFAULT_CIRCLE_BG_LIGHT
        )
        mProgressColor = a.getColor(
            R.styleable.SHCircleProgressBar_progress_color, DEFAULT_CIRCLE_COLOR
        )
        mColors = intArrayOf(mProgressColor)
        mInnerRadius = a.getDimensionPixelOffset(
            R.styleable.SHCircleProgressBar_inner_radius, -1
        )
        mProgressStokeWidth = a.getDimensionPixelOffset(
            R.styleable.SHCircleProgressBar_progress_stoke_width,
            (STROKE_WIDTH_LARGE * density).toInt()
        )
        mArrowWidth = a.getDimensionPixelOffset(
            R.styleable.SHCircleProgressBar_arrow_width, -1
        )
        mArrowHeight = a.getDimensionPixelOffset(
            R.styleable.SHCircleProgressBar_arrow_height, -1
        )
        isShowArrow = a.getBoolean(R.styleable.SHCircleProgressBar_show_arrow, true)
        mCircleBackgroundEnabled =
            a.getBoolean(R.styleable.SHCircleProgressBar_enable_circle_background, true)
        mProgress = a.getInt(R.styleable.SHCircleProgressBar_progress, 0)
        max = a.getInt(R.styleable.SHCircleProgressBar_max, 100)
        a.recycle()
        mProgressDrawable = ContextCompat.getDrawable(context, R.drawable.circle_green)
        super.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.circle_green))
    }

    fun setProgressBackGroundColor(color: Int) {
        mBackGroundColor = color
    }

    private fun elevationSupported(): Boolean {
        return Build.VERSION.SDK_INT >= 21
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!elevationSupported()) {
            setMeasuredDimension(
                measuredWidth + mShadowRadius * 2, measuredHeight
                        + mShadowRadius * 2
            )
        }
    }

    var progressStokeWidth: Int
        get() = mProgressStokeWidth
        set(mProgressStokeWidth) {
            val density = context.resources.displayMetrics.density
            this.mProgressStokeWidth = (mProgressStokeWidth * density).toInt()
        }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val density = context.resources.displayMetrics.density
        mDiameter = Math.min(measuredWidth, measuredHeight)
        if (mDiameter <= 0) {
            mDiameter = density.toInt() * DEFAULT_CIRCLE_DIAMETER
        }
        if (background == null && mCircleBackgroundEnabled) {
            val shadowYOffset = (density * Y_OFFSET).toInt()
            val shadowXOffset = (density * X_OFFSET).toInt()
            mShadowRadius = (density * SHADOW_RADIUS).toInt()
            if (elevationSupported()) {
                mBgCircle = ShapeDrawable(OvalShape())
                ViewCompat.setElevation(this, SHADOW_ELEVATION * density)
            } else {
                val oval: OvalShape = OvalShadow(mShadowRadius, mDiameter - mShadowRadius * 2)
                mBgCircle = ShapeDrawable(oval)
                ViewCompat.setLayerType(this, LAYER_TYPE_SOFTWARE, mBgCircle!!.paint)
                mBgCircle!!.paint.setShadowLayer(
                    mShadowRadius.toFloat(), shadowXOffset.toFloat(), shadowYOffset.toFloat(),
                    KEY_SHADOW_COLOR
                )
                val padding = mShadowRadius
                // set padding so the inner image sits correctly within the shadow.
                setPadding(padding, padding, padding, padding)
            }
            mBgCircle!!.paint.color = mBackGroundColor
            setBackgroundDrawable(mBgCircle)
        }
//        mProgressDrawable!!.setBackgroundColor(mBackGroundColor)
//        mProgressDrawable!!.setColorSchemeColors(*mColors)
//        if (isShowArrow) {
//            mProgressDrawable!!.setArrowScale(1f)
//            mProgressDrawable!!.showArrow(true)
        }
//        super.setImageDrawable(null)
//        super.setImageDrawable(mProgressDrawable)
//        mProgressDrawable!!.alpha = 255
//        if (visibility == VISIBLE) {
//            mProgressDrawable!!.setStartEndTrim(0f, 0.8.toFloat())
//        }


    fun setAnimationListener(listener: Animation.AnimationListener?) {
        mListener = listener
    }

    public override fun onAnimationStart() {
        super.onAnimationStart()
        if (mListener != null) {
            mListener!!.onAnimationStart(animation)
        }
    }

    public override fun onAnimationEnd() {
        super.onAnimationEnd()
        if (mListener != null) {
            mListener!!.onAnimationEnd(animation)
        }
    }

    /**
     * Set the colors used in the progress animation. The first
     * color will also be the color of the bar that grows in response to a user
     * swipe gesture.
     *
     * @param colors
     */
    fun setColorSchemeColors(vararg colors: Int) {
        mColors = colors
        if (mProgressDrawable != null) {
         //   mProgressDrawable!!.setColorSchemeColors(*colors)
        }
    }

    /**
     * Update the background color of the mBgCircle image view.
     */
    fun setBackgroundColorResource(colorRes: Int) {
        if (background is ShapeDrawable) {
            val res = resources
            (background as ShapeDrawable).paint.color = res.getColor(colorRes)
        }
    }

    override fun setBackgroundColor(color: Int) {
        if (background is ShapeDrawable) {
            val res = resources
            (background as ShapeDrawable).paint.color = color
        }
    }

    var progress: Int
        get() = mProgress
        set(progress) {
            if (max > 0) {
                mProgress = progress
            }
            invalidate()
        }

    fun circleBackgroundEnabled(): Boolean {
        return mCircleBackgroundEnabled
    }

    fun setCircleBackgroundEnabled(enableCircleBackground: Boolean) {
        mCircleBackgroundEnabled = enableCircleBackground
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mProgressDrawable != null) {
         //   mProgressDrawable!!.stop()
            mProgressDrawable!!.setVisible(visibility == VISIBLE, false)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (mProgressDrawable != null) {
        //    mProgressDrawable!!.stop()
            mProgressDrawable!!.setVisible(false, false)
        }
    }

    private inner class OvalShadow(shadowRadius: Int, circleDiameter: Int) :
        OvalShape() {
        private val mRadialGradient: RadialGradient
        private val mShadowRadius: Float
        private val mShadowPaint: Paint
        private val mCircleDiameter: Float

        init {
            mShadowPaint = Paint()
            mShadowRadius = shadowRadius.toFloat()
            mCircleDiameter = circleDiameter.toFloat()

            mRadialGradient = RadialGradient(mCircleDiameter / 2, mCircleDiameter / 2,
                mShadowRadius, intArrayOf(
                    FILL_SHADOW_COLOR, Color.TRANSPARENT
                ), null, Shader.TileMode.CLAMP
            )
            mShadowPaint.shader = mRadialGradient
        }

        override fun draw(canvas: Canvas, paint: Paint) {
            val viewWidth = this@SHCircleProgressBar.width
            val viewHeight = this@SHCircleProgressBar.height
            canvas.drawCircle(
                (viewWidth / 2).toFloat(),
                (viewHeight / 2).toFloat(),
                (mCircleDiameter / 2 + mShadowRadius).toFloat(),
                mShadowPaint
            )
            canvas.drawCircle(
                (viewWidth / 2).toFloat(),
                (viewHeight / 2).toFloat(),
                (mCircleDiameter / 2).toFloat(),
                paint
            )
        }
    }

    /**
     * 开始动画
     */
    fun start() {
      //  mProgressDrawable!!.start()
    }

    /**
     * 设置动画起始位置
     */
    fun setStartEndTrim(startAngle: Float, endAngle: Float) {
   //     mProgressDrawable!!.setStartEndTrim(startAngle, endAngle)
    }

    /**
     * 停止动画
     */
    fun stop() {
    //    mProgressDrawable!!.stop()
    }

    fun setProgressRotation(rotation: Float) {
     //   mProgressDrawable!!.setProgressRotation(rotation)
    }

    companion object {
        private const val KEY_SHADOW_COLOR = 0x1E000000
        private const val FILL_SHADOW_COLOR = 0x3D000000

        // PX
        private const val X_OFFSET = 0f
        private const val Y_OFFSET = 1.75f
        private const val SHADOW_RADIUS = 3.5f
        private const val SHADOW_ELEVATION = 4
        const val DEFAULT_CIRCLE_BG_LIGHT = -0x50506
        const val DEFAULT_CIRCLE_COLOR = -0x100000
        private const val DEFAULT_CIRCLE_DIAMETER = 40
        private const val STROKE_WIDTH_LARGE = 3
        const val DEFAULT_TEXT_SIZE = 9
    }
}