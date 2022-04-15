package com.xabber.application.fragments.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.ChatDto
import com.xabber.databinding.ItemChatBinding

class ChatAdapter(
) : ListAdapter<ChatDto, ChatViewHolder>(DiffUtilCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatBinding.inflate(inflater, parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) =
        holder.bind(getItem(position))
}

private object DiffUtilCallback : DiffUtil.ItemCallback<ChatDto>() {

    override fun areItemsTheSame(oldItem: ChatDto, newItem: ChatDto) =
        oldItem.jid == newItem.jid

    override fun areContentsTheSame(oldItem: ChatDto, newItem: ChatDto): Boolean =
        oldItem.username == newItem.username &&
                oldItem.message == newItem.message &&
                oldItem.date == newItem.date &&
                oldItem.state == newItem.state &&
                oldItem.isMuted == newItem.isMuted &&
                oldItem.isSynced == newItem.isSynced &&
                oldItem.status == newItem.status &&
                oldItem.entity == newItem.entity &&
                oldItem.unread == newItem.unread &&
                oldItem.unreadString == newItem.unreadString &&
                oldItem.colorId == newItem.colorId &&
                oldItem.isDrafted == newItem.isDrafted &&
                oldItem.hasAttachment == newItem.hasAttachment &&
                oldItem.userNickname == newItem.userNickname &&
                oldItem.isSystemMessage == newItem.isSystemMessage &&
                oldItem.isPinned == newItem.isPinned
}