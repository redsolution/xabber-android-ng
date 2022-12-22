package com.xabber.presentation.application.fragments.chatlist

import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DATE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_BODY
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_STATE
import com.xabber.presentation.AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT
import com.xabber.presentation.AppConstants.PAYLOAD_PINNED_POSITION_CHAT
import com.xabber.presentation.application.bottomsheet.TimeMute
import com.xabber.utils.DateFormatter
import com.xabber.utils.dp


class ChatListViewHolder(
    private val binding: ItemChatListBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(chatListDto: ChatListDto, listener: ChatListAdapter.ChatListener) {
        with(binding) {
            // color divider
            profileDivider.setBackgroundColor(
                itemView.resources.getColor(
                    chatListDto.colorId,
                    itemView.context.theme
                )
            )
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
            tvChatListName.text =
                if (chatListDto.customName.isNotEmpty()) chatListDto.customName else if (chatListDto.displayName.isNotEmpty()) chatListDto.displayName else chatListDto.opponentJid

            // last message
            tvChatListLastMessage.text =
                if (chatListDto.lastMessageBody == null) "" else chatListDto.lastMessageBody

            // timeStamp
            tvChatListTimestamp.text =
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
            binding.imChatListMuted.isVisible =
                (chatListDto.muteExpired - System.currentTimeMillis()) > 0
            if ((chatListDto.muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time) binding.imChatListMuted.setImageResource(
                R.drawable.ic_bell_off_light_grey
            ) else binding.imChatListMuted.setImageResource(R.drawable.ic_bell_sleep_light_grey)

            // unread messages

            unreadMessagesWrapper.isVisible = chatListDto.unread.isNotEmpty()
            unreadMessagesCount.text = chatListDto.unread
           unreadMessagesWrapper.setCardBackgroundColor(if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0) ContextCompat.getColorStateList(binding.root.context, R.color.grey_400) else ContextCompat.getColorStateList(binding.root.context, R.color.green_500))


            // message status
            var image: Int? = null
            var tint: Int? = null
            imChatListStatusMessage.isVisible =
                chatListDto.lastMessageBody != null && chatListDto.lastMessageBody.isNotEmpty() && chatListDto.outgoing

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
                    imChatListStatusMessage.isVisible = false
                }
                else -> {
                    tint = null
                    image = null
                }
            }

            if (binding.imChatListStatusMessage.isVisible) {

                if (tint != null && image != null) {
                    binding.imChatListStatusMessage.setImageResource(image)
                    binding.imChatListStatusMessage.setColorFilter(
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
                imChatStatus16.isVisible = false
                imChatStatus14.isVisible = true
                imChatStatus14.setImageResource(icon)
            } else {
                val icon =
                    when (chatListDto.entity) {
                        RosterItemEntity.Server -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_server_unavailable
                                else -> R.drawable.status_server_online
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
                imChatStatus16.isVisible = true
                imChatStatus14.isVisible = false
                imChatStatus14.setImageResource(icon)
            }

            // onClick
            itemView.setOnClickListener {
                listener.onClickItem(chatListDto)
            }

            // popup menu
            itemView.setOnLongClickListener {
                val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
                popup.inflate(R.menu.popup_menu_chat_list_item)
               if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0)  {
                    popup.menu.removeItem(R.id.turn_of_notifications)
                } else {
                    popup.menu.removeItem(R.id.enable_notifications)
                }

                if (chatListDto.isArchived) {
                    popup.menu.removeItem(R.id.pin_chat)
                    popup.menu.removeItem(R.id.unpin)
                } else {
                    if (chatListDto.pinnedDate > 0) {
                        popup.menu.removeItem(R.id.pin_chat)
                    } else {
                        popup.menu.removeItem(R.id.unpin)
                    }
                }
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.unpin -> listener.unPinChat(chatListDto.id)
                        R.id.pin_chat -> {
                            listener.pinChat(chatListDto.id)
                        }
                        R.id.turn_of_notifications -> {
                            listener.turnOfNotifications(chatListDto.id)
                        }
                        R.id.enable_notifications -> {
                            listener.enableNotifications(chatListDto.id)
                        }
                        R.id.customise_notifications -> {
                            listener.openSpecialNotificationsFragment()
                        }
                        R.id.delete -> {
                            listener.deleteChat(chatListDto.displayName, chatListDto.id)
                        }
                        R.id.clear_history -> {
                            listener.clearHistory(
                                chatListDto
                            )
                        }
                    }
                    true
                }
                popup.show()
                true
            }

        }
        if (chatListDto.lastMessageState != null) {
            setUpMessageSendingState(chatListDto.lastMessageState)
            binding.imChatListStatusMessage.isVisible =
                chatListDto.unread.isNotEmpty() && chatListDto.lastMessageBody != null && chatListDto.outgoing
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
                binding.imChatListStatusMessage.isVisible = false
            }
            else -> {
                tint = null
                image = null
            }
        }
    }

    fun bind(
        chatListDto: ChatListDto,
        listener: ChatListAdapter.ChatListener,
        payloads: List<Any>
    ) {
        val bundle = payloads.last() as Bundle
        for (key in bundle.keySet()) {
            when (key) {
                AppConstants.PAYLOAD_UNREAD_CHAT -> {
                    val unread = bundle.getString(AppConstants.PAYLOAD_UNREAD_CHAT)
                    binding.unreadMessagesWrapper.isVisible = !unread.isNullOrEmpty()
                    binding.unreadMessagesCount.text = unread.toString()
                }
                PAYLOAD_PINNED_POSITION_CHAT -> {
                    val pinnedPosition = bundle.getLong(PAYLOAD_PINNED_POSITION_CHAT)
                    if (pinnedPosition > 0) {
                        binding.chatGround.setBackgroundResource(
                            R.drawable.clickable_pinned_chat_background
                        )
                    } else {
                        binding.chatGround.setBackgroundResource(R.drawable.clickable_view_group_background)
                    }
                    binding.imChatListPinned.isVisible = pinnedPosition > 0
                    itemView.setOnLongClickListener {
                        val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
                        popup.inflate(R.menu.popup_menu_chat_list_item)
                        if (chatListDto.isArchived) {
                            popup.menu.removeItem(R.id.pin_chat)
                            popup.menu.removeItem(R.id.unpin)
                        } else {
                            if (chatListDto.pinnedDate > 0) {
                                popup.menu.removeItem(R.id.pin_chat)
                            } else {
                                popup.menu.removeItem(R.id.unpin)
                            }

                           if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0)  {
                                popup.menu.removeItem(R.id.turn_of_notifications)
                            } else {
                                popup.menu.removeItem(R.id.enable_notifications)
                            }

                        }
                        popup.setOnMenuItemClickListener {
                            when (it.itemId) {
                                R.id.unpin -> listener.unPinChat(chatListDto.id)
                                R.id.pin_chat -> {
                                    listener.pinChat(chatListDto.id)
                                }
                                R.id.turn_of_notifications -> {
                                    listener.turnOfNotifications(chatListDto.id)
                                }
                                R.id.enable_notifications -> {
                                    listener.enableNotifications(chatListDto.id)
                                }
                                R.id.customise_notifications -> {
                                    listener.openSpecialNotificationsFragment()
                                }
                                R.id.delete -> {
                                    listener.deleteChat(chatListDto.displayName, chatListDto.id)
                                }
                                R.id.clear_history -> {
                                    listener.clearHistory(
                                        chatListDto
                                    )
                                }
                            }
                            true
                        }
                        popup.show()
                        true
                    }
                }
                PAYLOAD_MUTE_EXPIRED_CHAT -> {
                    val muteExpired = bundle.getLong(PAYLOAD_MUTE_EXPIRED_CHAT)
                    binding.imChatListMuted.isVisible =
                        (muteExpired - System.currentTimeMillis()) > 0
                    if ((muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time) binding.imChatListMuted.setImageResource(
                        R.drawable.ic_bell_off_light_grey
                    ) else binding.imChatListMuted.setImageResource(R.drawable.ic_bell_sleep_light_grey)
                    itemView.setOnLongClickListener {
                        val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
                        popup.inflate(R.menu.popup_menu_chat_list_item)

                        if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0)  {
                            popup.menu.removeItem(R.id.turn_of_notifications)
                        } else {
                            popup.menu.removeItem(R.id.enable_notifications)
                        }
                        if (chatListDto.isArchived) {
                            popup.menu.removeItem(R.id.pin_chat)
                            popup.menu.removeItem(R.id.unpin)
                        } else {
                            if (chatListDto.pinnedDate > 0) {
                                popup.menu.removeItem(R.id.pin_chat)
                            } else {
                                popup.menu.removeItem(R.id.unpin)
                            }
                        }
                        popup.setOnMenuItemClickListener {
                            when (it.itemId) {
                                R.id.unpin -> listener.unPinChat(chatListDto.id)
                                R.id.pin_chat -> {
                                    listener.pinChat(chatListDto.id)
                                }
                                R.id.turn_of_notifications -> {
                                    listener.turnOfNotifications(chatListDto.id)
                                }
                                R.id.enable_notifications -> {
                                    listener.enableNotifications(chatListDto.id)
                                }
                                R.id.customise_notifications -> {
                                    listener.openSpecialNotificationsFragment()
                                }
                                R.id.delete -> {
                                    listener.deleteChat(chatListDto.displayName, chatListDto.id)
                                }
                                R.id.clear_history -> {
                                    listener.clearHistory(
                                        chatListDto
                                    )
                                }
                            }
                            true
                        }
                        popup.show()
                        true
                    }

                    binding.unreadMessagesWrapper.setCardBackgroundColor( if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0) ContextCompat.getColorStateList(binding.root.context, R.color.grey_400) else ContextCompat.getColorStateList(binding.root.context, R.color.green_500))




                }
                PAYLOAD_CHAT_DATE -> {
                    binding.tvChatListTimestamp.text =
                        DateFormatter.dateFormat(chatListDto.lastMessageDate)
                }
                PAYLOAD_CHAT_MESSAGE_BODY -> {
                    val lastMessageBody = bundle.getString(PAYLOAD_CHAT_MESSAGE_BODY)
                    binding.tvChatListLastMessage.text = lastMessageBody
                    binding.imChatListStatusMessage.isVisible =
                        (lastMessageBody!!.isNotEmpty() && chatListDto.outgoing)
                }
                PAYLOAD_CHAT_DRAFT_MESSAGE -> {
                    val draftMessage = bundle.getString(PAYLOAD_CHAT_DRAFT_MESSAGE)
                    if (draftMessage != null) {
                        val spannable = SpannableString("Drafted: $draftMessage")
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
                        binding.tvChatListLastMessage.text = spannable
                        if (draftMessage.isNotEmpty()) binding.tvChatListTimestamp.text =
                            DateFormatter.dateFormat(System.currentTimeMillis())
                        binding.imChatListStatusMessage.isVisible = false
                    } else {
                        binding.tvChatListLastMessage.text = chatListDto.lastMessageBody
                        binding.tvChatListTimestamp.text =
                            DateFormatter.dateFormat(chatListDto.lastMessageDate)
                        binding.imChatListStatusMessage.isVisible =
                            (chatListDto.lastMessageBody.isNotEmpty() && chatListDto.outgoing)
                    }
                }
                PAYLOAD_CHAT_MESSAGE_STATE -> {
                    val messageState =
                        bundle.getParcelable(PAYLOAD_CHAT_MESSAGE_STATE) as MessageSendingState?
                    if (messageState != null) setUpMessageSendingState(messageState)
                }
            }

        }

    }

}
