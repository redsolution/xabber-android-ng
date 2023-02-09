package com.xabber.presentation.custom

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.view.NestedScrollingParent
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.xabber.R
import com.xabber.utils.dp
import kotlin.math.abs

class PullRefreshLayout : FrameLayout, NestedScrollingParent {
    private var parentHelper: NestedScrollingParentHelper? = null
    private var onRefreshListener: OnRefreshListener? = null

    interface OnRefreshListener {
        fun onRefresh()
        fun onRefreshPulStateChange(percent: Float, state: Int)
    }

    internal open class WXRefreshAnimatorListener : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    }

    private val headerView: HeaderView = HeaderView(context)
    private var mTargetView: View? = null

    // Enable PullRefresh
    var isRefreshEnable = true

    // Is Refreshing
    @Volatile
    var isRefreshing = false
        private set
    private var guidanceViewHeight = 0f
    private var guidanceViewFlowHeight = 0f
    private var mCurrentAction = -1
    private var isConfirm = false

    constructor(context: Context) : super(context) {
        initAttrs(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttrs(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initAttrs(attrs)
    }

    fun setOnRefreshListener(onRefreshListener: OnRefreshListener?) {
        this.onRefreshListener = onRefreshListener
    }


    fun finishRefresh() {
        if (mCurrentAction == ACTION_PULL_REFRESH)
            resetHeaderView(headerView.measuredHeight)
    }

    fun setRefreshViewText(@StringRes textRes: Int) {
        headerView.setText(textRes)
    }

    fun setHeaderBackground(color: Int) {
        headerView.setBackgroundResource(color)
    }

    fun setElementsColors(color: Int, colorLight: Int, isUp: Boolean) {
        headerView.setColor(color, colorLight, isUp)
    }

    @SuppressLint("Recycle", "CustomViewStyleable")
    private fun initAttrs(attrs: AttributeSet?) {
        if (childCount > 1) {
            throw RuntimeException("WXSwipeLayout should not have more than one child")
        }
        parentHelper = NestedScrollingParentHelper(this)
        guidanceViewHeight = GUIDANCE_VIEW_HEIGHT.dp.toFloat()
        guidanceViewFlowHeight = guidanceViewHeight * 1.5.toFloat()
        if (isInEditMode && attrs == null) {
            return
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mTargetView = getChildAt(0)
        setGuidanceView()
    }

    private fun setGuidanceView() {
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, 0)
        headerView.setTextColor(R.color.red_500)
        headerView.setBackgroundResource(R.color.grey_300)
        addView(headerView, lp)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return if (!isRefreshEnable) {
            false
        } else super.onInterceptTouchEvent(ev)
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return true
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        parentHelper?.onNestedScrollAccepted(child, target, axes)
    }

    override fun onStopNestedScroll(child: View) {
        parentHelper?.onStopNestedScroll(child)
        handlerAction()
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        if (!isRefreshEnable) {
            return
        }
        if (abs(dy) > 200) {
            return
        }
        if (!isConfirm) {
            if (dy < 0 && !canChildScrollUp()) {
                mCurrentAction = ACTION_PULL_REFRESH
                isConfirm = true
            } else if (dy > 0 && !canChildScrollDown()) {
                mCurrentAction = ACTION_LOAD_MORE
                isConfirm = true
            }
        }
        if (moveGuidanceView(-dy.toFloat())) {
            consumed[1] += dy
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
    }

    override fun getNestedScrollAxes(): Int = parentHelper?.nestedScrollAxes ?: 0

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        return false
    }

    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ) = false

    private fun moveGuidanceView(distanceY: Float): Boolean {
        if (isRefreshing) {
            return false
        }
        if (!canChildScrollUp() && isRefreshEnable && mCurrentAction == ACTION_PULL_REFRESH) {
            val lp = headerView.layoutParams as LayoutParams
            val sign = if (distanceY < 0) 0.5 else {
                if (lp.height >= 260) 0.1 else if (lp.height in 259 downTo 230) 0.2 else if (lp.height in 229 downTo 190) 0.3 else 0.5
            }
            val translationYDelta = (distanceY * sign).toInt()
            lp.height += translationYDelta
            if (lp.height > 100) headerView.setProgressRotation(true) else headerView.setProgressRotation(
                false
            )
            if (lp.height < 0) {
                lp.height = 0
            }
            if (lp.height > guidanceViewFlowHeight) {
                lp.height = guidanceViewFlowHeight.toInt()
            }
            if (onRefreshListener != null) {
                if (lp.height >= guidanceViewHeight) {
                    onRefreshListener?.onRefreshPulStateChange(
                        lp.height / guidanceViewHeight,
                        OVER_TRIGGER_POINT
                    )
                } else {
                    onRefreshListener?.onRefreshPulStateChange(
                        lp.height / guidanceViewHeight,
                        NOT_OVER_TRIGGER_POINT
                    )
                }
            }
            if (lp.height == 0) {
                isConfirm = false
                mCurrentAction = -1
            }
            headerView.layoutParams = lp
            moveTargetView(lp.height.toFloat())
            return true
        }
        return false
    }

    private fun moveTargetView(h: Float) {
        mTargetView?.translationY = h
    }

    private fun handlerAction() {
        if (isRefreshing) {
            return
        }
        isConfirm = false
        val lp: LayoutParams
        if (isRefreshEnable && mCurrentAction == ACTION_PULL_REFRESH) {
            lp = headerView.layoutParams as LayoutParams
            if (lp.height >= guidanceViewHeight) {
                startRefresh(lp.height)
                if (onRefreshListener != null) onRefreshListener?.onRefreshPulStateChange(
                    1f,
                    START
                )
            } else if (lp.height > 0) {
                resetHeaderView(lp.height)
            } else {
                resetRefreshState()
            }
        }
    }

    private fun startRefresh(headerViewHeight: Int) {
        isRefreshing = true
        val animator = ValueAnimator.ofFloat(headerViewHeight.toFloat(), guidanceViewHeight)
        animator.addUpdateListener { animation ->
            val lp = headerView.layoutParams as LayoutParams
            lp.height = (animation.animatedValue as Float).toFloat().toInt()
            headerView.layoutParams = lp
            moveTargetView(lp.height.toFloat())
        }
        animator.addListener(object : WXRefreshAnimatorListener() {
            override fun onAnimationEnd(animation: Animator, isReverse: Boolean) {
                onRefreshListener?.onRefresh()
            }
        })
        animator.duration = 300
        animator.start()
    }

    private fun resetHeaderView(headerViewHeight: Int) {
        val animator = ValueAnimator.ofFloat(headerViewHeight.toFloat(), 0f)
        animator.addUpdateListener { animation ->
            val lp = headerView.layoutParams as LayoutParams
            lp.height = (animation.animatedValue as Float).toFloat().toInt()
            headerView.layoutParams = lp
            moveTargetView(lp.height.toFloat())
        }
        animator.addListener(object : WXRefreshAnimatorListener() {
            override fun onAnimationEnd(animation: Animator, isReverse: Boolean) {
                resetRefreshState()
            }
        })
        animator.duration = 300
        animator.start()
    }

    private fun resetRefreshState() {
        isRefreshing = false
        isConfirm = false
        mCurrentAction = -1
    }

    private fun canChildScrollUp(): Boolean {
        if (mTargetView == null) {
            return false
        }
        return ViewCompat.canScrollVertically(mTargetView, -1)
    }

    private fun canChildScrollDown(): Boolean {
        if (mTargetView == null) {
            return false
        }
        return ViewCompat.canScrollVertically(mTargetView, 1)
    }

    companion object {
        const val NOT_OVER_TRIGGER_POINT = 1
        const val OVER_TRIGGER_POINT = 2
        const val START = 3
        private const val GUIDANCE_VIEW_HEIGHT = 90
        private const val ACTION_PULL_REFRESH = 0
        private const val ACTION_LOAD_MORE = 1
    }

}
