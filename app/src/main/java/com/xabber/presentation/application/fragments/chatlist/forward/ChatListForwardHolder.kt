package com.xabber.presentation.application.fragments.chatlist.forward

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.models.dto.ChatListDto
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.presentation.application.dialogs.TimeMute
import com.xabber.utils.DateFormatter
import com.xabber.utils.dp

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
            val multiTransformation = MultiTransformation(CircleCrop())

            Glide.with(itemView).load(chatListDto.drawableId)
                .apply(RequestOptions.bitmapTransform(multiTransformation))
                .into(binding.imChatListItemAvatar)

            // name
            tvChatItemName.text = if (chatListDto.customNickname.isNotEmpty()) chatListDto.customNickname else if(chatListDto.opponentNickname.isNotEmpty()) chatListDto.opponentNickname else chatListDto.opponentJid

            // last message
            tvChatListLastMessage.text =
                if (chatListDto.lastMessageBody == null) "" else chatListDto.lastMessageBody

            // timeStamp
            tvTimestamp.text =
                DateFormatter.dateFormat(chatListDto.lastMessageDate)

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
            if (chatListDto.entity == RosterItemEntity.Contact) {
                val icon = when (chatListDto.status) {
                    ResourceStatus.Offline -> R.drawable.status_online
                    ResourceStatus.Away -> R.drawable.status_away
                    ResourceStatus.Online -> R.drawable.status_online
                    ResourceStatus.Xa -> R.drawable.ic_status_xa
                    ResourceStatus.Dnd -> R.drawable.status_dnd
                    ResourceStatus.Chat -> R.drawable.status_chat
                }
                imChatStatus.isVisible = false
//                imChatStatus14.isVisible = true
//                imChatStatus14.setImageResource(icon)
            } else {
                val icon =
                    when (chatListDto.entity) {
                        RosterItemEntity.Server -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_server_unavailable
                                else -> R.drawable.status_server
                            }
                        }
                        RosterItemEntity.Bot -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_bot_unavailable
                                ResourceStatus.Away -> R.drawable.status_bot_away
                                ResourceStatus.Online -> R.drawable.status_bot_online
                                ResourceStatus.Xa -> R.drawable.status_bot_xa
                                ResourceStatus.Dnd -> R.drawable.status_bot_dnd
                                ResourceStatus.Chat -> R.drawable.status_bot_chat

                            }
                        }
                        RosterItemEntity.IncognitoChat -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_incognito_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_incognito_group_away
                                ResourceStatus.Online -> R.drawable.status_incognito_group_online
                                ResourceStatus.Xa -> R.drawable.status_incognito_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_incognito_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_incognito_group_chat

                            }
                        }

                        RosterItemEntity.Groupchat -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_public_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_public_group_away
                                ResourceStatus.Online -> R.drawable.status_public_group_online
                                ResourceStatus.Xa -> R.drawable.status_public_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_public_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_public_group_chat
                            }
                        }

                        RosterItemEntity.PrivateChat -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_private_chat_unavailable
                                ResourceStatus.Away -> R.drawable.status_private_chat_away
                                ResourceStatus.Online -> R.drawable.status_private_chat_online
                                ResourceStatus.Xa -> R.drawable.status_private_chat_xa
                                ResourceStatus.Dnd -> R.drawable.status_private_chat_dnd
                                ResourceStatus.Chat -> R.drawable.status_private_chat
                            }
                        }
                        else -> {
                            0
                        }
                    }
                imChatStatus.isVisible = true
//                imChatStatus14.isVisible = false
//                imChatStatus14.setImageResource(icon)
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