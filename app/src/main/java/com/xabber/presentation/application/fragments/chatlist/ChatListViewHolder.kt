package com.xabber.presentation.application.fragments.chatlist

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
import com.xabber.databinding.ItemChatListBinding
import com.xabber.models.dto.ChatListDto
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_CUSTOM_NAME
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DATE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_BODY
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_STATE
import com.xabber.presentation.AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT
import com.xabber.presentation.AppConstants.PAYLOAD_PINNED_POSITION_CHAT
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.dialogs.TimeMute
import com.xabber.utils.DateFormatter
import com.xabber.utils.parcelable


class ChatListViewHolder(
    private val binding: ItemChatListBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(chatListDto: ChatListDto, listener: ChatListAdapter.ChatListener) {

        setColorDivider(chatListDto.colorKey)
        setAvatar(chatListDto.drawableId)
        setName(chatListDto.getChatName())
        setTextMessage(chatListDto.draftMessage, chatListDto.lastMessageBody)
        setTime(chatListDto.lastMessageDate)
        setPin(chatListDto.pinnedDate, chatListDto.unread)
        setMuted(chatListDto)
        setUnreadMessages(chatListDto.unread, chatListDto.muteExpired)
        setMessageSendingState(chatListDto)
        setupChatStatus(chatListDto)

        binding.chatSyncImage.isVisible = chatListDto.isSynced

        itemView.setOnClickListener {
            listener.onClickItem(chatListDto)
        }

        itemView.setOnLongClickListener {
            setupAndShowPopupMenu(chatListDto, listener)
            true
        }
    }

    private fun setColorDivider(colorKey: String) {
        val colorDivider = ColorManager.convertColorNameToId(colorKey)
        binding.accountColorIndicator.setBackgroundColor(
            ContextCompat.getColor(
                binding.accountColorIndicator.context,
                colorDivider
            )
        )
    }

    private fun setAvatar(drawableId: Int) {
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(itemView).load(drawableId)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imChatListItemAvatar)
    }

    private fun setName(name: String) {
        binding.tvChatItemName.text = name
    }

    private fun setTextMessage(draftMessage: String?, lastMessageBody: String) {
        if (draftMessage != null) {
            val spannable =
                SpannableString("${binding.root.resources.getString(R.string.drafted)} $draftMessage")
            spannable.setSpan(
                ForegroundColorSpan(
                    itemView.resources.getColor(
                        R.color.red_500,
                        itemView.context.theme
                    )
                ),
                0,
                binding.root.resources.getString(R.string.drafted).length,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE
            )
            binding.tvChatListLastMessage.text = spannable
            binding.imMessageStatus.isVisible = false
        } else {
            binding.tvChatListLastMessage.text = HtmlCompat.fromHtml(
                lastMessageBody,
                HtmlCompat.FROM_HTML_MODE_COMPACT
            )
        }
    }

    private fun setTime(time: Long) {
        binding.tvTimestamp.text =
            DateFormatter.dateFormat(time)
    }

    private fun setupChatStatus(chatListDto: ChatListDto) {
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

        val tint = when (chatListDto.status) {
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
                ContextCompat.getColor(itemView.context, tint),
                PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun setPin(pinnedDate: Long, unread: String) {
        if (pinnedDate > 0) {
            binding.chatGround.setBackgroundResource(
                R.drawable.clickable_pinned_chat_background
            )
        } else {
            binding.chatGround.setBackgroundResource(R.drawable.clickable_view_group_background)
        }
        binding.imChatListPinned.isVisible =
            pinnedDate > 0 && unread.isEmpty()
    }

    private fun setUnreadMessages(unread: String, muteExpired: Long) {
        binding.unreadMessagesCount.isVisible = unread.isNotEmpty()
        if (unread.isNotEmpty()) binding.unreadMessagesCount.text =
            if (unread.toInt() < 1000) unread else "999+"
        binding.unreadMessagesCount.background =
            if ((muteExpired - System.currentTimeMillis()) > 0)
                ContextCompat.getDrawable(binding.root.context, R.drawable.circle_grey)
            else ContextCompat.getDrawable(binding.root.context, R.drawable.circle_green)
    }

    private fun setMuted(chatListDto: ChatListDto) {
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
    }

    private fun setMessageSendingState(
        chatListDto: ChatListDto
    ) {
        var image: Int? = null
        var tint: Int? = null
        binding.imMessageStatus.isVisible =
            chatListDto.lastMessageBody.isNotEmpty() && chatListDto.lastMessageIsOutgoing && chatListDto.draftMessage == null

        if (binding.imMessageStatus.isVisible) {
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
                }
            }
            if (tint != null && image != null) {
                binding.imMessageStatus.setImageResource(image)
                binding.imMessageStatus.setColorFilter(
                    ContextCompat.getColor(itemView.context, tint),
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
        if (chatListDto.pinnedDate > 0) {
            popup.menu.removeItem(R.id.pin_chat)
        } else {
            popup.menu.removeItem(R.id.unpin)
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
                    binding.unreadMessagesCount.isVisible = !unread.isNullOrEmpty()
                    if (chatListDto.unread.isNotEmpty()) binding.unreadMessagesCount.text =
                        if (unread!!.toInt() < 1000) unread else "999+"
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
                    binding.imChatListPinned.isVisible =
                        pinnedPosition > 0 && chatListDto.unread.isEmpty()
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

                            if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0) {
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
                }
                PAYLOAD_MUTE_EXPIRED_CHAT -> {
                    val muteExpired = bundle.getLong(PAYLOAD_MUTE_EXPIRED_CHAT)
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

                    binding.unreadMessagesCount.background =
                        if ((chatListDto.muteExpired - System.currentTimeMillis()) > 0)
                            ContextCompat.getDrawable(binding.root.context, R.drawable.circle_grey)
                        else
                            ContextCompat.getDrawable(binding.root.context, R.drawable.circle_green)
                }
                PAYLOAD_CHAT_DATE -> {
                    binding.tvTimestamp.text =
                        DateFormatter.dateFormat(chatListDto.lastMessageDate)
                }
                PAYLOAD_CHAT_MESSAGE_BODY -> {
                    val lastMessageBody = bundle.getString(PAYLOAD_CHAT_MESSAGE_BODY)
                    binding.tvChatListLastMessage.text = lastMessageBody
                    binding.imMessageStatus.isVisible =
                        (lastMessageBody!!.isNotEmpty() && chatListDto.lastMessageIsOutgoing)
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
                        binding.imMessageStatus.isVisible = false
                    } else {
                        binding.tvChatListLastMessage.text = chatListDto.lastMessageBody
                        binding.tvTimestamp.text =
                            DateFormatter.dateFormat(chatListDto.lastMessageDate)
                        binding.imMessageStatus.isVisible =
                            (chatListDto.lastMessageBody.isNotEmpty() && chatListDto.lastMessageIsOutgoing)
                    }
                }
                PAYLOAD_CHAT_MESSAGE_STATE -> {
                    val messageState =
                        bundle.parcelable(PAYLOAD_CHAT_MESSAGE_STATE) as MessageSendingState? ?: MessageSendingState.None
                    chatListDto.lastMessageState = messageState
                    setMessageSendingState(chatListDto)
                }
                PAYLOAD_CHAT_CUSTOM_NAME -> {
                    val name = bundle.getString(PAYLOAD_CHAT_CUSTOM_NAME)
                    binding.tvChatItemName.text =
                        if (name!!.isNotEmpty()) name else chatListDto.opponentNickname
                }
            }
        }
    }

}
