package com.xabber.presentation.application.fragments.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.message.IncomingMessageVH
import com.xabber.presentation.application.fragments.chat.message.MessageViewHolder
import com.xabber.presentation.application.fragments.chat.message.OutgoingMessageVH
import com.xabber.presentation.application.fragments.chat.message.SystemMessageVH
import com.xabber.utils.isSameDayWith

class MessageAdapter(
    private val layoutInflater: LayoutInflater,
    private val listener: MenuItemListener? = null,
    private val onViewClickListener: OnViewClickListener? = null,
    private val isGroup: Boolean,
    private val onBindListener: ((MessageStorageItem?) -> Unit)? = null
) : ListAdapter<MessageStorageItem, MessageViewHolder>(MessageDiffCallback()) {

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
        return getItem(position).primary.hashCode().toLong()
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        val isSystem = message.displayAs_ == "system" || message.conversationType_ == "https://xabber.com/protocol/groups#system-message"
        return when {
            isSystem -> SYSTEM_MESSAGE
            message.outgoing -> OUTGOING_MESSAGE
            else -> INCOMING_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when (viewType) {
            OUTGOING_MESSAGE -> OutgoingMessageVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_outgoing, parent, false),
                layoutInflater, listener, onViewClickListener
            )
            INCOMING_MESSAGE -> IncomingMessageVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_incoming, parent, false),
                layoutInflater, listener, onViewClickListener
            )
            SYSTEM_MESSAGE -> SystemMessageVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_system, parent, false),
                layoutInflater, listener, onViewClickListener
            )
            else -> throw IllegalStateException("Unsupported view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        Log.v(TAG, "Binding message: primary=${message.primary}, body=${message.body.take(50)}, isOutgoing=${message.outgoing}, isRead=${message.isRead}, isChecked=${checkedItemIds.contains(message.primary)}")
        holder.messageId = message.primary
        val extraData = MessageVhExtraData(
            isUnread = !message.isRead && (firstUnreadMessageID == null || message.primary == firstUnreadMessageID),
            isChecked = checkedItemIds.contains(message.primary),
            isNeedTail = isMessageNeedTail(position),
            isNeedDate = isMessageNeedDate(position),
            isNeedName = isMessageNeedName(position),
            isGroup = isGroup
        )
        when (holder) {
            is IncomingMessageVH -> holder.bind(message, extraData)
            is OutgoingMessageVH -> holder.bind(message, extraData)
            is SystemMessageVH -> holder.bind(message, extraData)
        }
        onBindListener?.invoke(message)
    }

    private fun isMessageNeedDate(position: Int): Boolean {
        val message = getItem(position)
        val previousMessage = getItemOrNull(position - 1) ?: return true
        return !message.sentDate.isSameDayWith(previousMessage.sentDate)
    }

    private fun isMessageNeedTail(position: Int): Boolean {
        if (ChatSettingsManager.bottom) {
            val message = getItem(position)
            val nextMessage = getItemOrNull(position + 1) ?: return true
            return if (message.references.size > 0 && message.body.isEmpty()) false
            else message.outgoing != nextMessage.outgoing
        } else {
            val message = getItem(position)
            val preMessage = getItemOrNull(position - 1) ?: return true
            return if (message.references.size > 0 && message.body.isEmpty()) false
            else message.outgoing != preMessage.outgoing
        }
    }

    private fun isMessageNeedName(position: Int): Boolean {
        if (!isGroup) return false
        val message = getItem(position)
        val preMessage = getItemOrNull(position - 1) ?: return true
        return message.outgoing != preMessage.outgoing || message.opponent != preMessage.opponent
    }

    private fun getItemOrNull(position: Int): MessageStorageItem? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    fun getMessageItem(position: Int): MessageStorageItem? =
        if (position in 0 until itemCount) getItem(position) else null

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
        notifyUnreadState()
    }

    private fun notifyUnreadState() {
        getCurrentList().forEachIndexed { index, message ->
            if (!message.isRead && (firstUnreadMessageID == null || message.primary == firstUnreadMessageID)) {
                notifyItemChanged(index)
            }
        }
    }

    companion object {
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
        const val SYSTEM_MESSAGE = 3
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<MessageStorageItem>() {
    override fun areItemsTheSame(oldItem: MessageStorageItem, newItem: MessageStorageItem): Boolean {
        return oldItem.primary == newItem.primary
    }

    override fun areContentsTheSame(oldItem: MessageStorageItem, newItem: MessageStorageItem): Boolean {
        if (oldItem.body != newItem.body ||
            oldItem.sentDate != newItem.sentDate ||
            oldItem.editDate != newItem.editDate ||
            oldItem.outgoing != newItem.outgoing ||
            oldItem.isRead != newItem.isRead ||
            oldItem.state_ != newItem.state_) return false

        if (oldItem.references.size != newItem.references.size) return false

        val oldRefs = oldItem.references.toList()
        val newRefs = newItem.references.toList()
        return oldRefs.zip(newRefs).all { (oldRef, newRef) ->
            oldRef.primary == newRef.primary &&
                    oldRef.uri == newRef.uri &&
                    oldRef.isAudioMessage == newRef.isAudioMessage &&
                    oldRef.mimeType == newRef.mimeType
            // Add further fields (e.g., fileSize, fileName) if they affect display
        }
    }
}