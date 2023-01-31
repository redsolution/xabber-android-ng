package com.xabber.presentation.application.fragments.chatlist

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.EdgeEffect
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.presentation.custom.HeaderView


class PullRecyclerViewEffectFactory(private val view: ViewGroup) : RecyclerView.EdgeEffectFactory() {
    private var isVibrate = false
    private var archiveShowed = false
    private var isStop = false
    private val headerView: HeaderView = HeaderView(view.context)
    var isC = false


    init {
        var lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0)
        headerView!!.setStartEndTrim(0f, 0.75f)
        headerView.setText("mRefreshDefaulText")

        headerView.setBackgroundColor(Color.RED)
        view.addView(headerView)
    }

    override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {



        return object : EdgeEffect(recyclerView.context) {

            var lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0)

            var translationAnim: SpringAnimation? = null

            override fun onPull(deltaDistance: Float) {
                draw(Canvas())
                Log.d("ppp", "direction = $direction, recycler y = ${recyclerView.translationY}, deltaDistance = $deltaDistance, width = ${recyclerView.width}")
                super.onPull(deltaDistance)
           handlePull(deltaDistance)
            }



            override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
                return super.onPullDistance(deltaDistance, displacement)
            }

            override fun onPull(deltaDistance: Float, displacement: Float) {

                super.onPull(deltaDistance, displacement)
             handlePull(deltaDistance)
            }

            private fun handlePull(deltaDistance: Float) {
                Log.d("ppp", "22222 direction = $direction, recycler y = ${recyclerView.y}, deltaDistance = $deltaDistance, width = ${recyclerView.width}")
if (!isC) {
    draw(Canvas())
    isC = true
}
                val sign = if (direction == DIRECTION_BOTTOM) -1 else if (direction == DIRECTION_TOP) 1 else 0
                if (direction == 1) {
                    if (!isStop) {

                        if (recyclerView.translationY < 500 && recyclerView.translationY >=0) {
                            if (recyclerView.translationY >= 300) {
                                if (!archiveShowed) shortVibrate()
                                archiveShowed = true
                                isVibrate = true
                            }
                            val translationYDelta =
                                sign * recyclerView.width * deltaDistance * if (recyclerView.translationY < 150) 0.5f else if (recyclerView.translationY >= 150 && recyclerView.translationY < 260) 0.1f else 0.05f
                            recyclerView.translationY += translationYDelta
                        }
                    }
                } else {

                }
                translationAnim?.cancel()

            }



            override fun onRelease() {
                super.onRelease()
                isVibrate = false
                if (archiveShowed) isStop = true
                if (archiveShowed) recyclerView.translationY = 200f else {
                    createAnimClose().start()
                    isStop = false
                }
            }

            private fun shortVibrate() {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }

            override fun onAbsorb(velocity: Int) {
                super.onAbsorb(velocity)

                Log.d("iii", "onAbsorb")
            }


            override fun draw(canvas: Canvas): Boolean {
//                headerView.setStartEndTrim(0f, 0.75f)
//                headerView.setText("mRefreshDefaulText")
//
//                recyclerView.addView(headerView, lp)
                val x = 200
                val y = 200
                val radius: Int
                radius = 100
                val paint = Paint()
                paint.setStyle(Paint.Style.FILL)
                paint.setColor(Color.BLACK)
                canvas.drawPaint(paint)
                // Use Color.parseColor to define HTML colors
                // Use Color.parseColor to define HTML colors
                paint.setColor(Color.parseColor("#CD5C5C"))
                canvas.drawCircle((x / 2).toFloat(), (y / 2).toFloat(), radius.toFloat(), paint)
                Log.d("canvas", "$canvas")
val background = ContextCompat.getDrawable(recyclerView.context, R.color.green_500)

                background?.draw(canvas)
                return false
            }

            override fun isFinished(): Boolean {
                return true
            }

            private fun createAnim() = SpringAnimation(recyclerView, SpringAnimation.TRANSLATION_Y)
                .setSpring(
                    SpringForce()
                        .setFinalPosition(200f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_LOW)
                )

            private fun createAnimClose() =
                SpringAnimation(recyclerView, SpringAnimation.TRANSLATION_Y)
                    .setSpring(
                        SpringForce()
                            .setFinalPosition(0f)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                            .setStiffness(SpringForce.STIFFNESS_LOW)
                    )
        }

    }
}