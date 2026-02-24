package com.xabber.presentation.application.fragments.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.message.*
import com.xabber.utils.isSameDayWith

class MessageAdapter(
    private val layoutInflater: LayoutInflater,
    private val listener: MenuItemListener? = null,
    private val onViewClickListener: OnViewClickListener? = null,
    private val isGroup: Boolean,
    private val onBindListener: ((MessageStorageItem?) -> Unit)? = null
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatItemDiffCallback()) {

    private var firstUnreadMessageID: String? = null
    private val checkedItemIds: MutableList<String> = ArrayList()
    private val TAG = "MessageAdapter"

    interface MenuItemListener {
        fun copyText(text: String)
        fun pinMessage(message: MessageStorageItem)
        fun forwardMessage(message: MessageStorageItem)
        fun replyMessage(message: MessageStorageItem)
        fun deleteMessage(primary: String)
        fun editMessage(primary: String, text: String)
    }

    interface OnViewClickListener {
        fun onLongClick(primary: String)
        fun checkItem(isChecked: Boolean, primary: String)
        fun onImageOrVideoClick(startPosition: Int, messageId: String)
        fun onLocationClick(latitude: Double, longitude: Double)
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.MessageItem -> {
                val message = item.message
                val isSystem = message.displayAs_ == "system" ||
                        message.conversationType_ == "https://xabber.com/protocol/groups#system-message"
                when {
                    isSystem -> VIEW_TYPE_SYSTEM_MESSAGE
                    message.outgoing -> VIEW_TYPE_OUTGOING_MESSAGE
                    else -> VIEW_TYPE_INCOMING_MESSAGE
                }
            }
            is ChatItem.DateHeaderItem -> VIEW_TYPE_DATE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_OUTGOING_MESSAGE -> OutgoingMessageVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_outgoing, parent, false),
                layoutInflater, listener, onViewClickListener
            )
            VIEW_TYPE_INCOMING_MESSAGE -> IncomingMessageVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_incoming, parent, false),
                layoutInflater, listener, onViewClickListener
            )
            VIEW_TYPE_SYSTEM_MESSAGE -> SystemMessageVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_system, parent, false),
                layoutInflater, listener, onViewClickListener
            )
            VIEW_TYPE_DATE_HEADER -> DateHeaderVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false)
            )
//            VIEW_TYPE_UNREAD_MARKER -> UnreadMarkerVH(
//                LayoutInflater.from(parent.context).inflate(R.layout.item_unread_marker, parent, false)
//            )
            else -> throw IllegalStateException("Unsupported view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageViewHolder -> {
                val item = getItem(position) as ChatItem.MessageItem
                holder.messageId = item.message.primary
                val extraData = MessageVhExtraData(
                    isUnread = !item.message.isRead && (firstUnreadMessageID == null || item.message.primary == firstUnreadMessageID),
                    isChecked = checkedItemIds.contains(item.message.primary),
                    isNeedTail = isMessageNeedTail(position),
                    isNeedDate = false, // Dates are separate items now
                    isNeedName = isMessageNeedName(position),
                    isGroup = isGroup
                )
                when (holder) {
                    is IncomingMessageVH -> holder.bind(item.message, extraData)
                    is OutgoingMessageVH -> holder.bind(item.message, extraData)
                    is SystemMessageVH -> holder.bind(item.message, extraData)
                }
                onBindListener?.invoke(item.message)
            }
            is DateHeaderVH -> {
                val item = getItem(position) as ChatItem.DateHeaderItem
                holder.bind(item.formattedDate)
            }
//            is UnreadMarkerVH -> {
//                val item = getItem(position) as ChatItem.UnreadMarkerItem
//                holder.bind(item.count)
//            }
        }
    }

    private fun isMessageNeedTail(position: Int): Boolean {
        // Нужно найти соседние сообщения, пропуская заголовки дат
        val currentMessage = getMessageAtPosition(position) ?: return true

        if (ChatSettingsManager.bottom) {
            // Ищем следующее сообщение
            var nextPos = position + 1
            while (nextPos < itemCount) {
                val nextMessage = getMessageAtPosition(nextPos)
                if (nextMessage != null) {
                    return if (currentMessage.references.size > 0 && currentMessage.body.isEmpty()) false
                    else currentMessage.outgoing != nextMessage.outgoing
                }
                nextPos++
            }
            return true
        } else {
            // Ищем предыдущее сообщение
            var prevPos = position - 1
            while (prevPos >= 0) {
                val prevMessage = getMessageAtPosition(prevPos)
                if (prevMessage != null) {
                    return if (currentMessage.references.size > 0 && currentMessage.body.isEmpty()) false
                    else currentMessage.outgoing != prevMessage.outgoing
                }
                prevPos--
            }
            return true
        }
    }

    private fun isMessageNeedName(position: Int): Boolean {
        if (!isGroup) return false

        val currentMessage = getMessageAtPosition(position) ?: return true
        // System messages don't need name
        if (currentMessage.displayAs_ == "system") return false

        // Ищем предыдущее сообщение
        var prevPos = position - 1
        while (prevPos >= 0) {
            val prevMessage = getMessageAtPosition(prevPos)
            if (prevMessage != null) {
                // In group chats, compare by author id/nickname to distinguish between members
                val currentAuthor = currentMessage.groupchatAuthorId ?: if (currentMessage.outgoing) "__self__" else ""
                val prevAuthor = prevMessage.groupchatAuthorId ?: if (prevMessage.outgoing) "__self__" else ""
                return currentAuthor != prevAuthor
            }
            prevPos--
        }
        return true
    }

    private fun getMessageAtPosition(position: Int): MessageStorageItem? {
        if (position !in 0 until itemCount) return null
        val item = getItem(position)
        return if (item is ChatItem.MessageItem) item.message else null
    }

    // Публичный метод для получения ChatItem по позиции
    fun getChatItem(position: Int): ChatItem? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    fun getMessageItem(position: Int): MessageStorageItem? {
        val item = getChatItem(position)
        return if (item is ChatItem.MessageItem) item.message else null
    }

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
        notifyUnreadState()
    }

    private fun notifyUnreadState() {
        for (i in 0 until itemCount) {
            val item = getItem(i)
            if (item is ChatItem.MessageItem) {
                if (!item.message.isRead && (firstUnreadMessageID == null || item.message.primary == firstUnreadMessageID)) {
                    notifyItemChanged(i)
                }
            }
        }
    }

    // Метод для обратной совместимости (можно удалить позже)
    fun submitMessageList(messages: List<MessageStorageItem>) {
        submitList(messages.toChatItems())
    }

    companion object {
        const val VIEW_TYPE_INCOMING_MESSAGE = 1
        const val VIEW_TYPE_OUTGOING_MESSAGE = 2
        const val VIEW_TYPE_SYSTEM_MESSAGE = 3
        const val VIEW_TYPE_DATE_HEADER = 4
    }
}

class ChatItemDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return when {
            oldItem is ChatItem.MessageItem && newItem is ChatItem.MessageItem -> {
                val oldMessage = oldItem.message
                val newMessage = newItem.message
                oldMessage.body == newMessage.body &&
                        oldMessage.sentDate == newMessage.sentDate &&
                        oldMessage.editDate == newMessage.editDate &&
                        oldMessage.outgoing == newMessage.outgoing &&
                        oldMessage.isRead == newMessage.isRead &&
                        oldMessage.state_ == newMessage.state_
            }
            oldItem is ChatItem.DateHeaderItem && newItem is ChatItem.DateHeaderItem -> {
                oldItem.date == newItem.date && oldItem.formattedDate == newItem.formattedDate
            }

            else -> false
        }
    }
}