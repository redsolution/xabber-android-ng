package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.CustomPopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.data.util.dp
import com.xabber.presentation.application.fragments.message.MessageAdapter.Companion.INCOMING_MESSAGE
import com.xabber.presentation.application.fragments.message.MessageAdapter.Companion.OUTGOING_MESSAGE
import com.xabber.presentation.application.util.StringUtils
import com.xabber.data.xmpp.messages.MessageSendingState
import com.xabber.data.xmpp.messages.MessageSendingState.*
import com.xabber.databinding.ItemMessageOutgoingBinding
import java.util.*


class OutgoingMessageVH(
    private val binding: ItemMessageOutgoingBinding,
    private val listener: MessageAdapter.Listener
) : BasicViewHolder(binding.root) {

   @RequiresApi(Build.VERSION_CODES.N)
   @SuppressLint("RestrictedApi")
   override fun bind(message: MessageDto, isNeedTail: Boolean, needDay: Boolean) {
        // text & appearance
       binding.tvContent.isVisible = message.messageBody != null
        if (message.messageBody != null) binding.tvContent.text = message.messageBody
       //  tvContent.setTextAppearance(SettingsManager.chatsAppearanceStyle()) - берем из класса настроек

       // date
        binding.messageDate.tvDate.isVisible = needDay
        binding.messageDate.tvDate.text = StringUtils.getDateStringForMessage(message.sentTimestamp)

        // time
        val date = Date(message.sentTimestamp)
        val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
        binding.tvSendingTime. text = time

        // status
        if (message.isOutgoing) setStatus(
           binding.imageMessageStatus,
            message.messageSendingState
        )


//dateMessage.isVisible = need

        //  val nextMessage = getMessage(position + 1)
        //   if (nextMessage != null)

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(50.dp, 2.dp, if (isNeedTail) 2.dp else 11.dp, 2.dp)
            binding.balloon.layoutParams = params
            binding.balloon.setPadding(16.dp, 8.dp, if (isNeedTail) 14.dp else 8.dp, 10.dp)



        val typedValue = TypedValue()
        binding.root.context.theme.resolveAttribute(R.attr.message_background, typedValue, true)
        val shadowDrawable: Drawable =
            ContextCompat.getDrawable(binding.root.context, R.drawable.fwd_out_shadow)!!
        shadowDrawable.setColorFilter(
            ContextCompat.getColor(binding.root.context, R.color.black),
            PorterDuff.Mode.MULTIPLY
        )

            binding.balloon.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    binding.root.context,
                    if (isNeedTail) R.drawable.msg_out else R.drawable.msg
                ))



            // tvContent.marginStart = if (needTail) 20 else 11

          binding.root.setOnClickListener {
            val popup = CustomPopupMenu(it.context, it, Gravity.CENTER)
              if (message.isOutgoing) popup.inflate(R.menu.context_menu_message_outgoing)
              else popup.inflate(R.menu.context_menu_message_incoming)

              val menuHealper = MenuPopupHelper(it.context, popup.menu as MenuBuilder, binding.root)
              menuHealper.setForceShowIcon(true)
          menuHealper.show()

            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.copy -> {}
                    R.id.forward -> {}
                   R.id.reply -> {}
                    R.id.delete_message -> {}
                    R.id.edit -> { listener.editMessage(message.primary) }
                }
                true
            }
            popup.show()
            true
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
                    R.drawable.msg_out
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
        binding.balloon.background = balloonDrawable

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