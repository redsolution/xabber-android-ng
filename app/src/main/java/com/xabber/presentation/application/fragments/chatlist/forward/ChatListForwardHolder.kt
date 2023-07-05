package com.xabber.presentation.application.fragments.chatlist.forward

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.dto.ChatListDto
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.utils.MaskManager
import com.xabber.presentation.application.dialogs.TimeMute
import com.xabber.presentation.application.util.dateFormat
import com.xabber.utils.dp
import java.util.*

class ChatListForwardHolder(
    private val binding: ItemChatListBinding,
) : RecyclerView.ViewHolder(binding.root) {


    fun bind(chatListDto: ChatListDto, listener: ChatListForForwardAdapter.Listener) {
        with(binding) {
            if (chatListDto.isHide) {
                val par = chatGround.layoutParams as RecyclerView.LayoutParams
                par.height = 0
            } else {
                val par = chatGround.layoutParams as RecyclerView.LayoutParams
                par.height = 72.dp
            }
            // avatar
binding.shapeView.setDrawable(MaskManager.mask)
            Glide.with(itemView).load(chatListDto.drawableId)
                .into(binding.imChatListItemAvatar)

            // name
            tvChatItemName.text = if (chatListDto.customNickname.isNotEmpty()) chatListDto.customNickname else if(chatListDto.opponentNickname.isNotEmpty()) chatListDto.opponentNickname else chatListDto.opponentJid

            // last message
            tvChatListLastMessage.text =
                if (chatListDto.lastMessageBody == null) "" else chatListDto.lastMessageBody

            // timeStamp
            tvTimestamp.text =
                Date().dateFormat(chatListDto.lastMessageDate)

            // pinned -> background and icon
            if (chatListDto.pinnedDate > 0) {
                chatGround.setBackgroundResource(
                    R.drawable.clickable_pinned_chat_background
                )
            } else {
                chatGround.setBackgroundResource(R.drawable.clickable_view_group_background)
            }
            imChatListPinned.isVisible = chatListDto.pinnedDate > 0

            // muted
            val muteExpired = chatListDto.muteExpired
            val imageResource =
                if (muteExpired - System.currentTimeMillis() <= 0) null else if (
                    (muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time)
                    R.drawable.ic_bell_off_light_grey_mini else R.drawable.ic_bell_sleep_light_grey_mini
            var drawable: Drawable? = null
            if (imageResource != null) drawable =
                ContextCompat.getDrawable(binding.root.context, imageResource)
            binding.tvChatItemName.setCompoundDrawablesWithIntrinsicBounds(
                null, null, drawable, null
            )
            // unread messages

            unreadMessagesCount.isVisible = chatListDto.unread.isNotEmpty()
            unreadMessagesCount.text = chatListDto.unread


            // message status
            var image: Int? = null
            var tint: Int? = null
           imMessageStatus.isVisible =
                chatListDto.lastMessageBody != null

            when (chatListDto.lastMessageState) {
                MessageSendingState.Sending -> {
                    tint = R.color.grey_500
                    image = R.drawable.ic_clock_outline
                }
                MessageSendingState.Sent -> {
                    tint = R.color.grey_500
                    image = R.drawable.ic_check_green
                }
                MessageSendingState.Deliver -> {
                    tint = R.color.green_500
                    image = R.drawable.ic_check_green
                }
                MessageSendingState.Read -> {
                    tint = R.color.green_500
                    image = R.drawable.ic_check_all_green
                }
                MessageSendingState.Error -> {
                    tint = R.color.red_500
                    image = R.drawable.ic_exclamation_mark_outline
                }
                MessageSendingState.NotSent -> {
                    tint = R.color.grey_500
                    image = R.drawable.ic_clock_outline
                }
                MessageSendingState.Uploading -> {
                    tint = R.color.blue_500
                    image = R.drawable.ic_clock_outline
                }
                MessageSendingState.None -> {
                    imMessageStatus.isVisible = false
                }
                else -> {
                    tint = null
                    image = null
                }
            }

            if (binding.imMessageStatus.isVisible) {

                if (tint != null && image != null) {
                    binding.imMessageStatus.setImageResource(image)
                    binding.imMessageStatus.setColorFilter(
                        ContextCompat.getColor(itemView.context, tint),
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }

            // synced
            chatSyncImage.isVisible = chatListDto.isSynced

            // contact status
            val icon = when (chatListDto.entity) {
                RosterItemEntity.Contact -> R.drawable.status_contact
                RosterItemEntity.Server -> R.drawable.status_server
                RosterItemEntity.Bot -> R.drawable.status_bot_chat
                RosterItemEntity.PrivateChat -> R.drawable.status_private_chat
                RosterItemEntity.Groupchat -> R.drawable.status_public_group_online
                RosterItemEntity.IncognitoChat -> R.drawable.status_incognito_group_chat
                else -> {
                    null
                }
            }

            val tintStatus = when (chatListDto.status) {
                ResourceStatus.Online -> R.color.green_700
                ResourceStatus.Chat -> R.color.light_green_500
                ResourceStatus.Away -> R.color.amber_700
                ResourceStatus.Dnd -> R.color.red_700
                ResourceStatus.Xa -> R.color.blue_500
                ResourceStatus.Offline -> R.color.grey_500
            }

            if (icon != null) {
                binding.imChatStatus.isVisible = true
                binding.imChatStatus.setImageResource(icon)
                binding.imChatStatus.setColorFilter(
                    ContextCompat.getColor(itemView.context, tintStatus),
                    PorterDuff.Mode.SRC_IN
                )
            }

binding.root.setOnClickListener { listener.onClickItem(chatListDto.id) }
        }
        if (chatListDto.lastMessageState != null) {
            setUpMessageSendingState(chatListDto.lastMessageState)
            binding.imMessageStatus.isVisible =
                chatListDto.unread.isEmpty() && chatListDto.lastMessageBody != null
        }
    }

    private fun setUpMessageSendingState(messageSendingState: MessageSendingState) {
        var image: Int? = null
        var tint: Int? = null

        when (messageSendingState) {
            MessageSendingState.Sending -> {
                tint = R.color.grey_500
                image = R.drawable.ic_clock_outline
            }
            MessageSendingState.Sent -> {
                tint = R.color.grey_500
                image = R.drawable.ic_check_green
            }
            MessageSendingState.Deliver -> {
                tint = R.color.green_500
                image = R.drawable.ic_check_green
            }
            MessageSendingState.Read -> {
                tint = R.color.green_500
                image = R.drawable.ic_check_all_green
            }
            MessageSendingState.Error -> {
                tint = R.color.red_500
                image = R.drawable.ic_exclamation_mark_outline
            }
            MessageSendingState.NotSent -> {
                tint = R.color.grey_500
                image = R.drawable.ic_clock_outline
            }
            MessageSendingState.Uploading -> {
                tint = R.color.blue_500
                image = R.drawable.ic_clock_outline
            }
            MessageSendingState.None -> {
                binding.imMessageStatus.isVisible = false
            }
            else -> {
                tint = null
                image = null
            }
        }
    }

   }