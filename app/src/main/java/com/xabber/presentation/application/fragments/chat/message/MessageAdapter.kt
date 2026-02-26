package com.xabber.presentation.application.fragments.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
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
    private val checkedItemIds: MutableSet<String> = HashSet()
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
                    message.outgoing -> VIEW_TYPE_OUTGOING_BASE + resolveContentType(message)
                    else -> VIEW_TYPE_INCOMING_BASE + resolveContentType(message)
                }
            }
            is ChatItem.DateHeaderItem -> VIEW_TYPE_DATE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when {
            viewType in VIEW_TYPE_OUTGOING_BASE..(VIEW_TYPE_OUTGOING_BASE + CONTENT_MAX) ->
                OutgoingMessageVH(
                    LayoutInflater.from(parent.context).inflate(R.layout.item_message_outgoing, parent, false),
                    layoutInflater, listener, onViewClickListener
                )
            viewType in VIEW_TYPE_INCOMING_BASE..(VIEW_TYPE_INCOMING_BASE + CONTENT_MAX) ->
                IncomingMessageVH(
                    LayoutInflater.from(parent.context).inflate(R.layout.item_message_incoming, parent, false),
                    layoutInflater, listener, onViewClickListener
                )
            viewType == VIEW_TYPE_SYSTEM_MESSAGE -> SystemMessageVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_system, parent, false),
                layoutInflater, listener, onViewClickListener
            )
            viewType == VIEW_TYPE_DATE_HEADER -> DateHeaderVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false)
            )
            else -> throw IllegalStateException("Unsupported view type: $viewType")
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is MessageViewHolder) {
            holder.onRecycled()
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
                    isNeedName = item.isNeedName,
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
        val currentMessage = getMessageAtPosition(position) ?: return true
        if (currentMessage.references.size > 0 && currentMessage.body.isEmpty()) return false

        // Find adjacent message (skip date headers, max 3 positions to avoid runaway loops)
        if (ChatSettingsManager.bottom) {
            for (nextPos in (position + 1)..minOf(position + 3, itemCount - 1)) {
                val nextMessage = getMessageAtPosition(nextPos) ?: continue
                return currentMessage.outgoing != nextMessage.outgoing
            }
        } else {
            for (prevPos in (position - 1) downTo maxOf(position - 3, 0)) {
                val prevMessage = getMessageAtPosition(prevPos) ?: continue
                return currentMessage.outgoing != prevMessage.outgoing
            }
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
        if (firstUnreadMessageID == null) return
        for (i in 0 until itemCount) {
            val item = getItem(i)
            if (item is ChatItem.MessageItem && item.message.primary == firstUnreadMessageID) {
                notifyItemChanged(i)
                break
            }
        }
    }

    // Метод для обратной совместимости (можно удалить позже)
    fun submitMessageList(messages: List<MessageStorageItem>) {
        submitList(messages.toChatItems(isGroup = isGroup))
    }

    companion object {
        const val VIEW_TYPE_DATE_HEADER = 0
        const val VIEW_TYPE_SYSTEM_MESSAGE = 1
        const val VIEW_TYPE_INCOMING_BASE = 10
        const val VIEW_TYPE_OUTGOING_BASE = 20

        // Content type offsets (must match MessageViewHolder content types)
        private const val CONTENT_TEXT = 1
        private const val CONTENT_IMAGE = 2
        private const val CONTENT_GEO = 3
        private const val CONTENT_VOICE = 4
        private const val CONTENT_FILES = 5
        private const val CONTENT_FILES_TEXT = 6
        private const val CONTENT_IMAGE_TEXT = 7
        private const val CONTENT_MAX = 7

        fun resolveContentType(message: MessageStorageItem): Int {
            if (message.references.isEmpty()) {
                return if (message.body.isNotEmpty()) CONTENT_TEXT else CONTENT_TEXT
            }
            val firstRef = message.references[0]
            if (firstRef.isGeo) return CONTENT_GEO
            if (firstRef.isAudioMessage) return CONTENT_VOICE

            var hasImages = false
            var hasOtherFiles = false
            for (ref in message.references) {
                if (ref.kind_ == "groupchat" || ref.kind_ == "system-message") continue
                val category = FileCategory.determineFileCategory(ref.mimeType ?: "")
                if (category == FileCategory.IMAGE || category == FileCategory.VIDEO) {
                    hasImages = true
                } else {
                    hasOtherFiles = true
                }
            }
            return when {
                hasImages && message.body.isNotEmpty() -> CONTENT_IMAGE_TEXT
                hasImages -> CONTENT_IMAGE
                hasOtherFiles && message.body.isNotEmpty() -> CONTENT_FILES_TEXT
                hasOtherFiles -> CONTENT_FILES
                message.body.isNotEmpty() -> CONTENT_TEXT
                else -> CONTENT_TEXT
            }
        }
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
                // Note: isRead is intentionally excluded to break the feedback loop:
                // scroll → onBind → markAsRead → Realm write → Flow re-emit → submitList → rebind
                // Read status doesn't change the visual appearance of the message bubble.
                oldMessage.body == newMessage.body &&
                        oldMessage.sentDate == newMessage.sentDate &&
                        oldMessage.editDate == newMessage.editDate &&
                        oldMessage.outgoing == newMessage.outgoing &&
                        oldMessage.state_ == newMessage.state_
            }
            oldItem is ChatItem.DateHeaderItem && newItem is ChatItem.DateHeaderItem -> {
                oldItem.date == newItem.date && oldItem.formattedDate == newItem.formattedDate
            }

            else -> false
        }
    }
}