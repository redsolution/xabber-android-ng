package com.xabber.presentation.application.fragments.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.databinding.ItemChatListBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.presentation.application.fragments.chatlist.ChatListAdapter

class ChatListForForwardAdapter(private val listener: Listener) :
    ListAdapter<ChatListDto, ChatListForwardHolder>(ChatListForForwardAdapter.DiffUtilCallback) {

interface Listener {
    fun onClickItem(id: String)
}
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListForwardHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatListBinding.inflate(inflater, parent, false)
        return ChatListForwardHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListForwardHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<ChatListDto>() {

        override fun areItemsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatListDto, newItem: ChatListDto) =
            oldItem == newItem


    }
}