package com.xabber.presentation.application.fragments.chatlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemChatListBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.presentation.AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT
import com.xabber.presentation.AppConstants.PAYLOAD_PINNED_POSITION_CHAT
import com.xabber.presentation.AppConstants.PAYLOAD_UNREAD_CHAT

class ChatListAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatListDto, ChatListViewHolder>(DiffUtilCallback) {

    interface ChatListener {

        fun onClickItem(chatListDto: ChatListDto)

        fun pinChat(id: String)

        fun unPinChat(id: String)

        fun swipeItem(id: String)

        fun deleteChat(name: String, id: String)

        fun clearHistory(name: String, id: String)

        fun turnOfNotifications(id: String)

        fun enableNotifications(id: String)

        fun openSpecialNotificationsFragment()
    }

    lateinit var recyclerView: RecyclerView

    override fun onAttachedToRecyclerView(recycler: RecyclerView) {
        recyclerView = recycler
        ItemTouchHelper(SwipeToArchiveCallback(this)).attachToRecyclerView(recycler)

        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatListBinding.inflate(inflater, parent, false)
        return ChatListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    override fun onBindViewHolder(
        holder: ChatListViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bind(getItem(position), listener, payloads)
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
            if (oldItem.unreadString != newItem.unreadString) diffBundle.putString(
                PAYLOAD_UNREAD_CHAT,
                newItem.unreadString
            )
            if (oldItem.pinnedDate != newItem.pinnedDate) diffBundle.putLong(
                PAYLOAD_PINNED_POSITION_CHAT,
                newItem.pinnedDate
            )
            if (oldItem.muteExpired != newItem.muteExpired) diffBundle.putLong(
                PAYLOAD_MUTE_EXPIRED_CHAT,
                newItem.muteExpired
            )
            return diffBundle
        }
    }
}
