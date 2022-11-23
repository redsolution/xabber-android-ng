package com.xabber.presentation.application.fragments.chatlist

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.xabber.utils.dp
import com.xabber.utils.px

//Величина расстояния перевода при прокрутке списка
private const val OVERSCROLL_TRANSLATION_MAGNITUDE = 0.3f

/** Величина расстояния перевода, когда список достигает края при бросании. */
private const val FLING_TRANSLATION_MAGNITUDE = 0.2f

/**
 * Заменить краевой эффект отскоком
 */
class BounceEdgeEffectFactory(private val view: View) : RecyclerView.EdgeEffectFactory() {
    private var isVibrate = false

    override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {

        return object : EdgeEffect(recyclerView.context) {

            // Ссылка на [SpringAnimation] для этого RecyclerView, используемая для возврата элемента после эффекта чрезмерной прокрутки.
            var translationAnim: SpringAnimation? = null

            override fun onPull(deltaDistance: Float) {
                super.onPull(deltaDistance)
                handlePull(deltaDistance)
            }

            override fun onPull(deltaDistance: Float, displacement: Float) {
                super.onPull(deltaDistance, displacement)
                handlePull(deltaDistance)
            }

            private fun handlePull(deltaDistance: Float) {
                // Это вызывается при каждом событии касания, когда список прокручивается пальцем.
Log.d("bounds", "handlePull")
                // Переведите recyclerView с расстоянием
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                val translationYDelta = sign * recyclerView.width * deltaDistance * OVERSCROLL_TRANSLATION_MAGNITUDE
                recyclerView.translationY += translationYDelta

                translationAnim?.cancel()
            }

            override fun onRelease() {

                super.onRelease()
                Log.d("bounds", "${recyclerView.translationY}")

                // Палец поднят. Запустите анимацию, чтобы вернуть перевод в состояние покоя
                if (recyclerView.translationY > 80f) {
                    if (!isVibrate) shortVibrate()
                    isVibrate = true
                    translationAnim = createAnim()?.also {
                      it.start()
                    //    shortVibrate()
                    }
                } else {   translationAnim = createAnimClose()?.also {
                      it.start()
                    isVibrate = false
                    //    shortVibrate()
                    }}
            }

            private fun shortVibrate() {
              view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }

            override fun onAbsorb(velocity: Int) {
                super.onAbsorb(velocity)
Log.d("bounds", "край")
                // Список достиг края на броске
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                val translationVelocity = sign * velocity * FLING_TRANSLATION_MAGNITUDE
                translationAnim?.cancel()
                translationAnim = createAnim().setStartVelocity(translationVelocity)?.also { it.start() }
            }

            override fun draw(canvas: Canvas?): Boolean {
             //   не рисуйте обычный краевой эффект
                return false
            }

            override fun isFinished(): Boolean {
              //  Без этого будут пропущены будущие вызовы onAbsorb()
                return translationAnim?.isRunning?.not() ?: true
            }


            private fun createAnim() = SpringAnimation(recyclerView, SpringAnimation.TRANSLATION_Y)
                .setSpring(
                    SpringForce()
                    .setFinalPosition(180f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_LOW)
                )

            private fun createAnimClose() = SpringAnimation(recyclerView, SpringAnimation.TRANSLATION_Y)
                .setSpring(
                    SpringForce()
                    .setFinalPosition(0f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_LOW)
                )

        }

    }
}