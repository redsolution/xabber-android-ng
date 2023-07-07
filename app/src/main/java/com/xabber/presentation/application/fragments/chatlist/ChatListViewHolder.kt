package com.xabber.presentation.application.fragments.chatlist

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.databinding.ItemChatListBinding
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_COLOR
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_CUSTOM_NAME
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DATE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_BODY
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_STATE
import com.xabber.presentation.AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT
import com.xabber.presentation.AppConstants.PAYLOAD_PINNED_POSITION_CHAT
import com.xabber.presentation.application.dialogs.TimeMute
import com.xabber.presentation.application.fragments.chat.StatusMaker
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.util.dateFormat
import com.xabber.utils.MaskManager
import com.xabber.utils.parcelable
import java.util.*


class ChatListViewHolder(
    private val binding: ItemChatListBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun getDivider(): View = binding.accountColorIndicator

    fun bind(chatListDto: ChatListDto, listener: ChatListAdapter.ChatListener) {
        binding.cardview.radius = 0f             // скругленный уголок появится при сдвиге чата влево, в обычном состоянии 0
        setColorDivider(chatListDto.colorKey)   // цвет-индикатор аккаунта
        setAvatar(chatListDto.drawableId)      // здесь нужно будет скачивать аватарку с сервера, пока что просто картинка-заглушка
        setName(chatListDto.getChatName())    // имя собеседника. Групповые чаты пока не реализованы. В дальнейшем добавить если групповой чат - название группы
        setTextMessage(chatListDto.draftMessage, chatListDto.lastMessageBody)  // контент последнего сообщения или черновик
        setTime(chatListDto.lastMessageDate)     // время отправки последнего сообщения (если сообщений еще нет - время создания чата)
        setPin(chatListDto.pinnedDate)           // добавление фона и иконки для запиненных чатов
        setMuted(chatListDto.muteExpired)        // иконка замьюченных чатов
        setUnreadMessages(chatListDto.unread, chatListDto.muteExpired, chatListDto.lastMessageIsOutgoing)  // показ бейджа непрочитанных сообщений и установление его цвета: зеленый - уведомления включены, серый - выключены
        setMessageSendingState(chatListDto)    // статус доставки сообщения
        setupChatStatus(chatListDto)           // статус чата

        binding.chatSyncImage.isVisible = chatListDto.isSynced

        itemView.setOnClickListener {
            listener.onClickItem(chatListDto)   // открытие чата
        }

        itemView.setOnLongClickListener {
            setupAndShowPopupMenu(chatListDto.id, chatListDto.muteExpired, chatListDto.pinnedDate, chatListDto.getChatName(), listener)   // показ меню
            true
        }
    }

    private fun setColorDivider(colorKey: String) {
        val color = ColorManager.convertColorNameToId(colorKey)
        binding.accountColorIndicator.setBackgroundResource(color)
    }

    private fun setAvatar(drawableId: Int) {
        binding.shapeView.setDrawable(MaskManager.mask)
        Glide.with(itemView).load(drawableId).into(binding.imChatListItemAvatar)
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
            Date().dateFormat(time)
    }

    private fun setupChatStatus(chatListDto: ChatListDto) {
        val icon = StatusMaker.statusIcon(chatListDto.entity)
        val tint = StatusMaker.statusTint(chatListDto.status)

        if (icon != null) {
            binding.imChatStatus.isVisible = true
            binding.imChatStatus.setImageResource(icon)
            binding.imChatStatus.setColorFilter(
                ContextCompat.getColor(itemView.context, tint),
                PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun setPin(pinnedDate: Long) {
        if (pinnedDate > 0) {
            binding.chatGround.setBackgroundResource(
                R.drawable.clickable_pinned_chat_background
            )
        } else {
            binding.chatGround.setBackgroundResource(R.drawable.clickable_view_group_background)
        }
        binding.imChatListPinned.isVisible =
            pinnedDate > 0
    }

    private fun setUnreadMessages(
        unread: String,
        muteExpired: Long,
        lastMessageIsOutgoing: Boolean
    ) {
        binding.unreadMessagesCount.isVisible = unread.isNotEmpty()
        binding.imMessageStatus.isVisible = unread.isEmpty() && lastMessageIsOutgoing
        if (unread.isNotEmpty()) binding.unreadMessagesCount.text =
            if (unread.toInt() < 1000) unread else binding.root.context.resources.getString(R.string.over_unread_messages)
        val colorBackground =
            ContextCompat.getColor(
                binding.root.context,
                if ((muteExpired - System.currentTimeMillis()) > 0) R.color.grey_300 else R.color.green_500
            )
        val colorFilter = PorterDuffColorFilter(
            colorBackground,
            PorterDuff.Mode.SRC_IN
        )
        binding.unreadMessagesCount.background.colorFilter = colorFilter
    }

    private fun setMuted(muteExpired: Long) {
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
        binding.imMessageStatus.isVisible =
            chatListDto.lastMessageBody.isNotEmpty() && chatListDto.lastMessageIsOutgoing && chatListDto.draftMessage == null && chatListDto.unread.isEmpty()

        if (binding.imMessageStatus.isVisible) {
            val iconAndTint = StatusMaker.deliverMessageStatusIcon(chatListDto.lastMessageState)
            val icon = iconAndTint.first
            val tint = iconAndTint.second
            if (icon != null && tint != null) {
                binding.imMessageStatus.setImageResource(icon)
                binding.imMessageStatus.setColorFilter(
                    ContextCompat.getColor(binding.root.context, tint),
                    PorterDuff.Mode.SRC_IN
                )
            }
        }
    }

    private fun setupAndShowPopupMenu(
        chatId: String, muteExpired: Long, pinnedDate: Long, chatName: String,
        listener: ChatListAdapter.ChatListener
    ) {
        val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
        popup.inflate(R.menu.popup_menu_chat_list_item)
        if ((muteExpired - System.currentTimeMillis()) > 0) {
            popup.menu.removeItem(R.id.turn_of_notifications)
        } else {
            popup.menu.removeItem(R.id.enable_notifications)
        }
        if (pinnedDate > 0) {
            popup.menu.removeItem(R.id.pin_chat)
        } else {
            popup.menu.removeItem(R.id.unpin)
        }

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.unpin -> listener.unPinChat(chatId, absoluteAdapterPosition)
                R.id.pin_chat -> {
                    listener.pinChat(chatId)
                }
                R.id.turn_of_notifications -> {
                    listener.turnOfNotifications(chatId)
                }
                R.id.enable_notifications -> {
                    listener.enableNotifications(chatId)
                }
                R.id.customise_notifications -> {
                    listener.openSpecialNotificationsFragment()
                }
                R.id.delete -> {
                    listener.deleteChat(chatName, chatId)
                }
                R.id.clear_history -> {
                    listener.clearHistory(chatName, chatId)
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
                    if (unread != null) setUnreadMessages(
                        unread,
                        chatListDto.muteExpired,
                        chatListDto.lastMessageIsOutgoing
                    )
                }
                PAYLOAD_PINNED_POSITION_CHAT -> {
                    val pinnedDate = bundle.getLong(PAYLOAD_PINNED_POSITION_CHAT)
                    setPin(pinnedDate)
                    itemView.setOnLongClickListener {
                        setupAndShowPopupMenu(
                            chatListDto.id,
                            chatListDto.muteExpired,
                            pinnedDate,
                            chatListDto.getChatName(),
                            listener
                        )
                        true
                    }
                }
                PAYLOAD_MUTE_EXPIRED_CHAT -> {
                    val muteExpired = bundle.getLong(PAYLOAD_MUTE_EXPIRED_CHAT)
                    setMuted(muteExpired)
                    itemView.setOnLongClickListener {
                      setupAndShowPopupMenu(chatListDto.id, muteExpired, chatListDto.pinnedDate, chatListDto.getChatName(), listener)
                        true
                    }
                    setUnreadMessages(
                        chatListDto.unread,
                        muteExpired,
                        chatListDto.lastMessageIsOutgoing
                    )
                }
                PAYLOAD_CHAT_DATE -> {
                    binding.tvTimestamp.text =
                        Date().dateFormat(chatListDto.lastMessageDate)
                }
                PAYLOAD_CHAT_MESSAGE_BODY -> {
                    val lastMessageBody = bundle.getString(PAYLOAD_CHAT_MESSAGE_BODY)
                    binding.tvChatListLastMessage.text = lastMessageBody
                    binding.imMessageStatus.isVisible =
                        (lastMessageBody!!.isNotEmpty() && chatListDto.lastMessageIsOutgoing)
                }
                PAYLOAD_CHAT_DRAFT_MESSAGE -> {
                    val draftMessage = bundle.getString(PAYLOAD_CHAT_DRAFT_MESSAGE)
                    setTextMessage(draftMessage, chatListDto.lastMessageBody)
                }
                PAYLOAD_CHAT_MESSAGE_STATE -> {
                    val messageState =
                        bundle.parcelable(PAYLOAD_CHAT_MESSAGE_STATE) as MessageSendingState?
                            ?: MessageSendingState.None
                    chatListDto.lastMessageState = messageState
                    setMessageSendingState(chatListDto)
                }
                PAYLOAD_CHAT_CUSTOM_NAME -> {
                    val name = bundle.getString(PAYLOAD_CHAT_CUSTOM_NAME)
                    if (name != null) setName(name)
                }
                PAYLOAD_CHAT_COLOR -> {
                    val color = bundle.getString(PAYLOAD_CHAT_COLOR)
                    if (color != null) {
                        setColorDivider(color)
                    }
                }
            }
        }
    }

}
