package com.xabber.presentation.application.fragments.chat

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.ItemChatInContactListBinding
import com.xabber.models.dto.ChatListDto
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.dialogs.TimeMute
import com.xabber.presentation.application.fragments.chatlist.ChatListAdapter
import com.xabber.utils.DateFormatter
import com.xabber.utils.dp
import com.xabber.utils.parcelable

class ChatListViewHolder1(
    private val binding: ItemChatInContactListBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(chatListDto: ChatListDto, listener: ChatListAdapter.ChatListener) {
        with(binding) {
            // height
            if (chatListDto.isHide) {
                val par = binding.root.layoutParams as RecyclerView.LayoutParams
                par.height = 0
            } else {
                val par = binding.root.layoutParams as RecyclerView.LayoutParams
                par.height = 72.dp
            }

            // color divider
            val color = ColorManager.convertColorNameToId(chatListDto.colorKey)
            accountColorIndicator.setBackgroundResource(
            color
            )

            // avatar
            val multiTransformation = MultiTransformation(CircleCrop())

            Glide.with(itemView).load(chatListDto.drawableId)
                .apply(RequestOptions.bitmapTransform(multiTransformation))
                .into(binding.imAvatar)

            // name
            tvName.text =
                if (chatListDto.customNickname.isNotEmpty()) chatListDto.customNickname else if (chatListDto.opponentNickname.isNotEmpty()) chatListDto.opponentNickname else chatListDto.opponentJid

            // last message
            if (chatListDto.draftMessage != null && chatListDto.draftMessage!!.isNotEmpty()) {
                val spannable = SpannableString("Drafted: ${chatListDto.draftMessage}")
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
                binding.tvLastMessage.text = spannable
                binding.imMessageStatus.isVisible = false
            } else {
                binding.tvLastMessage.text = HtmlCompat.fromHtml(
                    chatListDto.lastMessageBody,
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                )
            }

            // timeStamp
            tvTimestamp.text =
                DateFormatter.dateFormat(chatListDto.lastMessageDate)

            // pinned -> background and icon
            if (chatListDto.pinnedDate > 0) {
                binding.root.setBackgroundResource(
                    R.drawable.clickable_pinned_chat_background
                )
            } else {
                binding.root.setBackgroundResource(R.drawable.clickable_view_group_background)
            }
            imChatListPinned.isVisible = chatListDto.pinnedDate > 0 && chatListDto.unread.isEmpty()

            // muted
            val muteExpired = chatListDto.muteExpired
            val imageResource =
                if (muteExpired - System.currentTimeMillis() <= 0) null else if (
                    (muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time)
                    R.drawable.ic_bell_off_light_grey_mini else R.drawable.ic_bell_sleep_light_grey_mini
            var drawable: Drawable? = null
            if (imageResource != null) drawable =
                ContextCompat.getDrawable(binding.root.context, imageResource)
            binding.tvName.setCompoundDrawablesWithIntrinsicBounds(
                null, null, drawable, null
            )

            // unread messages
            tvUnreadCount.isVisible = chatListDto.unread.isNotEmpty()
            tvUnreadCount.text = chatListDto.unread
//            tvUnreadCount.setCardBackgroundColor(
//                if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0) ContextCompat.getColorStateList(
//                    binding.root.context,
//                    R.color.grey_400
//                ) else ContextCompat.getColorStateList(binding.root.context, R.color.green_500)
//            )

            // message state
            setUpMessageSendingState(chatListDto, chatListDto.lastMessageState)

            // synced
            chatSyncImage.isVisible = chatListDto.isSynced

            // chat status
            setupChatStatus(chatListDto)

            // onClick
            itemView.setOnClickListener {
                listener.onClickItem(chatListDto)
            }

            // popup menu
            itemView.setOnLongClickListener {
                setupAndShowPopupMenu(chatListDto, listener)
                true
            }
        }
    }

    private fun setupChatStatus(chatListDto: ChatListDto) {
        if (chatListDto.entity == RosterItemEntity.Contact) {
            val icon = when (chatListDto.status) {
                ResourceStatus.Offline -> R.drawable.status_online
                ResourceStatus.Away -> R.drawable.status_away
                ResourceStatus.Online -> R.drawable.status_online
                ResourceStatus.Xa -> R.drawable.ic_status_xa
                ResourceStatus.Dnd -> R.drawable.status_dnd
                ResourceStatus.Chat -> R.drawable.status_chat
            }
          //  binding.imContactStatus.isVisible = false
         //   binding.imChatStatus14.isVisible = true
            binding.imContactStatus.setImageResource(icon)
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
           // binding.imChatStatus16.isVisible = true
          //  binding.imChatStatus14.isVisible = false
            binding.imContactStatus.setImageResource(icon)
        }
    }

    private fun setUpMessageSendingState(
        chatListDto: ChatListDto,
        messageSendingState: MessageSendingState
    ) {
        var image: Int? = null
        var tint: Int? = null
        binding.imMessageStatus.isVisible =
            chatListDto.lastMessageBody.isNotEmpty() && chatListDto.lastMessageIsOutgoing && chatListDto.draftMessage == null

        if (binding.imMessageStatus.isVisible) {
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
                }
            }
            if (binding.imMessageStatus.isVisible) {
                binding.imMessageStatus.setImageResource(image!!)
                binding.imMessageStatus.setColorFilter(
                    ContextCompat.getColor(itemView.context, tint!!),
                    PorterDuff.Mode.SRC_IN
                )
            }
        }
    }

    private fun setupAndShowPopupMenu(
        chatListDto: ChatListDto,
        listener: ChatListAdapter.ChatListener
    ) {
        val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
        popup.inflate(R.menu.popup_menu_chat_list_item)
        if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0) {
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
                    listener.deleteChat(chatListDto.opponentNickname, chatListDto.id)
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
                    binding.tvUnreadCount.isVisible = !unread.isNullOrEmpty()
                    binding.tvUnreadCount.text = unread.toString()
                }
                AppConstants.PAYLOAD_PINNED_POSITION_CHAT -> {
                    val pinnedPosition = bundle.getLong(AppConstants.PAYLOAD_PINNED_POSITION_CHAT)

                }
                AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT -> {
                    val muteExpired = bundle.getLong(AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT)
                    val imageResource =
                        if (muteExpired - System.currentTimeMillis() <= 0) null else if (
                            (muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time)
                            R.drawable.ic_bell_off_light_grey_mini else R.drawable.ic_bell_sleep_light_grey_mini
                    var drawable: Drawable? = null
                    if (imageResource != null) drawable =
                        ContextCompat.getDrawable(binding.root.context, imageResource)
                    binding.tvName.setCompoundDrawablesWithIntrinsicBounds(
                        null, null, drawable, null
                    )
                    itemView.setOnLongClickListener {
                        val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
                        popup.inflate(R.menu.popup_menu_chat_list_item)

                        if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0) {
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
                                    listener.deleteChat(
                                        chatListDto.opponentNickname,
                                        chatListDto.id
                                    )
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

//                    binding.unreadMessagesWrapper.setCardBackgroundColor(
//                        if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0) ContextCompat.getColorStateList(
//                            binding.root.context,
//                            R.color.grey_400
//                        ) else ContextCompat.getColorStateList(
//                            binding.root.context,
//                            R.color.green_500
//                        )
//                    )
                }
                AppConstants.PAYLOAD_CHAT_DATE -> {
                    binding.tvTimestamp.text =
                        DateFormatter.dateFormat(chatListDto.lastMessageDate)
                }
                AppConstants.PAYLOAD_CHAT_MESSAGE_BODY -> {
                    val lastMessageBody = bundle.getString(AppConstants.PAYLOAD_CHAT_MESSAGE_BODY)
                    binding.tvLastMessage.text = lastMessageBody
                    binding.imMessageStatus.isVisible =
                        (lastMessageBody!!.isNotEmpty() && chatListDto.lastMessageIsOutgoing && chatListDto.draftMessage == null)
                }
                AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE -> {
                    val draftMessage = bundle.getString(AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE)
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
                        binding.tvLastMessage.text = spannable
                        binding.imMessageStatus.isVisible = false
                    } else {
                        binding.tvLastMessage.text = chatListDto.lastMessageBody
                        binding.tvTimestamp.text =
                            DateFormatter.dateFormat(chatListDto.lastMessageDate)
                        binding.imMessageStatus.isVisible =
                            (chatListDto.lastMessageBody.isNotEmpty() && chatListDto.lastMessageIsOutgoing)
                    }
                }
                AppConstants.PAYLOAD_CHAT_MESSAGE_STATE -> {
                    val messageState =
                        bundle.parcelable(AppConstants.PAYLOAD_CHAT_MESSAGE_STATE) as MessageSendingState?
                    if (messageState != null) setUpMessageSendingState(chatListDto, messageState)
                }
                AppConstants.PAYLOAD_CHAT_CUSTOM_NAME -> {
                    val name = bundle.getString(AppConstants.PAYLOAD_CHAT_CUSTOM_NAME)
                    binding.tvName.text =
                        if (name!!.isNotEmpty()) name else chatListDto.opponentNickname
                }
            }
        }
    }

}