package com.xabber.application.fragments.chat

import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.application.util.getStatusColor
import com.xabber.application.util.getStatusIcon
import com.xabber.data.dto.ChatDto
import com.xabber.data.dto.RosterItemEntity.CONTACT
import com.xabber.data.dto.RosterItemEntity.ISSUE
import com.xabber.databinding.ItemChatBinding

class ChatViewHolder(
    private val binding: ItemChatBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(chat: ChatDto) {
        with(binding) {
            profileDivider.setBackgroundColor(
                itemView.resources.getColor(
                    chat.colorId,
                    itemView.context.theme
                )
            )

            chatImage.setBackgroundColor(
                itemView.resources.getColor(
                    chat.colorId,
                    itemView.context.theme
                )
            )

            Glide.with(itemView)
                .load(R.drawable.ic_avatar_placeholder)
                .centerCrop()
                .skipMemoryCache(true)
                .into(chatImage)

            val chatStatusContainer = chatStatusContainer12
             //   if (chat.entity in listOf(CONTACT, ISSUE))
            //        chatStatusContainer12
            //    else
             //       chatStatusContainer16

            chatStatusContainer.isVisible = true
            chatStatusContainer.setCardBackgroundColor(
                itemView.resources.getColor(
                    chat.getStatusColor(),
                    itemView.context.theme
                )
            )

            val chatStatus = chatStatus12
             //   if (chat.entity in listOf(CONTACT, ISSUE))
            //        chatStatus12
           //     else
          //          chatStatus16

            chat.entity.getStatusIcon()?.let { iconId ->
                Glide.with(itemView)
                    .load(iconId)
                    .centerCrop()
                    .skipMemoryCache(true)
                    .into(chatStatus)
                chatStatus.background = ResourcesCompat.getDrawable(
                    itemView.resources,
                    iconId,
                    itemView.context.theme
                )
            }

            chatName.text = chat.username

            // допилить
            chatTimestamp.text = "12:00"

            chatMuted.isVisible = chat.isMuted
            chatPinned.isVisible = chat.isPinned

            when {
                chat.unread != 0 -> {
                    unreadMessagesWrapper.isVisible = true
                    unreadMessagesCount.text = chat.unread.toString()
                }
                else -> {
                    chatStatusImage.isVisible = true
                    var image: Int? = null
                    var tint: Int? = null
                    when (chat.state) {
                        MessageState.SENDING -> {
                            tint = R.color.grey_500
                            image = R.drawable.ic_material_clock_outline_24
                        }
                        MessageState.SENT -> {
                            tint = R.color.grey_500
                            image = R.drawable.ic_material_check_24
                        }
                        MessageState.DELIVERED -> {
                            tint = R.color.green_500
                            image = R.drawable.ic_material_check_24
                        }
                        MessageState.READ -> {
                            tint = R.color.green_500
                            image = R.drawable.ic_material_check_all_24
                        }
                        MessageState.ERROR -> {
                            tint = R.color.red_500
                            image = R.drawable.ic_material_alert_circle_outline_24
                        }
                        MessageState.NOT_SENT -> {
                            tint = R.color.grey_500
                            image = R.drawable.ic_material_clock_outline_24
                        }
                        MessageState.UPLOADING -> {
                            tint = R.color.blue_500
                            image = R.drawable.ic_material_clock_outline_24
                        }
                        MessageState.NONE -> {
                            chatStatusImage.isVisible = false
                        }
                    }
                    if (tint != null && image != null) {
                        Glide.with(itemView)
                            .load(image)
                            .centerCrop()
                            .skipMemoryCache(true)
                            .into(chatStatusImage)
                        chatStatusImage.setColorFilter(
                            ContextCompat.getColor(itemView.context, tint),
                            PorterDuff.Mode.SRC_IN
                        )
                    }
                }
            }

            if (chat.hasAttachment)
                chatMessage.setTextColor(
                    itemView.resources.getColor(
                        chat.colorId,
                        itemView.context.theme
                    )
                )

            if (chat.userNickname != null) {
                val spannable = SpannableString("${chat.userNickname}\n${chat.message}")
                spannable.setSpan(
                    ForegroundColorSpan(
                        itemView.resources.getColor(
                            R.color.grey_900,
                            itemView.context.theme
                        )
                    ),
                    0,
                    chat.userNickname.length,
                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                )
                chatMessage.text = spannable
            } else
                chatMessage.text = chat.message


            if (chat.isDrafted) {
                val spannable = SpannableString("Drafted: ${chat.message}")
                spannable.setSpan(
                    ForegroundColorSpan(
                        itemView.resources.getColor(
                            R.color.red_500,
                            itemView.context.theme
                        )
                    ),
                    0,
                    8,
                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                )
                chatMessage.text = spannable
            }

            chatSyncImage.isVisible = chat.isSynced
            if (chat.isSystemMessage)
                chatMessage.setTypeface(null, Typeface.ITALIC)

            binding.root.setOnClickListener {

            }
        }
    }
}