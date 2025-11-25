package com.xabber.presentation.application.fragments.chatlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants

class ChatListAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatListDto, ChatListViewHolder>(DiffUtilCallback) {

    var selectedChatId: String? = null
        private set

    private object SelectionPayload

    interface ChatListener {
        fun onClickItem(chat: ChatListDto)
        fun pinChat(chatId: String)
        fun unPinChat(chatId: String, position: Int)
        fun swipeItem(chatId: String)
        fun deleteChat(name: String, chatId: String)
        fun clearHistory(name: String, chatId: String)
        fun turnOfNotifications(chatId: String)
        fun enableNotifications(chatId: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListViewHolder {
        val binding = ItemChatListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int) {
        val chat = getItem(position)
        holder.bind(chat, listener)
        updateBackground(holder, chat)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int, payloads: MutableList<Any>) {
        val chat = getItem(position)

        if (payloads.isEmpty()) {
            holder.bind(chat, listener)
        } else {
            val hasSelectionPayload = payloads.any { it === SelectionPayload || (it is Bundle && it.containsKey("selection_payload")) }
            if (hasSelectionPayload) {
                updateBackground(holder, chat)
            }

            val bundlePayloads = payloads.filterIsInstance<Bundle>()
            if (bundlePayloads.isNotEmpty()) {
                holder.bind(chat, listener, bundlePayloads)
            }
        }

        updateBackground(holder, chat)
    }


    fun setSelectedChatId(newId: String?) {
        if (selectedChatId == newId) return
        val oldId = selectedChatId
        selectedChatId = newId
        notifyItemChangedIfNeeded(oldId)
        notifyItemChangedIfNeeded(newId)
    }

    private fun notifyItemChangedIfNeeded(chatId: String?) {
        chatId ?: return
        val position = currentList.indexOfFirst { it.id == chatId }
        if (position != -1) {
            notifyItemChanged(position, SelectionPayload)
        }
    }
    private fun updateBackground(holder: ChatListViewHolder, chat: ChatListDto) {
        val isSelected = chat.id == selectedChatId
        val bgRes = if (isSelected) {
            R.color.grey_200
        } else if (chat.pinnedDate > 0) {
            R.drawable.clickable_pinned_chat_background
        } else {
            R.drawable.clickable_view_group_background
        }

        if (isSelected) {
            holder.binding.chatGround.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.grey_200)
            )
        } else {
            holder.binding.chatGround.setBackgroundResource(bgRes)
        }
    }

    object DiffUtilCallback : DiffUtil.ItemCallback<ChatListDto>() {
        override fun areItemsTheSame(old: ChatListDto, new: ChatListDto) = old.id == new.id
        override fun areContentsTheSame(old: ChatListDto, new: ChatListDto) = old == new

        override fun getChangePayload(old: ChatListDto, new: ChatListDto): Any? {
            val bundle = Bundle()
            if (old.unread != new.unread) bundle.putString(AppConstants.PAYLOAD_UNREAD_CHAT, new.unread)
            if (old.pinnedDate != new.pinnedDate) bundle.putLong(AppConstants.PAYLOAD_PINNED_POSITION_CHAT, new.pinnedDate)
            if (old.muteExpired != new.muteExpired) bundle.putLong(AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT, new.muteExpired)
            if (old.lastMessageDate != new.lastMessageDate) bundle.putLong(AppConstants.PAYLOAD_CHAT_DATE, new.lastMessageDate)
            if (old.lastMessageBody != new.lastMessageBody) bundle.putString(AppConstants.PAYLOAD_CHAT_MESSAGE_BODY, new.lastMessageBody)
            if (old.draftMessage != new.draftMessage) bundle.putString(AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE, new.draftMessage)
            if (old.customNickname != new.customNickname) bundle.putString(AppConstants.PAYLOAD_CHAT_CUSTOM_NAME, new.customNickname)
            if (old.colorKey != new.colorKey) bundle.putString(AppConstants.PAYLOAD_CHAT_COLOR, new.colorKey)
            return if (bundle.isEmpty) null else bundle
        }
    }
}