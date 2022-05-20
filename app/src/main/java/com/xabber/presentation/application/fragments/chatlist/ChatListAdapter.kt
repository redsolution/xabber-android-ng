package com.xabber.presentation.application.fragments.chatlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.ChatListDto
import com.xabber.databinding.ItemChatBinding

class ChatListAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatListDto, ChatViewHolder>(DiffUtilCallback) {

    interface ChatListener {
        fun onClickItem(name: String)

        fun pinChat(id: String)

        //  fun swipeItem(id: Int)

        fun unPinChat(id: String)

        fun deleteChat(id: String)

        fun turnOfNotifications(id: String)

        fun openSpecialNotificationsFragment()

        fun onClickAvatar(name: String)

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatBinding.inflate(inflater, parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {


        holder.bind(getItem(position), listener)

    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<ChatListDto>() {

        override fun areItemsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem.jid == newItem.jid

        override fun areContentsTheSame(oldItem: ChatListDto, newItem: ChatListDto): Boolean =
            oldItem.username == newItem.username &&
                    oldItem.message == newItem.message &&
                    oldItem.date == newItem.date &&
                    oldItem.state == newItem.state &&
                    oldItem.isMuted == newItem.isMuted &&
                    oldItem.isSynced == newItem.isSynced &&
                    oldItem.status == newItem.status &&
                    oldItem.entity == newItem.entity &&
                    oldItem.unreadString == newItem.unreadString &&
                    oldItem.colorId == newItem.colorId &&
                    oldItem.isDrafted == newItem.isDrafted &&
                    oldItem.hasAttachment == newItem.hasAttachment &&
                    oldItem.userNickname == newItem.userNickname &&
                    oldItem.isSystemMessage == newItem.isSystemMessage &&
                    oldItem.isPinned == newItem.isPinned &&
                    oldItem.isArchived == newItem.isArchived
    }
}