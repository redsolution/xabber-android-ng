package com.xabber.presentation.application.fragments.chatlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.xabber.databinding.HideChatItemBinding
import com.xabber.databinding.ItemChatListBinding
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_COLOR
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_CUSTOM_NAME
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DATE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_DRAFT_MESSAGE
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_BODY
import com.xabber.presentation.AppConstants.PAYLOAD_CHAT_MESSAGE_STATE
import com.xabber.presentation.AppConstants.PAYLOAD_MUTE_EXPIRED_CHAT
import com.xabber.presentation.AppConstants.PAYLOAD_PINNED_POSITION_CHAT
import com.xabber.presentation.AppConstants.PAYLOAD_UNREAD_CHAT
import com.xabber.utils.custom.SwipeToArchiveCallback

class ChatListAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatListDto, ViewHolder>(DiffUtilCallback) {
    lateinit var recyclerView: RecyclerView
    var isManyOwners = false

    interface ChatListener {

        fun onClickItem(chatListDto: ChatListDto)

        fun pinChat(id: String)

        fun unPinChat(id: String, position: Int)

        fun swipeItem(id: String)

        fun deleteChat(name: String, id: String)

        fun clearHistory(chatListDto: ChatListDto)

        fun turnOfNotifications(id: String)

        fun enableNotifications(id: String)

        fun openSpecialNotificationsFragment()
    }

    companion object {
        const val HIDE_CHAT = 0
        const val NORMAL_CHAT = 1
    }

    override fun onAttachedToRecyclerView(recycler: RecyclerView) {
        recyclerView = recycler
        ItemTouchHelper(SwipeToArchiveCallback(this)).attachToRecyclerView(recycler)
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            getItem(position).isHide -> HIDE_CHAT
            else -> NORMAL_CHAT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            NORMAL_CHAT -> {
                val binding = ItemChatListBinding.inflate(inflater, parent, false)
                ChatListViewHolder(binding)
            }
            HIDE_CHAT -> {
                val binding = HideChatItemBinding.inflate(inflater, parent, false)
                HideChatListViewHolder(binding)
            }
            else -> {
                throw IllegalStateException("Unsupported message view type!")
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder is ChatListViewHolder) {
            holder.getDivider().isVisible = isManyOwners
            holder.bind(getItem(position), listener)
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            if (holder is ChatListViewHolder)
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
            if (oldItem.unread != newItem.unread) diffBundle.putString(
                PAYLOAD_UNREAD_CHAT,
                newItem.unread
            )
            if (oldItem.pinnedDate != newItem.pinnedDate) diffBundle.putLong(
                PAYLOAD_PINNED_POSITION_CHAT,
                newItem.pinnedDate
            )
            if (oldItem.muteExpired != newItem.muteExpired) diffBundle.putLong(
                PAYLOAD_MUTE_EXPIRED_CHAT,
                newItem.muteExpired
            )
            if (oldItem.lastMessageDate != newItem.lastMessageDate) diffBundle.putLong(
                PAYLOAD_CHAT_DATE, newItem.lastMessageDate
            )
            if (oldItem.lastMessageBody != newItem.lastMessageBody) diffBundle.putString(
                PAYLOAD_CHAT_MESSAGE_BODY, newItem.lastMessageBody
            )
            if (oldItem.lastMessageState != newItem.lastMessageState) diffBundle.putParcelable(
                PAYLOAD_CHAT_MESSAGE_STATE, newItem.lastMessageState
            )
            if (oldItem.draftMessage != newItem.draftMessage) diffBundle.putString(
                PAYLOAD_CHAT_DRAFT_MESSAGE, newItem.draftMessage
            )
            if (oldItem.customNickname != newItem.customNickname)
                diffBundle.putString(
                    PAYLOAD_CHAT_CUSTOM_NAME, newItem.customNickname
                )
            if (oldItem.colorKey != newItem.colorKey)
                diffBundle.putString(
                    PAYLOAD_CHAT_COLOR, newItem.colorKey
                )
            return diffBundle
        }
    }

}
