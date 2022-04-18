package com.xabber.presentation.application.fragments.message

import android.content.Context
import android.graphics.PorterDuff
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.data.dto.MessageState
import com.xabber.data.dto.MessageState.*
import com.xabber.databinding.ItemMessageBinding
import java.util.*

class MessageViewHolder(
    private val binding: ItemMessageBinding,
    private val onAvatarClick: (MessageDto) -> Unit = {},
    private val onMessageClick: (MessageDto) -> Unit = {},
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        messageDto: MessageDto,
        isMessageNeedDisplayName: Boolean,
        isMessageNeedTail: Boolean
    ) {
        with(binding) {
            if (isMessageNeedDisplayName)
                messageSender.text = messageDto.sender?.displayName ?: messageDto.sender?.jid
            else
                messageSender.isVisible = false
            if (messageDto.text != null) {
                binding.messageContainer.messageText.text = messageDto.text
                binding.messageContainer.messageInfo.messageTime.text = getTime(messageDto)
                setStatus(binding.messageContainer.messageInfo.messageStatusIcon, messageDto.state)
            } else {
                binding.messageBottomStatus.messageTime.text = getTime(messageDto)
            }

            setBackground(messageDto, isMessageNeedTail)
        }
    }

    private fun setStatus(imageView: ImageView, messageState: MessageState) {
        imageView.isVisible = true
        var image: Int? = null
        var tint: Int? = null
        when (messageState) {
            SENDING -> {
                tint = R.color.grey_500
                image = R.drawable.ic_material_clock_outline_24
            }
            SENT -> {
                tint = R.color.grey_500
                image = R.drawable.ic_material_check_24
            }
            DELIVERED -> {
                tint = R.color.green_500
                image = R.drawable.ic_material_check_24
            }
            READ -> {
                tint = R.color.green_500
                image = R.drawable.ic_material_check_all_24
            }
            ERROR -> {
                tint = R.color.red_500
                image = R.drawable.ic_material_alert_circle_outline_24
            }
            NOT_SENT -> {
                tint = R.color.grey_500
                image = R.drawable.ic_material_clock_outline_24
            }
            UPLOADING -> {
                tint = R.color.blue_500
                image = R.drawable.ic_material_clock_outline_24
            }
            NONE -> {
                imageView.isVisible = false
            }
        }
        if (tint != null && image != null) {
            Glide.with(itemView)
                .load(image)
                .centerCrop()
                .skipMemoryCache(true)
                .into(imageView)
            imageView.setColorFilter(
                ContextCompat.getColor(itemView.context, tint),
                PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun setBackground(
        messageDto: MessageDto,
        isMessageNeedTail: Boolean
    ) {
        with(binding) {
            val balloonDrawable = ResourcesCompat.getDrawable(
                itemView.resources,
                if (isMessageNeedTail)
                    if (messageDto.isOutgoing)
                        R.drawable.msg_out
                    else
                        R.drawable.msg_in
                else
                    R.drawable.msg,
                itemView.context.theme
            )!!
            if (!messageDto.isOutgoing)
                balloonDrawable.setColorFilter(
                    itemView.resources.getColor(
                        R.color.blue_100,
                        itemView.context.theme
                    ), PorterDuff.Mode.MULTIPLY
                )
            messageBalloon.background = balloonDrawable

            val shadowDrawable = ResourcesCompat.getDrawable(
                itemView.resources,
                if (isMessageNeedTail)
                    if (messageDto.isOutgoing)
                        R.drawable.msg_out_shadow
                    else
                        R.drawable.msg_in_shadow
                else
                    R.drawable.msg_shadow,
                itemView.context.theme
            )!!
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                shadowDrawable.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
//                    itemView.resources.getColor(R.color.grey_300, itemView.context.theme),
//                    BlendModeCompat.MULTIPLY
//                )
//            }
//            else {
            shadowDrawable.setColorFilter(
                itemView.resources.getColor(
                    R.color.black,
                    itemView.context.theme
                ), PorterDuff.Mode.MULTIPLY
            )
//            }
            messageShadow.background = shadowDrawable

            messageShadow.updateLayoutParams<LinearLayout.LayoutParams> {
                gravity =
                    if (messageDto.isOutgoing)
                        Gravity.END
                    else
                        Gravity.START
                setMargins(
                    dipToPxFloat(
                        if (isMessageNeedTail)
                            if (messageDto.isOutgoing)
                                50f
                            else
                                3f
                        else
                            11f,
                        itemView.context
                    ).toInt(),
                    dipToPxFloat(3f, itemView.context).toInt(),
                    dipToPxFloat(
                        if (isMessageNeedTail)
                            if (messageDto.isOutgoing)
                                3f
                            else
                                50f
                        else
                            11f,
                        itemView.context
                    ).toInt(),
                    dipToPxFloat(3f, itemView.context).toInt(),
                )
            }

            messageBalloon.setPadding(
                dipToPxFloat(
                    if (isMessageNeedTail)
                        if (messageDto.isOutgoing)
                            12f
                        else
                            20f
                    else
                        12f,
                    itemView.context
                ).toInt(),
                dipToPxFloat(8f, itemView.context).toInt(),
                dipToPxFloat(
                    if (isMessageNeedTail)
                        if (messageDto.isOutgoing)
                            20f
                        else
                            12f
                    else
                        12f, itemView.context
                ).toInt(),
                dipToPxFloat(8f, itemView.context).toInt(),
            )
        }
    }

    private fun getTime(messageDto: MessageDto): String {
        var time = DateFormat.getTimeFormat(itemView.context.applicationContext)
            .format(Date(messageDto.sentTimestamp))
        messageDto.delayTimestamp?.let {
            val delay = itemView.context.getString(
                if (messageDto.isOutgoing) R.string.chat_typed else R.string.chat_delay,
                DateFormat.getTimeFormat(itemView.context.applicationContext).format(Date(it))
            )
            time += " ($delay)"
        }
        messageDto.editTimestamp?.let {
            time += itemView.context.getString(
                R.string.edited,
                DateFormat.getTimeFormat(itemView.context.applicationContext).format(Date(it))
            )
        }

        return time
    }
}

fun dipToPxFloat(dip: Float, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dip,
        context.resources.displayMetrics
    )
}
