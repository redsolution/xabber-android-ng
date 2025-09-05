package com.xabber.presentation.application.fragments.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.message.IncomingMessageVH
import com.xabber.presentation.application.fragments.chat.message.MessageViewHolder
import com.xabber.presentation.application.fragments.chat.message.OutgoingMessageVH
import com.xabber.presentation.application.fragments.chat.message.SystemMessageVH
import com.xabber.utils.isSameDayWith

class MessageAdapter(
    private val layoutInflater: LayoutInflater,
    private val listener: MenuItemListener? = null,
    private val onViewClickListener: OnViewClickListener? = null,
    val messages: ArrayList<MessageDto> = ArrayList(),
    private val isGroup: Boolean,
    private val onBindListener: ((MessageDto?) -> Unit)? = null
) : ListAdapter<MessageDto, MessageViewHolder>(DiffUtilCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getMessageItem(position)?.primary?.hashCode()?.toLong() ?: RecyclerView.NO_ID
    }

    private var firstUnreadMessageID: String? = null
    private val checkedItemIds: MutableList<String> = ArrayList()
    private val TAG = "MessageAdapter"

    interface MenuItemListener {
        fun copyText(text: String)
        fun pinMessage(messageDto: MessageDto)
        fun forwardMessage(messageDto: MessageDto)
        fun replyMessage(messageDto: MessageDto)
        fun deleteMessage(primary: String)
        fun editMessage(primary: String, text: String)
    }

    interface OnViewClickListener {
        fun onLongClick(primary: String)
        fun checkItem(isChecked: Boolean, primary: String)
        fun onImageOrVideoClick(startPosition: Int, messageId: String)
        fun onLocationClick(latitude: Double, longitude: Double)
    }

    override fun getItemCount(): Int = messages.size

    fun updateAdapter(messageDtoList: List<MessageDto>) {
        Log.v(TAG, "Updating adapter with ${messageDtoList.size} messages")
        val newList = messageDtoList.distinctBy { it.primary }.sortedBy { it.sentTimestamp }
        val oldList = messages.toList()
        messages.clear()
        messages.addAll(newList)
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition].primary == newList[newItemPosition].primary
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                // Be more permissive to ensure updates for state changes
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                val isSame = oldItem.messageBody == newItem.messageBody &&
                        oldItem.sentTimestamp == newItem.sentTimestamp &&
                        oldItem.isOutgoing == newItem.isOutgoing &&
                        oldItem.isUnread == newItem.isUnread &&
                        oldItem.messageSendingState == newItem.messageSendingState &&
                        oldItem.isSelected == newItem.isSelected &&
                        oldItem.isChecked == newItem.isChecked &&
                        oldItem.references == newItem.references &&
                        oldItem.archivedId == newItem.archivedId
                if (!isSame) {
                    Log.d(TAG, "Content changed for primary=${oldItem.primary}: oldBody=${oldItem.messageBody.take(50)}, newBody=${newItem.messageBody.take(50)}, oldUnread=${oldItem.isUnread}, newUnread=${newItem.isUnread}")
                }
                return isSame
            }
        })
        diffResult.dispatchUpdatesTo(this)
        notifyUnreadState()
        Log.d(TAG, "Adapter updated with ${messages.size} messages, first=${messages.firstOrNull()?.primary}, last=${messages.lastOrNull()?.primary}")
    }

    fun insertOlderMessages(newMessages: List<MessageDto>) {
        Log.v(TAG, "Inserting ${newMessages.size} older messages")
        val filteredMessages = newMessages.filter { m -> !messages.any { it.primary == m.primary } }
        if (filteredMessages.isEmpty()) {
            Log.d(TAG, "No new messages to insert")
            return
        }
        val oldSize = messages.size
        messages.addAll(0, filteredMessages) // Prepend older messages
        messages.sortBy { it.sentTimestamp } // Restored to sortBy
        submitList(messages.toList()) { notifyUnreadState() }
        Log.d(TAG, "Inserted ${filteredMessages.size} older messages, new list size: ${messages.size}, first=${messages.firstOrNull()?.primary}, last=${messages.lastOrNull()?.primary}")
    }

    override fun getItemViewType(position: Int): Int {
        val message = getMessageItem(position) ?: return INCOMING_MESSAGE
        return when {
            message.displayType == MessageDisplayType.System -> SYSTEM_MESSAGE
            message.isOutgoing -> OUTGOING_MESSAGE
            else -> INCOMING_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when (viewType) {
            OUTGOING_MESSAGE -> OutgoingMessageVH(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_outgoing, parent, false
                ), layoutInflater, listener, onViewClickListener
            )
            INCOMING_MESSAGE -> IncomingMessageVH(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_incoming, parent, false
                ), layoutInflater, listener, onViewClickListener
            )
            SYSTEM_MESSAGE -> SystemMessageVH(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_system, parent, false
                ), layoutInflater, listener, onViewClickListener
            )
            else -> throw IllegalStateException("Unsupported view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        Log.d(TAG, "Binding position $position of ${itemCount} (message primary: ${getMessageItem(position)?.primary})")
        val message = getMessageItem(position) ?: return
        Log.v(TAG, "Binding message: primary=${message.primary}, body=${message.messageBody.take(50)}, isOutgoing=${message.isOutgoing}, isUnread=${message.isUnread}, isChecked=${message.isChecked}")
        holder.setIsRecyclable(true)
        holder.messageId = message.primary
        val extraData = MessageVhExtraData(
            isUnread = message.isUnread && (firstUnreadMessageID == null || message.primary == firstUnreadMessageID),
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
        val message = getMessageItem(position) ?: return true
        val previousMessage = getMessageItem(position - 1) ?: return true
        return !message.sentTimestamp.isSameDayWith(previousMessage.sentTimestamp)
    }

    private fun isMessageNeedTail(position: Int): Boolean {
        if (ChatSettingsManager.bottom) {
            val message = getMessageItem(position) ?: return true
            val nextMessage = getMessageItem(position + 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false
            else message.isOutgoing != nextMessage.isOutgoing
        } else {
            val message = getMessageItem(position) ?: return true
            val preMessage = getMessageItem(position - 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false
            else message.isOutgoing != preMessage.isOutgoing
        }
    }

    private fun isMessageNeedName(position: Int): Boolean {
        if (!isGroup) return false
        val message = getMessageItem(position) ?: return false
        val preMessage = getMessageItem(position - 1) ?: return true
        return message.isOutgoing != preMessage.isOutgoing || message.opponentJid != preMessage.opponentJid
    }

    fun getMessageItem(position: Int): MessageDto? =
        if (position in 0 until messages.size) messages[position] else null

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
        notifyUnreadState()
    }

    fun updateCheckedItems(primary: String, isChecked: Boolean) {
        if (isChecked) {
            checkedItemIds.add(primary)
        } else {
            checkedItemIds.remove(primary)
        }
        val position = messages.indexOfFirst { it.primary == primary }
        if (position != -1) {
            messages[position] = messages[position].copy(isChecked = isChecked, isSelected = isChecked)
            notifyItemChanged(position)
        }
    }

    private fun notifyUnreadState() {
        messages.forEachIndexed { index, message ->
            if (message.isUnread && (firstUnreadMessageID == null || message.primary == firstUnreadMessageID)) {
                notifyItemChanged(index)
            }
        }
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<MessageDto>() {
        override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean {
            return oldItem.primary == newItem.primary
        }

        override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean {
            return oldItem == newItem &&
                    oldItem.isSelected == newItem.isSelected &&
                    oldItem.isChecked == newItem.isChecked &&
                    oldItem.isUnread == newItem.isUnread &&
                    oldItem.messageSendingState == newItem.messageSendingState
        }
    }

    companion object {
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
        const val SYSTEM_MESSAGE = 3
    }
}