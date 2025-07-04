package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.utils.custom.SwipeToArchiveCallback

class ChatListAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatListDto, ChatListViewHolder>(DiffUtilCallback) {
    lateinit var recyclerView: RecyclerView
    var isManyOwners = false
    private var selectedChatId: String? = null

    interface ChatListener {
        fun onClickItem(chatListDto: ChatListDto)
        fun pinChat(chatId: String)
        fun unPinChat(chatId: String, position: Int)
        fun swipeItem(chatId: String)
        fun deleteChat(chatName: String, chatId: String)
        fun clearHistory(chatName: String, chatId: String)
        fun turnOfNotifications(chatId: String)
        fun enableNotifications(chatId: String)
    }

    companion object {
        const val NORMAL_CHAT = 1
        const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }

    fun setSelectedChatId(newSelectedChatId: String?) {
        val oldSelectedChatId = selectedChatId
        selectedChatId = newSelectedChatId

        val previousAnimator = recyclerView.itemAnimator
        recyclerView.itemAnimator = null

        if (oldSelectedChatId != null) {
            val oldPosition = currentList.indexOfFirst { it.id == oldSelectedChatId }
            if (oldPosition != -1) notifyItemChanged(oldPosition, PAYLOAD_SELECTION)
        }
        if (newSelectedChatId != null) {
            val newPosition = currentList.indexOfFirst { it.id == newSelectedChatId }
            if (newPosition != -1) notifyItemChanged(newPosition, PAYLOAD_SELECTION)
        }

        recyclerView.itemAnimator = previousAnimator
    }

    override fun onAttachedToRecyclerView(recycler: RecyclerView) {
        this.recyclerView = recycler
        ItemTouchHelper(SwipeToArchiveCallback(this)).attachToRecyclerView(recycler)
        super.onAttachedToRecyclerView(recycler)
    }

    override fun getItemViewType(position: Int): Int {
        return NORMAL_CHAT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatListBinding.inflate(inflater, parent, false)
        return ChatListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int) {
        val chat = getItem(position)
        holder.bind(chat, listener)
        updateBackground(holder, chat)
    }

    override fun onBindViewHolder(
        holder: ChatListViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val chat = getItem(position)
        if (payloads.isEmpty()) {
            holder.bind(chat, listener)
            updateBackground(holder, chat)
        } else if (payloads.contains(PAYLOAD_SELECTION)) {
            updateBackground(holder, chat)
        } else {
            holder.bind(chat, listener, payloads)
            updateBackground(holder, chat)
        }
    }

    private fun updateBackground(holder: ChatListViewHolder, chat: ChatListDto) {
        if (chat.id == selectedChatId) {
            holder.binding.chatGround.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.grey_200)
            )
        } else {
            holder.binding.chatGround.setBackgroundResource(
                if (chat.pinnedDate > 0) R.drawable.clickable_pinned_chat_background
                else R.drawable.clickable_view_group_background
            )
        }
    }

    fun onSwipeChatItem(position: Int) {
        listener.swipeItem(currentList[position].id)
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<ChatListDto>() {
        override fun areItemsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem == newItem

        override fun getChangePayload(oldItem: ChatListDto, newItem: ChatListDto): Any {
            val diffBundle = Bundle()
            if (oldItem.unread != newItem.unread) diffBundle.putString(
                AppConstants.PAYLOAD_UNREAD_CHAT,
                newItem.unread
            )
            if (oldItem.pinnedDate != newItem.pinnedDate) diffBundle.putLong(
                AppConstants.PAYLOAD_PINNED_POSITION_CHAT,
                newItem.pinnedDate
            )
            if (oldItem.muteExpired != newItem.muteExpired) diffBundle.putLong(
                AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT,
                newItem.muteExpired
            )
            if (oldItem.lastMessageDate != newItem.lastMessageDate) diffBundle.putLong(
                AppConstants.PAYLOAD_CHAT_DATE, newItem.lastMessageDate
            )
            if (oldItem.lastMessageBody != newItem.lastMessageBody) diffBundle.putString(
                AppConstants.PAYLOAD_CHAT_MESSAGE_BODY, newItem.lastMessageBody
            )
            if (oldItem.lastMessageState != newItem.lastMessageState) diffBundle.putParcelable(
                AppConstants.PAYLOAD_CHAT_MESSAGE_STATE, newItem.lastMessageState
            )
            if (oldItem.draftMessage != newItem.draftMessage) diffBundle.putString(
                AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE, newItem.draftMessage
            )
            if (oldItem.customNickname != newItem.customNickname) diffBundle.putString(
                AppConstants.PAYLOAD_CHAT_CUSTOM_NAME, newItem.customNickname
            )
            if (oldItem.colorKey != newItem.colorKey) diffBundle.putString(
                AppConstants.PAYLOAD_CHAT_COLOR, newItem.colorKey
            )
            return diffBundle
        }
    }
}