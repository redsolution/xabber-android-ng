package com.xabber.presentation.application.fragments.message

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.presentation.application.fragments.message.MessageAdapter.Companion.INCOMING_MESSAGE
import com.xabber.presentation.application.fragments.message.MessageAdapter.Companion.OUTGOING_MESSAGE
import com.xabber.presentation.application.util.StringUtils
import com.xabber.data.xmpp.messages.MessageDisplayType
import com.xabber.data.xmpp.messages.MessageSendingState
import com.xabber.data.xmpp.messages.MessageSendingState.*
import java.util.*


class MessageViewHolder(
    private val view: View,
    private val onAvatarClick: (MessageDto) -> Unit = {},
    private val onMessageClick: (MessageDto) -> Unit = {},
) : RecyclerView.ViewHolder(view) {

    private val tvContent: TextView = view.findViewById(R.id.tv_content)
    private val tvTime: TextView = view.findViewById(R.id.tv_sending_time)
    private val messageBalloon: RelativeLayout = view.findViewById(R.id.balloon)

    private val cv: CardView = view.findViewById(R.id.cv)
    private val im: ImageView = view.findViewById(R.id.image_mes)

    fun bind(itemModel: MessageDto, next: String) {
        // text & appearance
        tvContent.text =
            itemModel.messageBody //  tvContent.setTextAppearance(SettingsManager.chatsAppearanceStyle()) - берем из класса настроек

        // time
        val date = Date(itemModel.sentTimestamp)
        val time = StringUtils.getTimeText(view.context, date)
        tvTime.text = time

        // status
        if (itemModel.isOutgoing) setStatus(
            view.findViewById(R.id.image_message_status),
            itemModel.messageSendingState
        )

        // color
        //     messageBalloon.backgroundTintList = ColorStateList.valueOf(R.color.blue_50)

// image
        if (itemModel.displayType == MessageDisplayType.Files) {
            cv.isVisible = true

        } else cv.isVisible = false

        // needTail
        var needTail = false
        //  val nextMessage = getMessage(position + 1)
        //   if (nextMessage != null)
        needTail = itemModel.owner != next
        Log.d("needtail", "$needTail")
        if (itemViewType == INCOMING_MESSAGE) {
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(if (needTail) 2 else 24, 0, 0, 0)
            messageBalloon.layoutParams = params
            messageBalloon.setPadding(if (needTail) 54 else 30, 30, 30, 30)
        } else {
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, if (needTail) 2 else 24, 0)
            messageBalloon.layoutParams = params
            messageBalloon.setPadding(30, 30, if (needTail) 54 else 30, 30)
        }


        val typedValue = TypedValue()
        view.context.theme.resolveAttribute(R.attr.message_background, typedValue, true)
        val shadowDrawable: Drawable =
            ContextCompat.getDrawable(view.context, R.drawable.fwd_out_shadow)!!
        shadowDrawable.setColorFilter(
            ContextCompat.getColor(view.context, R.color.black),
            PorterDuff.Mode.MULTIPLY
        )
        if (itemViewType == OUTGOING_MESSAGE) {
            messageBalloon.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    view.context,
                    if (needTail) R.drawable.msg_out else R.drawable.msg
                )
            )

        } else {
            // tvContent.marginStart = if (needTail) 20 else 11
            messageBalloon.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    view.context,
                    if (needTail) R.drawable.msg_in else R.drawable.msg
                )
            )

        }
    }


    private fun setStatus(imageView: ImageView, messageSendingState: MessageSendingState) {
        var image: Int? = null
        var tint: Int? = null
        when (messageSendingState) {
            Sending -> {
                tint = R.color.grey_500
                image = R.drawable.ic_material_clock_outline_24
            }
            Sended -> {
                tint = R.color.grey_500
                image = R.drawable.ic_material_check_24
            }
            Deliver -> {
                tint = R.color.green_500
                image = R.drawable.ic_material_check_24
            }
            Read -> {
                tint = R.color.green_500
                image = R.drawable.ic_material_check_all_24
            }
            Error -> {
                tint = R.color.red_500
                image = R.drawable.ic_material_alert_circle_outline_24
            }
            NotSended -> {
                tint = R.color.grey_500
                image = R.drawable.ic_material_clock_outline_24
            }
            Uploading -> {
                tint = R.color.blue_500
                image = R.drawable.ic_material_clock_outline_24
            }
            None -> {
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
        //     messageShadow.background = shadowDrawable

    }
}