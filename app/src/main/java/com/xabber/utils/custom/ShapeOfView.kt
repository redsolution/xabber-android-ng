package com.xabber.utils.custom

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import com.xabber.R

class ShapeOfView : FrameLayout {
    private val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private var pdMode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private var drawable: Drawable? = null
    private val clipManager: ClipManager = ClipPathManager()
    private var isNeedShapeUpdate = true
    private var clipBitmap: Bitmap? = null
    private val rectView = Path()

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        clipPaint.isAntiAlias = true          // настройка параметров рисовния
        isDrawingCacheEnabled = true
        setWillNotDraw(false)
        clipPaint.style = Paint.Style.FILL
        clipPaint.strokeWidth = 1f
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {      // настройка режима смешивания (xfermode)
            clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            setLayerType(LAYER_TYPE_SOFTWARE, clipPaint)
        } else {
            clipPaint.xfermode = pdMode
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        if (attrs != null) {                   // Получение пользовательских атрибутов
            val attributes = context.obtainStyledAttributes(attrs, R.styleable.ShapeOfView)
            if (attributes.hasValue(R.styleable.ShapeOfView_shape_clip_drawable)) {
                val resourceId =
                    attributes.getResourceId(R.styleable.ShapeOfView_shape_clip_drawable, -1)
                if (-1 != resourceId) {
                    setDrawable(resourceId)
                }
            }
            attributes.recycle()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            requiresShapeUpdate()
        }
    }

    private fun requiresBitmap(): Boolean {
        return isInEditMode || clipManager.requiresBitmap() || drawable != null
    }

    fun setDrawable(drawable: Drawable?) {
        this.drawable = drawable
        requiresShapeUpdate()
    }

    fun setDrawable(redId: Int) {
        setDrawable(AppCompatResources.getDrawable(context, redId))
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isNeedShapeUpdate) {       // Вычисление формы и ее отрисовка
            calculateLayout(canvas.width, canvas.height)
            isNeedShapeUpdate = false
        }
        if (requiresBitmap()) {
            clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(clipBitmap!!, 0f, 0f, clipPaint)
        } else {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                canvas.drawPath(clipPath, clipPaint)
            } else {
                canvas.drawPath(rectView, clipPaint)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    private fun calculateLayout(width: Int, height: Int) {
        rectView.reset()
        rectView.addRect(0f, 0f, 1f * getWidth(), 1f * getHeight(), Path.Direction.CW)
        if (width > 0 && height > 0) {
            clipManager.setupClipLayout(width, height)
            clipPath.reset()
            clipPath.set(clipManager.createMask(width, height))
            if (requiresBitmap()) {
                if (clipBitmap != null) {
                    clipBitmap!!.recycle()
                }
                clipBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(clipBitmap!!)
                if (drawable != null) {
                    drawable!!.setBounds(0, 0, width, height)
                    drawable!!.draw(canvas)
                } else {
                    canvas.drawPath(clipPath, clipManager.paint!!)
                }
            }
            if (ViewCompat.getElevation(this) > 0f) {
                try {
                    outlineProvider = outlineProvider
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        postInvalidate()
    }

    override fun getOutlineProvider(): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (!isInEditMode) {
                    val shadowConvexPath = clipManager.shadowConvexPath
                    if (shadowConvexPath != null) {
                        try {
                            outline.setConvexPath(shadowConvexPath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun requiresShapeUpdate() {
        isNeedShapeUpdate = true
        postInvalidate()
    }
}