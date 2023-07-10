package com.xabber.presentation.application.fragments.chat.message

import android.graphics.PorterDuff
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.StatusMaker
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.StringUtils
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.dp
import java.util.*

class ImageGridBuilder {

    fun inflateView(parent: ViewGroup, imageCount: Int): View {
        return LayoutInflater.from(parent.context)
            .inflate(getLayoutResource(imageCount), parent, false)
    }

    fun bindView(
        view: View, message: MessageDto,
        images: ArrayList<MessageReferenceDto>
    ) {
        val cornerRadius =
            if (ChatSettingsManager.cornerValue > 5) (ChatSettingsManager.cornerValue - 4) else 1  // радиус закругления уголка картинки

        val isNeedRoundBottomCorners =
            message.messageBody.isEmpty() && message.references.size == images.size

        var index = 0
        loop@ for (image in images) {
            if (index > 5) break@loop

            bindImage(view, index, image, cornerRadius, isNeedRoundBottomCorners, images.size)
            index++
        }
        if (images.size > 6) {
            val tvCounter = view.findViewById<TextView>(R.id.tvCounter)
            tvCounter.text = StringBuilder("+").append(images.size - MAX_IMAGE_IN_GRID)
            tvCounter.isVisible = true
        }

        val timeStampRadius =
            if (cornerRadius > 4) cornerRadius - 3 else 1  // радиус закругление штампа с датой/статусом

        val messageInfo = view.findViewById<LinearLayoutCompat>(R.id.message_info)
        messageInfo.isVisible = isNeedRoundBottomCorners

        if (messageInfo.isVisible) {
            val params = messageInfo.layoutParams as FrameLayout.LayoutParams
            val margin = ChatSettingsManager.timeStampMargin.dp  // отступ штампа от края картинки

            params.setMargins(margin, margin, margin, margin)
            messageInfo.layoutParams = params
            val timeStampBackground = getTimeStampBackground(timeStampRadius)
            messageInfo.setBackgroundResource(timeStampBackground)

            val tvTime = view.findViewById<TextView>(R.id.tv_image_sending_time)
            val date =
                Date(if (message.editTimestamp > 0) message.editTimestamp else message.sentTimestamp)
            val time = StringUtils.getTimeText(view.context, date)
            tvTime.text = if (message.editTimestamp > 0) view.context.resources.getString(R.string.edit) + " $time" else time

            val statusIcon =
                view.findViewById<ImageView>(R.id.iv_image_message_status)    // статус сообщения
            val iconAndTint = StatusMaker.deliverMessageStatusIcon(message.messageSendingState)
            val icon = iconAndTint.first
            val tint = iconAndTint.second
            if (icon != null && tint != null) {
                statusIcon.setImageResource(icon)
                statusIcon.setColorFilter(
                    ContextCompat.getColor(view.context, tint),
                    PorterDuff.Mode.SRC_IN
                )
            }
        }
    }

    private fun bindImage(
        view: View,
        index: Int,
        image: MessageReferenceDto,
        cornerRadius: Int,
        isNeedRoundBottomCorners: Boolean,
        imagesSize: Int
    ) {

        val innerRadius =
            if (cornerRadius <= 2) cornerRadius else 2 // радиус скругления внутреннего уголка между imageView

        val isOneImage = imagesSize == 1

        val shape = getShapeView(view, index)
        val radii = when (index) {    // скругление углов для каждого imageView
            0 -> {
                if (imagesSize < 5) floatArrayOf(
                    cornerRadius.dp.toFloat(),
                    cornerRadius.dp.toFloat(),
                    if (isOneImage) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                    if (isOneImage) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                    if (isNeedRoundBottomCorners && isOneImage) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                    if (isNeedRoundBottomCorners && isOneImage) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                    if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                    if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat()
                ) else
                    floatArrayOf(
                        cornerRadius.dp.toFloat(),
                        cornerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat()
                    )
            }
            1 -> {
                if (imagesSize < 3) floatArrayOf(
                    innerRadius.dp.toFloat(),
                    innerRadius.dp.toFloat(),
                    cornerRadius.dp.toFloat(),
                    cornerRadius.dp.toFloat(),
                    if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                    if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                    innerRadius.dp.toFloat(),
                    innerRadius.dp.toFloat()
                ) else
                    floatArrayOf(
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        cornerRadius.dp.toFloat(),
                        cornerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat(),
                        innerRadius.dp.toFloat()
                    )
            }
            2 -> floatArrayOf(
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat()
            )
            3 or 5 -> floatArrayOf(
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat()
            )
            4 -> floatArrayOf(
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                innerRadius.dp.toFloat(),
                if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat(),
                if (isNeedRoundBottomCorners) cornerRadius.dp.toFloat() else innerRadius.dp.toFloat()
            )
            else -> null
        }
        val shapeDrawable = ShapeDrawable(
            RoundRectShape(
                radii,
                null,
                null
            )
        )
        shape?.setDrawable(shapeDrawable)
        val videoLabel = getVideoLabel(view, index)
        videoLabel?.isVisible =
            FileCategory.determineFileCategory(image.mimeType) == FileCategory.VIDEO  // показываем значок "видео" если это видео
        val imageView = getImageView(view, index)

        if (imageView != null) {
            if (imagesSize > 6 && index == 5) imageView.setColorFilter(
                ContextCompat.getColor(
                    imageView.context,
                    R.color.grey_transparent
                )
            )
            if (isOneImage) {   // если картинка одна, загружаем ее учитывая соотношение сторон, но не превышая максимально допустимый размер
                val maxWidth = (DisplayManager.screenWidth() * 0.8).toInt()
                val maxHeight = (DisplayManager.screenHeight() * 0.5).toInt()
                Glide.with(imageView.context)
                    .load(image.uri)
                    .override(maxWidth, maxHeight)
                    .fitCenter()
                    .placeholder(R.drawable.ic_recent_image_placeholder)
                    .error(R.drawable.ic_recent_image_placeholder)
                    .into(imageView)
            } else {   // если картинок несколько, загружаем их в готовую сетку
                Glide.with(imageView.context)
                    .load(image.uri)
                    .placeholder(R.drawable.ic_recent_image_placeholder)
                    .error(R.drawable.ic_recent_image_placeholder)
                    .into(imageView)
            }
        }
    }

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

    private fun getLayoutResource(imageCount: Int): Int =
        when (imageCount) {
            1 -> R.layout.image_grid_1
            2 -> R.layout.image_grid_2
            3 -> R.layout.image_grid_3
            4 -> R.layout.image_grid_4
            5 -> R.layout.image_grid_5
            else -> R.layout.image_grid_6
        }

    private fun getImageView(view: View, index: Int): ImageView? =
        when (index) {
            0 -> view.findViewById(R.id.ivImage0)
            1 -> view.findViewById(R.id.ivImage1)
            2 -> view.findViewById(R.id.ivImage2)
            3 -> view.findViewById(R.id.ivImage3)
            4 -> view.findViewById(R.id.ivImage4)
            5 -> view.findViewById(R.id.ivImage5)
            else -> null
        }

    private fun getShapeView(view: View, index: Int): ShapeOfView? =
        when (index) {
            0 -> view.findViewById(R.id.shape0)
            1 -> view.findViewById(R.id.shape1)
            2 -> view.findViewById(R.id.shape2)
            3 -> view.findViewById(R.id.shape3)
            4 -> view.findViewById(R.id.shape4)
            5 -> view.findViewById(R.id.shape5)
            else -> null
        }

    private fun getVideoLabel(view: View, index: Int): ImageView? =
        when (index) {
            0 -> view.findViewById(R.id.iv_video_label_0)
            1 -> view.findViewById(R.id.iv_video_label_1)
            2 -> view.findViewById(R.id.iv_video_label_2)
            3 -> view.findViewById(R.id.iv_video_label_3)
            4 -> view.findViewById(R.id.iv_video_label_4)
            5 -> view.findViewById(R.id.iv_video_label_5)
            else -> null
        }

    companion object {
        private const val MAX_IMAGE_IN_GRID = 6
    }

}
