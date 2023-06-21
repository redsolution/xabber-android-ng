package com.xabber.presentation.application.fragments.chat.message

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.LinearLayoutCompat
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
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.XabberApplication
import java.util.*

class ImageGrid {

    private val centerCropTransformation: MultiTransformation<Bitmap> by lazy {
        createStandardTransformation(CenterCrop())
    }

    private val justRoundedTransformation: MultiTransformation<Bitmap> by lazy {
        createStandardTransformation()
    }

    private fun createStandardTransformation(vararg extraTransformation: BitmapTransformation) =
        MultiTransformation(
            listOf(
                RoundedCorners(IMAGE_ROUNDED_CORNERS),
                RoundedBorders(IMAGE_ROUNDED_BORDER_CORNERS, IMAGE_ROUNDED_BORDER_WIDTH),
                *extraTransformation
            )
        )

    fun inflateView(parent: ViewGroup, imageCount: Int): View =
        LayoutInflater.from(parent.context).inflate(getLayoutResource(imageCount), parent, false)

    fun bindView(
        view: View,
        messageRealmObject: MessageDto,
        clickListener: View.OnClickListener?,
        messageVhExtraData: MessageVhExtraData,
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
                    .also { setupImageViewIntoRigidGridCell(attachmentRealmObject, it) }
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

        bindMessageStatus(messageRealmObject, view, messageVhExtraData)
    }

    private fun bindMessageStatus(
        message: MessageDto, view: View, messageVhExtraData: MessageVhExtraData
    ) {
        fun getTime(): String {
            var time = DateFormat.getTimeFormat(XabberApplication.applicationContext()).format(Date(message.sentTimestamp))
            message.sentTimestamp.let {
                val delay = view.context.getString(
                    if (!message.isOutgoing) R.string.chat_delay else R.string.chat_typed,
                    DateFormat.getTimeFormat(XabberApplication.applicationContext()).format(Date(it))
                )
                time += " ($delay)"
            }
            message.editTimestamp.let {
                time += view.context.getString(
                    R.string.edited,
                    DateFormat.getTimeFormat(XabberApplication.applicationContext()).format(Date(it))
                )
            }
            return time
        }

        if (message.isAttachmentImageOnly) {
            view.findViewById<LinearLayoutCompat>(R.id.message_info).apply {
                visibility = View.VISIBLE
                setBackgroundColor(Color.BLACK)
                alpha = 0.6f
            }
            view.findViewById<TextView>(R.id.message_time).apply {
                setTextColor(context.resources.getColor(R.color.white))
                text = getTime()
            }

            view.findViewById<ImageView>(R.id.message_status_icon)?.apply {
                if (!message.isOutgoing) {
                    visibility = View.GONE
                } else {
                    MessageDeliveryStatusHelper.setupStatusImageView(message, this)
                }
            }

        } else {
            view.findViewById<LinearLayout>(R.id.message_info).visibility = View.GONE
        }
    }

    private fun setupImageViewIntoRigidGridCell(
        referenceRealmObject: MessageReferenceDto, imageView: ImageView
    ) {
        val uri = referenceRealmObject.uri.takeIf { it!!.isNotEmpty() }


        Glide.with(imageView.context)
            .load(uri)
            .transform(centerCropTransformation)
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)
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
            .transform(justRoundedTransformation)
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
//                    XabberApplication.applicationContext().runInBackground {
//                        DatabaseManager.getInstance().defaultRealmInstance.use { realm ->
//                            realm.executeTransactionAsync { realm1: Realm ->
//                                realm1.where(ReferenceRealmObject::class.java)
//                                    .equalTo(ReferenceRealmObject.Fields.UNIQUE_ID, attachmentId)
//                                    .findFirst()
//                                    ?.apply {
//                                        this.w = width
//                                        this.imageHeight = height
//                                    }
//                            }
//                        }
//
//                        XabberApplication.applicationContext().runOnUiThread {
//                            imageView.setImageBitmap(resource)
//                        }
//                    }
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
            .transform(justRoundedTransformation)
            .placeholder(R.drawable.ic_recent_image_placeholder)
            .error(R.drawable.ic_recent_image_placeholder)
            .into(imageView)

        scaleImage(imageView.layoutParams, height, width)
    }

    @LayoutRes
    private fun getLayoutResource(imageCount: Int):  Int {
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
                    scaledHeight = (height / (width.toDouble() / MIN_IMAGE_SIZE)).toInt().coerceAtMost(MAX_IMAGE_HEIGHT_SIZE)
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
                    scaledWidth = (width / (height.toDouble() / MIN_IMAGE_SIZE)).toInt().coerceAtMost(MAX_IMAGE_SIZE)
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

    companion object {
        private val resources = XabberApplication.applicationContext().resources

        private const val MAX_IMAGE_IN_GRID = 6

        private val MAX_IMAGE_SIZE = 288
        private val MIN_IMAGE_SIZE = 100

        private val MAX_IMAGE_HEIGHT_SIZE = 400

        private val IMAGE_ROUNDED_CORNERS = 2
        private val IMAGE_ROUNDED_BORDER_CORNERS = 2

        const val IMAGE_ROUNDED_BORDER_WIDTH = 0
    }

}