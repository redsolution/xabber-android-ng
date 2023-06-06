package com.xabber.presentation.application.fragments.test

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.application.fragments.chat.message.ImageGrid
import com.xabber.presentation.application.fragments.chat.message.RoundedBorders
import com.xabber.utils.StringUtils
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.dp
import java.util.*
import kotlin.collections.ArrayList

class ImageGridBuilder {

    private val centerCropTransformation: MultiTransformation<Bitmap> by lazy {
        val radius =
            if (MessageChanger.cornerValue > 4) (MessageChanger.cornerValue - 4).dp else 1.dp
        createStandardTransformation(radius, CenterCrop())
    }

    private fun createStandardTransformation(
        radius: Int,
        vararg extraTransformation: BitmapTransformation
    ) =
        MultiTransformation(
            listOf(
                RoundedCorners(radius),
                RoundedBorders(radius, ImageGrid.IMAGE_ROUNDED_BORDER_WIDTH),
                *extraTransformation
            )
        )

    fun inflateView(parent: ViewGroup, imageCount: Int): View {
        return LayoutInflater.from(parent.context)
            .inflate(getLayoutResource(imageCount), parent, false)
    }

    fun bindView(
        view: View, message: MessageDto,
        attachments: ArrayList<MessageReferenceDto>,
        clickListener: View.OnClickListener?
    ) {
        val tvTime = view.findViewById<TextView>(R.id.tv_image_sending_time)
        val dates = Date(message.sentTimestamp)
        val time = StringUtils.getTimeText(view.context, dates)
        tvTime.text = time
        if (attachments.size == 1) {
            val imageView = getImageView(view, 0)

            bindOneImage(message, attachments[0], view, imageView, view)
            imageView.setOnClickListener(clickListener)
        } else {
            val tvCounter = view.findViewById<TextView>(R.id.tvCounter)
            var index = 0
            loop@ for (attachment in attachments) {
                if (index > 5) break@loop
                val imageView = getImageView(view, index)
                bindImage(message, attachment, view, imageView, view)
                imageView.setOnClickListener(clickListener)
                index++
            }
            if (tvCounter != null) {
                if (attachments.size > 6) {
                    tvCounter.text = StringBuilder("+").append(attachments.size - MAX_IMAGE_IN_GRID)
                    tvCounter.visibility = View.VISIBLE
                } else tvCounter.visibility = View.GONE
            }
        }
    }


    fun bindView(
        view: View,
        messageRealmObject: MessageDto,
        clickListener: View.OnClickListener?,
        wholeGridLongTapListener: View.OnLongClickListener? = null,
    ) {
        val attachmentRealmObjects = messageRealmObject.references

        if (attachmentRealmObjects.size == 1) {
            getImageView(view, 0)
                .apply {
                    setOnLongClickListener(wholeGridLongTapListener)
//                    if (attachmentRealmObjects[0].isGeo == true) {
//                        val ref = attachmentRealmObjects[0]
//                        val lon =  ref.longitude
//                        val lat = ref.latitude
//                        setOnClickListener {
//                            context.startActivity(
//                                Intent().apply {
//                                    action = Intent.ACTION_VIEW
//                                    data = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
//                                }
//                            )
//                        }
//                    } else {
                    setOnClickListener(clickListener)
                    //     }
                }
                .also { setupImageViewIntoFlexibleSingleImageCell(attachmentRealmObjects[0], it) }
        } else {
            attachmentRealmObjects.take(5).forEachIndexed { index, attachmentRealmObject ->
                getImageView(view, index)
                    .apply {
                        setOnLongClickListener(wholeGridLongTapListener)
                        setOnClickListener(clickListener)
                    }
                    .also {
                        //setupImageViewIntoRigidGridCell(attachmentRealmObject, it) }
                    }
            }

            view.findViewById<TextView>(R.id.tvCounter)?.apply {
                if (attachmentRealmObjects.size > MAX_IMAGE_IN_GRID) {
                    text =
                        StringBuilder("+").append(attachmentRealmObjects.size - MAX_IMAGE_IN_GRID)
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
        }
    }

    private fun bindImage(message: MessageDto,
        attachment: MessageReferenceDto,
        parent: View,
        imageView: ImageView,
        view: View
    ) {
        val hasText = message.messageBody.isNotEmpty()
        val radius =
            if (MessageChanger.cornerValue > 4) (MessageChanger.cornerValue - 4) else 1
        val innerRadius = if (radius <= 2) radius else 2
        Log.d("yyy", "hasText = $hasText")
        val sh = view.findViewById<ShapeOfView>(R.id.cardview_1)
       val card2 = view.findViewById<ShapeOfView>(R.id.cardview_2)
       val card3 = view.findViewById<ShapeOfView>(R.id.cardview_3)
        val card4 = view.findViewById<ShapeOfView>(R.id.cardview_4)
       val card5 = view.findViewById<ShapeOfView>(R.id.cardview_5)
       val card6 = view.findViewById<ShapeOfView>(R.id.cardview_6)

        if (sh != null) {
            val radii = if (message.references.size < 5) floatArrayOf(radius.dp.toFloat(), radius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat()) else
                floatArrayOf(radius.dp.toFloat(), radius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat())    // массив радиусов углов в dp, по часовой стрелке
            val shape = ShapeDrawable(RoundRectShape(radii, null, null)) // создаем объект ShapeDrawable с заданными радиусами

            sh.setDrawable(shape)// устанавливаем объект ShapeDrawable в качестве фона ImageView
        }
        if (card2 != null) {
            val radii = if (message.references.size < 3) floatArrayOf(innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat()) else
                floatArrayOf(innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat())    // массив радиусов углов в dp, по часовой стрелке
            val shape = ShapeDrawable(RoundRectShape(radii, null, null)) // создаем объект ShapeDrawable с заданными радиусами
            card2.setDrawable(shape)
        }
        if (card3 != null) {
            val radii = floatArrayOf(innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat())
            val shape = ShapeDrawable(RoundRectShape(radii, null, null)) // создаем объект ShapeDrawable с заданными радиусами
            card3.setDrawable(shape)
        }
        if (card4 != null) {
            val radii =  floatArrayOf(innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat())
            val shape = ShapeDrawable(RoundRectShape(radii, null, null)) // создаем объект ShapeDrawable с заданными радиусами
            card4.setDrawable(shape)
        }
        if (card5 != null) {
            val radii =  floatArrayOf(innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat())
            val shape = ShapeDrawable(RoundRectShape(radii, null, null)) // создаем объект ShapeDrawable с заданными радиусами
            card5.setDrawable(shape)
        }
        if (card6 != null) {
            val radii =  floatArrayOf(innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat(), innerRadius.dp.toFloat())
            val shape = ShapeDrawable(RoundRectShape(radii, null, null)) // создаем объект ShapeDrawable с заданными радиусами
            card6.setDrawable(shape)
        }


        val timeStampRadius = if (radius > 3) radius - 3 else 1

        val lin = view.findViewById<LinearLayout>(R.id.message_info)

        val params = lin.layoutParams as FrameLayout.LayoutParams
        val margin = MessageChanger.timeStampMargin.dp
        params.setMargins(margin, margin, margin, margin)
        lin.layoutParams = params
        val timeStampBackground = getTimeStampBackground(timeStampRadius)
        lin.setBackgroundResource(timeStampBackground)

        Glide.with(imageView.context)
            .load(attachment.uri)
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)
    }

    private fun bindOneImage(message: MessageDto,
        attachment: MessageReferenceDto,
        parent: View,
        imageView: ImageView,
        view: View
    ) {
        val radius =
            if (MessageChanger.cornerValue > 4) (MessageChanger.cornerValue - 4) else 1
        val card = view.findViewById<ShapeOfView>(R.id.card)
        val innerRadius = if (radius <= 2) radius else 2
        val hasText = message.messageBody.isNotEmpty()
        if (card != null) {
            val radii = floatArrayOf(radius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat(), if (hasText) innerRadius.dp.toFloat() else radius.dp.toFloat())
            val shape = ShapeDrawable(RoundRectShape(radii, null, null)) // создаем объект ShapeDrawable с заданными радиусами

            card.setDrawable(shape)// устанавливаем объект ShapeDrawable в качестве фона ImageView
        }
        val timeStampRadius = if (radius > 3) radius - 3 else 1

        val lin = view.findViewById<LinearLayout>(R.id.message_info)

       val params = lin.layoutParams as FrameLayout.LayoutParams
        val margin = MessageChanger.timeStampMargin.dp
        params.setMargins(margin, margin, margin, margin)
        lin.layoutParams = params
        val timeStampBackground = getTimeStampBackground(timeStampRadius)
        lin.setBackgroundResource(timeStampBackground)



        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

// Вычислим максимально допустимые значения ширины и высоты
        val maxWidth = (screenWidth * 0.8).toInt()
        val maxHeight = (screenHeight * 0.5).toInt()
        Glide.with(imageView.context)
            .asBitmap()
            .load(attachment.uri)
            .override(maxWidth, maxHeight)
            .fitCenter()
          //  .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)

    }

    private fun getDrawable1(radius: Int): Int = 1
//        when (radius) {
//            1 -> R.drawable.bubble_1px
//            2 -> R.drawable.bubble_2px
//            3 -> R.drawable.bubble_3px
//            4 -> R.drawable.bubble_4px
//            5 ->
//                6->
//            7->
//            8->
//            9->
//            10->
//            11->
//            12->
//
//        }
   // }

    private fun getTimeStampBackground(timeStampRadius: Int): Int {
       return when (timeStampRadius) {
            1 -> R.drawable.time_stamp_1px
            2 -> R.drawable.time_stamp_2px
            3 -> R.drawable.time_stamp_3px
            4 -> R.drawable.time_stamp_4px
            5 -> R.drawable.time_stamp_5px
            6 -> R.drawable.time_stamp_6px
            7 -> R.drawable.time_stamp_7px
            8 -> R.drawable.time_stamp_8px
            9 -> R.drawable.time_stamp_9px
            10 -> R.drawable.time_stamp_10px
            11 -> R.drawable.time_stamp_11px
            12 -> R.drawable.time_stamp_12px
            13 -> R.drawable.time_stamp_13px

            else -> R.drawable.time_stamp_1px
        }
    }

    private fun setupImageViewIntoFlexibleSingleImageCell(
        referenceRealmObject: MessageReferenceDto, imageView: ImageView
    ) {
        val imageWidth = referenceRealmObject.width
        val imageHeight = referenceRealmObject.height

        if (imageWidth != null && imageHeight != null) {
            setupImageViewWithDimensions(
                imageView, referenceRealmObject, imageWidth, imageHeight
            )
        } else {
            setupImageViewWithoutDimensions(
                imageView, referenceRealmObject.uri!!, referenceRealmObject.id
            )
        }
    }

    private fun setupImageViewWithoutDimensions(
        imageView: ImageView, url: String, attachmentId: String
    ) {
        Glide.with(imageView.context)
            .asBitmap()
            .load(url)
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(object : CustomTarget<Bitmap?>() {
                override fun onLoadStarted(placeholder: Drawable?) {
                    super.onLoadStarted(placeholder)
                    imageView.setImageDrawable(placeholder)
                    imageView.visibility = View.VISIBLE
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    imageView.setImageDrawable(errorDrawable)
                    imageView.visibility = View.VISIBLE
                }

                override fun onResourceReady(
                    resource: Bitmap, transition: Transition<in Bitmap?>?
                ) {
                    val width = resource.width
                    val height = resource.height
                    if (width <= 0 || height <= 0) {
                        return
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun setupImageViewWithDimensions(
        imageView: ImageView, referenceRealmObject: MessageReferenceDto, width: Int, height: Int
    ) {
        val uri = referenceRealmObject.uri?.takeIf { it.isNotEmpty() }
            ?: referenceRealmObject.uri

        Glide.with(imageView.context)
            .load(uri)
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)

        scaleImage(imageView.layoutParams, height, width)
    }

    private fun scaleImage(layoutParams: ViewGroup.LayoutParams, height: Int, width: Int) {
        val scaledWidth: Int
        val scaledHeight: Int
        if (width <= height) {
            when {
                height > MAX_IMAGE_HEIGHT_SIZE -> {
                    scaledWidth = (width / (height.toDouble() / MAX_IMAGE_HEIGHT_SIZE)).toInt()
                    scaledHeight = MAX_IMAGE_HEIGHT_SIZE
                }
                width < MIN_IMAGE_SIZE -> {
                    scaledWidth = MIN_IMAGE_SIZE
                    scaledHeight =
                        (height / (width.toDouble() / MIN_IMAGE_SIZE)).toInt().coerceAtMost(
                            MAX_IMAGE_HEIGHT_SIZE
                        )
                }
                else -> {
                    scaledWidth = width
                    scaledHeight = height
                }
            }
        } else {
            when {
                width > MAX_IMAGE_SIZE -> {
                    scaledWidth = MAX_IMAGE_SIZE
                    scaledHeight = (height / (width.toDouble() / MAX_IMAGE_SIZE)).toInt()
                }
                height < MIN_IMAGE_SIZE -> {
                    scaledWidth =
                        (width / (height.toDouble() / MIN_IMAGE_SIZE)).toInt().coerceAtMost(
                            MAX_IMAGE_SIZE
                        )
                    scaledHeight = MIN_IMAGE_SIZE
                }
                else -> {
                    scaledWidth = width
                    scaledHeight = height
                }
            }
        }

        layoutParams.width = scaledWidth
        layoutParams.height = scaledHeight
    }

    private fun getLayoutResource(imageCount: Int): Int {
        return when (imageCount) {
            1 -> R.layout.image_grid_1
            2 -> R.layout.image_grid_2
            3 -> R.layout.image_grid_3
            4 -> R.layout.image_grid_4
            5 -> R.layout.image_grid_5
            else -> R.layout.image_grid_6
        }
    }

    private fun getImageView(view: View, index: Int): ImageView {
        return when (index) {
            1 -> view.findViewById(R.id.ivImage1)
            2 -> view.findViewById(R.id.ivImage2)
            3 -> view.findViewById(R.id.ivImage3)
            4 -> view.findViewById(R.id.ivImage4)
            5 -> view.findViewById(R.id.ivImage5)
            else -> view.findViewById(R.id.ivImage0)
        }
    }

    companion object {
        private const val MAX_IMAGE_IN_GRID = 5

        private val MAX_IMAGE_SIZE = 288
        private val MIN_IMAGE_SIZE = 100

        private val MAX_IMAGE_HEIGHT_SIZE = 400.dp
    }
}
