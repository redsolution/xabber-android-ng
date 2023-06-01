package com.xabber.presentation.application.fragments.test

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.application.fragments.chat.RoundedCornerOutlineProvider
import com.xabber.presentation.application.fragments.chat.message.ImageGrid
import com.xabber.presentation.application.fragments.chat.message.RoundedBorders
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.dp

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
        view: View,
        attachments: ArrayList<MessageReferenceDto>,
        clickListener: View.OnClickListener?
    ) {
        if (attachments.size == 1) {
            val imageView = getImageView(view, 0)

            bindOneImage(attachments[0], view, imageView, view)
            imageView.setOnClickListener(clickListener)
        } else {
            val tvCounter = view.findViewById<TextView>(R.id.tvCounter)
            var index = 0
            loop@ for (attachment in attachments) {
                if (index > 5) break@loop
                val imageView = getImageView(view, index)
                bindImage(attachment, view, imageView)
                imageView.setOnClickListener(clickListener)
                index++
            }
            if (tvCounter != null) {
                if (attachments.size > MAX_IMAGE_IN_GRID) {
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
                    text = StringBuilder("+").append(attachmentRealmObjects.size - MAX_IMAGE_IN_GRID)
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
        }

    }



    private fun bindImage(attachment: MessageReferenceDto, parent: View, imageView: ImageView) {

        Glide.with(imageView.context)
            .load(attachment.uri)
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)
    }

    private fun bindOneImage(attachment: MessageReferenceDto, parent: View, imageView: ImageView, view: View) {

        val radius =
            if (MessageChanger.cornerValue > 4) (MessageChanger.cornerValue - 4) else 1
        val timeStampRadius = if (radius > 3) radius - 3 else 1
        val timeStampBackground = when(timeStampRadius) {
            1 -> R.drawable.time_stamp_1px
            2 -> R.drawable.time_stamp_2px
            3 -> R.drawable.time_stamp_3px
            4 -> R.drawable.time_stamp_4px
            5 -> R.drawable.time_stamp_5px
            6 -> R.drawable.time_stamp_6px
            7 -> R.drawable.time_stamp_7px
            8 -> R.drawable.time_stamp_8px
            9 -> R.drawable.time_stamp_9px
            else -> R.drawable.time_stamp_1px
        }
        val lin = view.findViewById<LinearLayout>(R.id.image_message_info)
        lin.setBackgroundResource(timeStampBackground)
//            imageView.outlineProvider = RoundedCornerOutlineProvider(radius.dp.toFloat())
//                    imageView.clipToOutline = true
      // imageView.maxWidth = DisplayManager.getWidthDp()
      //  imageView.maxHeight = DisplayManager.getMainContainerWidth() + 100

        Glide.with(imageView.context)
            .load(attachment.uri)
            .centerCrop()
            .into(imageView)
//var width = 0
//        var height = 0
//
//        Glide.with(imageView.context)
//            .asBitmap()
//            .load(attachment.uri)
//            .listener(object : RequestListener<Bitmap> {
//                override fun onResourceReady(
//                    resource: Bitmap?,
//                    model: Any?,
//                    target: com.bumptech.glide.request.target.Target<Bitmap>?,
//                    dataSource: DataSource?,
//                    isFirstResource: Boolean
//                ): Boolean {
//                   width = resource?.width ?: 0
//                    height = resource?.height ?: 0
//                   return true
//                  //  if (width <= 0 || height <= 0) return
//                }
//
//                override fun onLoadFailed(
//                    e: GlideException?,
//                    model: Any?,
//                    target: Target<Bitmap>?,
//                    isFirstResource: Boolean
//                ): Boolean {
//                    return false
//                }
//            })
//            .into(imageView)
//
//        if (height > MAX_IMAGE_HEIGHT_SIZE) height = MAX_IMAGE_HEIGHT_SIZE
//        Glide.with(imageView.context)
//            .asBitmap().centerCrop().override(width, height)
//            .load(attachment.uri).into(imageView)
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
                    scaledHeight = (height / (width.toDouble() / MIN_IMAGE_SIZE)).toInt().coerceAtMost(
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
                    scaledWidth = (width / (height.toDouble() / MIN_IMAGE_SIZE)).toInt().coerceAtMost(
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
