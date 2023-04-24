package com.xabber.presentation.application.fragments.chat.message

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.ItemMessageOutgoingBinding
import com.xabber.models.dto.MessageDto
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.messages.MessageSendingState.*
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.utils.StringUtils
import com.xabber.utils.dp
import java.util.*

class OutgoingMessageVH(
    private val binding: ItemMessageOutgoingBinding,
    private val listener: ChatAdapter.Listener
) : BasicMessageVH(binding.root, listener) {


    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("RestrictedApi")
    override fun bind(
        messageDto: MessageDto,
        _isNeedTail: Boolean,
        needDay: Boolean,
        showCheckbox: Boolean,
        isNeedTitle: Boolean, isNeedUnread: Boolean
    ) {

        val isNeedTail =
            if (messageDto.messageBody.isEmpty() && messageDto.references != null) false else _isNeedTail
// text & appearance
        binding.tvContent.isVisible = messageDto.messageBody.isNotEmpty()
        binding.tvContent.text = messageDto.messageBody
        Log.d("realm", "viewHolder ${messageDto.messageBody}")
// tvContent.setTextAppearance(SettingsManager.chatsAppearanceStyle()) - берем из класса настроек

// date
        binding.messageDate.tvDate.isVisible = needDay
        binding.messageDate.tvDate.text =
            StringUtils.getDateStringForMessage(messageDto.sentTimestamp)

// time
        val date = Date(messageDto.sentTimestamp)
        val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
        binding.messageInfo.isVisible =
            messageDto.messageBody.isNotEmpty() && messageDto.kind == null

        binding.tvSendingTime.text = if (messageDto.editTimestamp > 0) "was edit $time" else time

// status
        if (messageDto.isOutgoing) setStatus(
            binding.imageMessageStatus,
            messageDto.messageSendingState
        )


        //  binding.messageInfo.isVisible = messageDto.kind == null
        binding.info.isVisible =
            messageDto.kind != null || (messageDto.references != null && messageDto.messageBody.isNotEmpty())

//dateMessage.isVisible = need

// val nextMessage = getMessage(position + 1)
// if (nextMessage != null)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(24.dp, 0, if (isNeedTail) 2.dp else 10.dp, 0)

        params.gravity = Gravity.END
        binding.balloon.layoutParams = params
        if (messageDto.references == null && messageDto.messageBody!!.isNotEmpty()) {
            binding.balloon.setPadding(16.dp, 8.dp, if (isNeedTail) 16.dp else 8.dp, 10.dp)
        } else if (messageDto.references != null && messageDto.messageBody!!.isNotEmpty()) {
            binding.balloon.setPadding(4.dp, 4.dp, if (isNeedTail) 12.dp else 8.dp, 10.dp)
        } else {
            binding.balloon.setPadding(4.dp, 4.dp, 4.dp, -17.dp)
        }


        val typedValue = TypedValue()
        binding.root.context.theme.resolveAttribute(R.attr.message_background, typedValue, true)
        val shadowDrawable: Drawable =
            ContextCompat.getDrawable(binding.root.context, R.drawable.fwd_out_shadow)!!
        shadowDrawable.setColorFilter(
            ContextCompat.getColor(binding.root.context, R.color.black),
            PorterDuff.Mode.MULTIPLY
        )


        if (messageDto.isChecked) {
            binding.frameLayoutBlackout.setBackgroundResource(R.color.selected)
        } else {
            binding.frameLayoutBlackout.setBackgroundResource(R.color.transparent)
        }


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
            if (Check.getSelectedMode()) {
                listener.checkItem(!messageDto.isChecked, messageDto.primary)
            } else {
                val popup = PopupMenu(it.context, it, Gravity.CENTER)
                popup.setForceShowIcon(true)
                if (messageDto.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
                else popup.inflate(R.menu.popup_menu_message_incoming)

                val menuHealper =
                    MenuPopupHelper(it.context, popup.menu as MenuBuilder, binding.root)
                menuHealper.setForceShowIcon(true)
                menuHealper.show()

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = binding.tvContent.text.toString()
                            listener.copyText(text)
                        }
                        R.id.pin -> {
                            listener.pinMessage(messageDto)
                        }
                        R.id.forward -> {
                            listener.forwardMessage(messageDto)
                        }
                        R.id.reply -> {
                            listener.replyMessage(messageDto)
                        }
                        R.id.delete_message -> {
                            listener.deleteMessage(messageDto.primary)
                        }
                        R.id.edit -> {
                            listener.editMessage(messageDto.primary, messageDto.messageBody)
                        }
                    }
                    true
                }
                popup.show()
            }
        }

        setBackground(messageDto, isNeedTail)
        Log.d("yyy", "setup")
        setupReferences(messageDto)
        binding.root.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener.onLongClick(messageDto.primary)
            else {
                listener.checkItem(!messageDto.isChecked, messageDto.primary)
            }
            true
        }
    }


    private fun showSnackbar(view: View) {
        val snackbar: Snackbar?
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
                image = R.drawable.ic_clock_outline
            }
            Sent -> {
                tint = R.color.grey_500
                image = R.drawable.ic_check_green
            }
            Deliver -> {
                tint = R.color.green_500
                image = R.drawable.ic_check_green
            }
            Read -> {
                tint = R.color.green_500
                image = R.drawable.ic_check_all_green
            }
            Error -> {
                tint = R.color.red_500
                image = R.drawable.ic_exclamation_mark_outline
            }
            NotSent -> {
                tint = R.color.grey_500
                image = R.drawable.ic_clock_outline
            }
            Uploading -> {
                tint = R.color.blue_500
                image = R.drawable.ic_clock_outline
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
    }
// }
// messageShadow.background = shadowDrawable


    private fun setupReferences(messageDto: MessageDto) {
        Log.d("yyy", "holder ${messageDto.references}")
        Log.d("uuu", "${messageDto.references}")
        binding.grid1.grid1.isVisible = false
        binding.grid2.grid2.isVisible = false
        binding.grid3.grid3.isVisible = false
        binding.grid4.grid4.isVisible = false
        binding.grid5.grid5.isVisible = false
        binding.grid6.grid6.isVisible = false

//        if (messageDto.uries != null) {
//
//            when (messageDto.uries.size) {
//                0 -> {}
//                1 -> {
//                    binding.grid1.grid1.isVisible = true
//                    Glide.with(binding.root).load(messageDto.uries[0])
//                        .into(binding.grid1.ivImage0)
//                    binding.grid1.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
//                    val date = Date(messageDto.sentTimestamp)
//                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
//                    binding.grid1.tvImageSendingTime.text = time
//                }
//                2 -> {
//                    binding.grid2.grid2.isVisible = true
//                    Glide.with(binding.root).load(messageDto.uries[0])
//                        .into(binding.grid2.ivImage0)
//                    Glide.with(binding.root).load(messageDto.uries[1])
//                        .into(binding.grid2.ivImage1)
//                    binding.grid2.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
//                    val date = Date(messageDto.sentTimestamp)
//                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
//                    binding.grid2.tvImageSendingTime.text = time
//                }
//                3 -> {
//                    binding.grid3.grid3.isVisible = true
//                    Glide.with(binding.root).load(messageDto.uries[0])
//                        .into(binding.grid3.ivImage0)
//                    Glide.with(binding.root).load(messageDto.uries[1])
//                        .into(binding.grid3.ivImage1)
//                    Glide.with(binding.root).load(messageDto.uries[2])
//                        .into(binding.grid3.ivImage2)
//                    binding.grid3.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
//                    val date = Date(messageDto.sentTimestamp)
//                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
//                    binding.grid3.tvImageSendingTime.text = time
//                }
//                4 -> {
//                    binding.grid4.grid4.isVisible = true
//                    Glide.with(binding.root).load(messageDto.uries[0])
//                        .into(binding.grid4.ivImage0)
//                    Glide.with(binding.root).load(messageDto.uries[1])
//                        .into(binding.grid4.ivImage1)
//                    Glide.with(binding.root).load(messageDto.uries[2])
//                        .into(binding.grid4.ivImage2)
//                    Glide.with(binding.root).load(messageDto.uries[3])
//                        .into(binding.grid4.ivImage3)
//
//                    binding.grid4.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
//                    val date = Date(messageDto.sentTimestamp)
//                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
//                    binding.grid4.tvImageSendingTime.text = time
//                }
//                5 -> {
//                    binding.grid5.grid5.isVisible = true
//                    Glide.with(binding.root).load(messageDto.uries[0])
//                        .into(binding.grid5.ivImage0)
//                    Glide.with(binding.root).load(messageDto.uries[1])
//                        .into(binding.grid5.ivImage1)
//                    Glide.with(binding.root).load(messageDto.uries[2])
//                        .into(binding.grid5.ivImage2)
//                    Glide.with(binding.root).load(messageDto.uries[3])
//                        .into(binding.grid5.ivImage3)
//                    Glide.with(binding.root).load(messageDto.uries[4])
//                        .into(binding.grid5.ivImage4)
//
//                    binding.grid5.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
//                    val date = Date(messageDto.sentTimestamp)
//                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
//                    binding.grid5.tvImageSendingTime.text = time
//                }
//                else -> {
//                    binding.grid6.grid6.isVisible = true
//                    Glide.with(binding.root).load(messageDto.uries[0])
//                        .into(binding.grid6.ivImage0)
//                    Glide.with(binding.root).load(messageDto.uries[1])
//                        .into(binding.grid6.ivImage1)
//                    Glide.with(binding.root).load(messageDto.uries[2])
//                        .into(binding.grid6.ivImage2)
//                    Glide.with(binding.root).load(messageDto.uries[3])
//                        .into(binding.grid6.ivImage3)
//                    Glide.with(binding.root).load(messageDto.uries[4])
//                        .into(binding.grid6.ivImage4)
//                    Glide.with(binding.root).load(messageDto.uries[5])
//                        .into(binding.grid6.ivImage5)
//                    val count = messageDto.uries.size - 6
//                    if (count > 0) {
//                        binding.grid6.tvCounter.isVisible = true
//                        binding.grid6.tvCounter.text = "+ $count"
//                    }
//
//                    binding.grid6.imageMessageInfo.isVisible = messageDto.messageBody!!.isEmpty()
//                    val date = Date(messageDto.sentTimestamp)
//                    val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
//                    binding.grid6.tvImageSendingTime.text = time
//                }
//            }
//
//
//            //  setUpFile(messageDto.references, vhExtraData)
//            //   setupNonExternalGeo(messageDto)
//        } else {
//            binding.grid1.grid1.isVisible = false
//            binding.grid2.grid2.isVisible = false
//            binding.grid3.grid3.isVisible = false
//            binding.grid4.grid4.isVisible = false
//            binding.grid5.grid5.isVisible = false
//            binding.grid6.grid6.isVisible = false
//
//
//        }
    }

    private fun setUpImage(messageDto: MessageDto) {

        //     messageDto.references
        //         ?.filter { it.isImage }
//            ?.also { imageCount = it.size }
//            ?.takeIf { it.isNotEmpty() }
//            ?.let {
//                RealmList<>().apply {
//                    addAll(it)
//                }
//            }
//            ?.let {
//                val gridBuilder = ImageGrid()
//                val imageGridView = messageDto.references?.size?.let {
//                    gridBuilder.inflateView(binding.imageGridContainerFl,
//                        it
//                    )
//                }


    }


    override fun bind(
        messageDto: MessageDto,
        _isNeedTail: Boolean,
        needDay: Boolean,
        showCheckbox: Boolean,
        isNeedTitle: Boolean, isNeedUnread: Boolean, payloads: List<Any>
    ) {
        val bundle = payloads.last() as Bundle
        for (key in bundle.keySet()) {
            when (key) {
                AppConstants.PAYLOAD_MESSAGE_SENDING_STATE -> {
                    val newState =
                        bundle.getParcelable<MessageSendingState>(AppConstants.PAYLOAD_MESSAGE_SENDING_STATE)
                    if (newState != null) setStatus(binding.imageMessageStatus, newState)
                }


            }

        }
    }
}









