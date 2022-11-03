package com.xabber.presentation.application.fragments.chatlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.model.dto.ChatListDto
import com.xabber.model.dto.ContactDto
import com.xabber.databinding.ItemChatListBinding

class ChatListAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatListDto, ChatListViewHolder>(DiffUtilCallback) {

    interface ChatListener {
        fun onClickItem(chatListDto: ChatListDto)

        fun pinChat(id: String)

       fun swipeItem(id: Int)

        fun unPinChat(id: String)

        fun deleteChat(id: String)

        fun turnOfNotifications(id: String)

        fun enableNotifications(id: String)

        fun openSpecialNotificationsFragment()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatListBinding.inflate(inflater, parent, false)
        return ChatListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int) {


        holder.bind(getItem(position), listener)

    }

    fun onSwipeChatItem(position: Int) {
        listener.swipeItem(position)
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<ChatListDto>() {

        override fun areItemsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem == newItem

        override fun getChangePayload(oldItem: ChatListDto, newItem: ChatListDto): Any? {
            return super.getChangePayload(oldItem, newItem)
        }
    }
}
