package com.xabber.presentation.application.fragments.chatlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.ChatListDto
import com.xabber.data.dto.ContactDto
import com.xabber.databinding.ItemChatListBinding

class ChatListAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatListDto, ChatListViewHolder>(DiffUtilCallback) {

    interface ChatListener {
        fun onClickItem(chatListDto: ChatListDto)

        fun pinChat(id: String)

        //  fun swipeItem(id: Int)

        fun unPinChat(id: String)

        fun deleteChat(id: String)

        fun turnOfNotifications(id: String)

        fun openSpecialNotificationsFragment()

        fun onClickAvatar(contactDto: ContactDto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatListBinding.inflate(inflater, parent, false)
        return ChatListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int) {


        holder.bind(getItem(position), listener)

    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<ChatListDto>() {

        override fun areItemsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem.jid == newItem.jid

        override fun areContentsTheSame(oldItem: ChatListDto, newItem: ChatListDto): Boolean =
            oldItem.displayName == newItem.displayName &&
                    oldItem.lastMessageDate == newItem.lastMessageDate &&
                    oldItem.lastMessageState == newItem.lastMessageState &&
                    oldItem.muteExpired == newItem.muteExpired &&
                    oldItem.isSynced == newItem.isSynced &&
                    oldItem.status == newItem.status &&
                    oldItem.entity == newItem.entity &&
                    oldItem.unreadString == newItem.unreadString &&
                    oldItem.hasAttachment == newItem.hasAttachment &&
                    oldItem.isSystemMessage == newItem.isSystemMessage &&
                    oldItem.pinnedDate == newItem.pinnedDate &&
                    oldItem.isArchived == newItem.isArchived
    }
}