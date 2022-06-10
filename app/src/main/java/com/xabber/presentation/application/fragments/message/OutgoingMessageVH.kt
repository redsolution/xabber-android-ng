package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
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
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.data.util.dp
import com.xabber.data.xmpp.messages.MessageSendingState
import com.xabber.data.xmpp.messages.MessageSendingState.*
import com.xabber.databinding.ItemMessageOutgoingBinding
import com.xabber.presentation.application.util.StringUtils
import java.util.*

class OutgoingMessageVH(
    private val binding: ItemMessageOutgoingBinding,
    private val listener: MessageAdapter.Listener
) : BasicViewHolder(binding.root, listener) {

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("RestrictedApi")
    override fun bind(
        messageDto: MessageDto,
        isNeedTail: Boolean,
        needDay: Boolean,
        showCheckbox: Boolean,
        isNeedTitle: Boolean
    ) {
// text & appearance
        binding.tvContent.isVisible = messageDto.messageBody != null
        if (messageDto.messageBody != null) binding.tvContent.text = messageDto.messageBody
// tvContent.setTextAppearance(SettingsManager.chatsAppearanceStyle()) - берем из класса настроек

// date
        binding.messageDate.tvDate.isVisible = needDay
        binding.messageDate.tvDate.text =
            StringUtils.getDateStringForMessage(messageDto.sentTimestamp)

// time
        val date = Date(messageDto.sentTimestamp)
        val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
        binding.tvSendingTime.text = time

// status
        if (messageDto.isOutgoing) setStatus(
            binding.imageMessageStatus,
            messageDto.messageSendingState
        )


        binding.messageInfo.isVisible = messageDto.kind == null
        binding.info.isVisible = messageDto.kind != null


        binding.checkboxIncoming.isVisible = showCheckbox
//dateMessage.isVisible = need

// val nextMessage = getMessage(position + 1)
// if (nextMessage != null)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, if (isNeedTail) 2.dp else 11.dp, 0)
        params.gravity = Gravity.END
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
            )
        )

        if (messageDto.kind == null) {
            binding.replyMessage.isVisible = false
        } else {
            binding.replyMessage.isVisible = true
            binding.replyMessageTitle.text = messageDto.kind.owner
            binding.replyMessageContent.text = messageDto.kind.content
        }

// tvContent.marginStart = if (needTail) 20 else 11

        binding.root.setOnClickListener {
            Log.d("show", "$showCheckbox")
            if (showCheckbox) {
                        binding.checkboxIncoming.isChecked = !binding.checkboxIncoming.isChecked
                        if (binding.checkboxIncoming.isChecked) {
                            binding.frameLayoutBlackout.setBackgroundResource(R.color.selected)
                            binding.tvContent.setTextIsSelectable(true)
                        } else {
                            binding.frameLayoutBlackout.setBackgroundResource(R.color.transparent)
                            binding.tvContent.setTextIsSelectable(false)
                        }
                    } else {
                        val popup = CustomPopupMenu(it.context, it, Gravity.CENTER)
                        if (messageDto.isOutgoing) popup.inflate(R.menu.context_menu_message_outgoing)
                        else popup.inflate(R.menu.context_menu_message_incoming)

                        val menuHealper =
                            MenuPopupHelper(it.context, popup.menu as MenuBuilder, binding.root)
                        menuHealper.setForceShowIcon(true)
                        menuHealper.show()

                        popup.setOnMenuItemClickListener { menuItem ->
                            when (menuItem.itemId) {
                                R.id.copy -> {
                                    val text = binding.tvContent.text.toString()
                                    listener.copyText(text)
                                    showSnackbar(itemView)

                                }
                                R.id.forward -> {
                                    listener.forwardMessage(messageDto)
                                }
                                R.id.reply -> {
                                    listener.replyMessage(messageDto)
                                }
                                R.id.delete_message -> {
                                    listener.deleteMessage(messageDto)
                                }
                                R.id.edit -> {
                                    listener.editMessage(messageDto.primary)
                                }
                            }
                            true
                        }
                        popup.show()
                        true
                    }
                }


            binding.root.setOnLongClickListener {

                if (!showCheckbox) listener.onLongClick(messageDto.primary)
//                } else {
//                    binding.checkboxIncoming.isChecked = !binding.checkboxIncoming.isChecked
//                    binding.balloon.setBackgroundResource(R.color.selected)
//                    binding.tvContent.setTextIsSelectable(showCheckbox)
//                }
                true
            }
        }


    private fun showSnackbar(view: View) {
        var snackbar: Snackbar? = null

        snackbar = view.let {
            Snackbar.make(
                it,
                "The message has copied to the clipboard",
                Snackbar.LENGTH_SHORT
            )
        }
        snackbar.setTextColor(Color.YELLOW)
        snackbar.show()
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
// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
// shadowDrawable.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
// itemView.resources.getColor(R.color.grey_300, itemView.context.theme),
// BlendModeCompat.MULTIPLY
// )
// }
// else {
        shadowDrawable.setColorFilter(
            itemView.resources.getColor(
                R.color.black,
                itemView.context.theme
            ), PorterDuff.Mode.MULTIPLY
        )
// }
// messageShadow.background = shadowDrawable


    }

}
