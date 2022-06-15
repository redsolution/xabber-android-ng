package com.xabber.presentation.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import android.view.MotionEvent
import android.view.View
import com.xabber.R
import java.util.ArrayList
import kotlin.math.ceil

@SuppressLint("AppCompatCustomView")
class PlayerVisualizerView : View {
    /**
     * bytes array converted from file.
     */
    private var bytes: ByteArray? = null
    private var wave: ArrayList<Int>? = ArrayList()
    private var amplitude = 0

    /**
     * Percentage of audio sample scale
     * Should updated dynamically while audioPlayer is played
     */
    private var denseness = 0f
    private val playedStatePainting = Paint()
    private val notPlayedStatePainting = Paint()
   private var myWidth = 0
    private var myHeight = 0
    private var manualInputMode = false

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        bytes = null
        notPlayedStatePainting.strokeWidth = 1f
        notPlayedStatePainting.isAntiAlias = true
        notPlayedStatePainting.color = ContextCompat.getColor(context, R.color.grey_500)
        notPlayedStatePainting.alpha = 127
        playedStatePainting.strokeWidth = 1f
        playedStatePainting.isAntiAlias = true
        playedStatePainting.color = ContextCompat.getColor(context, R.color.grey_800)
        playedStatePainting.alpha = 127
    }

    fun setPlayedColor(color: Int) {
        playedStatePainting.reset()
        playedStatePainting.strokeWidth = 1f
        playedStatePainting.isAntiAlias = true
        playedStatePainting.color = color
    }

    fun setPlayedColorAlpha(alpha: Int) {
        playedStatePainting.alpha = alpha
    }

    fun setNotPlayedColorAlpha(alpha: Int) {
        notPlayedStatePainting.alpha = alpha
    }

    fun setNotPlayedColor(color: Int) {
        notPlayedStatePainting.reset()
        notPlayedStatePainting.strokeWidth = 1f
        notPlayedStatePainting.isAntiAlias = true
        notPlayedStatePainting.color = color
    }

    fun setNotPlayedColorRes(id: Int) {
        notPlayedStatePainting.reset()
        notPlayedStatePainting.strokeWidth = 1f
        notPlayedStatePainting.isAntiAlias = true
        notPlayedStatePainting.color = ContextCompat.getColor(context, id)
        notPlayedStatePainting.alpha = 127
    }

    /**
     * update and redraw Visualizer view
     */
    /*public void updateVisualizer(byte[] bytes) {
        this.bytes = bytes;
        //decodeOpus(bytes);
        invalidate();
    }*/
    fun updateVisualizer(wave: ArrayList<Int>?) {
        this.wave = wave
        calculateAmplitude()
        invalidate()
    }

    fun refreshVisualizer() {
        val empty = ArrayList<Int>()
        empty.add(0)
        wave = empty
        invalidate()
    }

    fun setAmplitude(amp: Int) {
        amplitude = amp
    }

    /**
     * Update player percent. 0 - file not played, 1 - full played
     *
     * @param percent
     */
    fun updatePlayerPercent(percent: Float, manual: Boolean) {
        if (!manualInputMode || manual) {
            denseness = ceil((myWidth * percent).toDouble()).toFloat()
            if (denseness < 0) {
                denseness = 0f
            } else if (denseness > width) {
                denseness = width.toFloat()
            }
            invalidate()
        }
    }

    fun setManualInputMode(manual: Boolean) {
        manualInputMode = manual
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        myWidth = width
        myHeight = height
    }

    private val fakeAmplitudeList: ArrayList<Int>?
        get() {
            val waveform = ArrayList<Int>()
            val totalBarsCount = (myWidth / dp(3f)).toFloat()
            if (totalBarsCount <= 0.1f) {
                return null
            }
            var preemptiveSave = false
            val samplesCount = bytes!!.size
            val samplesPerBar = samplesCount / totalBarsCount.toInt()
            var currentAmplitude = 0
            for (a in 0 until samplesCount) {
                currentAmplitude += bytes!![a]
                if (a % samplesPerBar == 0) {
                    waveform.add(currentAmplitude)
                    if (currentAmplitude > amplitude) amplitude = currentAmplitude
                    currentAmplitude = 0
                    if (a + samplesPerBar > samplesCount) preemptiveSave = true
                }
            }
            if (preemptiveSave) {
                waveform.add(currentAmplitude)
                if (currentAmplitude > amplitude) {
                    amplitude = currentAmplitude
                }
            }
            return waveform
        }

    private fun calculateAmplitude() {
        if (wave == null || wave!!.isEmpty()) return
        var amplitude = 0
        for (i in wave!!.indices) {
            if (wave!![i] > amplitude) amplitude = wave!![i]
        }
        this.amplitude = amplitude
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (myWidth == 0) {
            return
        }
        val totalBarsCount = myWidth / dp(3f)
        if (totalBarsCount == 0) {
            return
        }
        if (wave != null && wave!!.isNotEmpty()) {
            for (a in 0 until totalBarsCount) {
                var barHeight: Int
                val sampleIndex: Int = a * wave!!.size / totalBarsCount
                val sampleSize: Int = wave!![sampleIndex]
                barHeight = if (amplitude != 0 && sampleSize / amplitude <= 1) {
                    myHeight * sampleSize / amplitude
                } else 0
                if (barHeight < dp(1f)) barHeight = dp(1f)
                val x = a * dp(3f)
                val left = x.toFloat()
                val right = (x + dp(2f)).toFloat()
                val top = (myHeight - barHeight).toFloat()
                val bottom = myHeight.toFloat()
                if (x < denseness) {
                    canvas.drawRect(left, top, right, bottom, playedStatePainting)
                } else {
                    canvas.drawRect(left, top, right, bottom, notPlayedStatePainting)
                }
            }
        } else {
            for (a in 0 until totalBarsCount) {
                val barHeight = dp(1f)
                val left = (a * dp(3f)).toFloat()
                val right = left + dp(2f)
                val top = (height - barHeight).toFloat()
                val bottom = height.toFloat()
                if (left < denseness) {
                    canvas.drawRect(left, top, right, bottom, playedStatePainting)
                } else {
                    canvas.drawRect(left, top, right, bottom, notPlayedStatePainting)
                }
            }
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    class OnProgressTouch : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            val touchPoint: Float
            val width: Int
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchPoint = motionEvent.x
                    width = view.width
                    (view as PlayerVisualizerView).setManualInputMode(true)
                    view.updatePlayerPercent(touchPoint / width, true)
                    view.getParent().requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    touchPoint = motionEvent.x
                    width = view.width
                    (view as PlayerVisualizerView).updatePlayerPercent(touchPoint / width, true)
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    (view as PlayerVisualizerView).setManualInputMode(false)
                    view.getParent().requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
    }

    fun dp(value: Float): Int {
        return if (value == 0f) {
            0
        } else ceil(
            (context.resources.displayMetrics.density * value).toDouble()
        ).toInt()
    }
}