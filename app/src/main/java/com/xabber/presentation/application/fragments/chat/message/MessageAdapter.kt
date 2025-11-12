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
    private val isGroup: Boolean,
    private val onBindListener: ((MessageDto?) -> Unit)? = null
) : ListAdapter<MessageDto, MessageViewHolder>(MessageDiffCallback()) {

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

    init {
        setHasStableIds(true)

    }

    override fun getItemId(position: Int): Long {
        return getItem(position).primary.hashCode().toLong()
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
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
        val message = getItem(position)
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
        val message = getItem(position)
        val previousMessage = getItemOrNull(position - 1) ?: return true
        return !message.sentTimestamp.isSameDayWith(previousMessage.sentTimestamp)
    }

    private fun isMessageNeedTail(position: Int): Boolean {
        if (ChatSettingsManager.bottom) {
            val message = getItem(position)
            val nextMessage = getItemOrNull(position + 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false
            else message.isOutgoing != nextMessage.isOutgoing
        } else {
            val message = getItem(position)
            val preMessage = getItemOrNull(position - 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false
            else message.isOutgoing != preMessage.isOutgoing
        }
    }

    private fun isMessageNeedName(position: Int): Boolean {
        if (!isGroup) return false
        val message = getItem(position)
        val preMessage = getItemOrNull(position - 1) ?: return true
        return message.isOutgoing != preMessage.isOutgoing || message.opponentJid != preMessage.opponentJid
    }

    private fun getItemOrNull(position: Int): MessageDto? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    fun getMessageItem(position: Int): MessageDto? =
        if (position in 0 until itemCount) getItem(position) else null

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
        notifyUnreadState()
    }

    private fun notifyUnreadState() {
        getCurrentList().forEachIndexed { index, message ->
            if (message.isUnread && (firstUnreadMessageID == null || message.primary == firstUnreadMessageID)) {
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

class MessageDiffCallback : DiffUtil.ItemCallback<MessageDto>() {
    override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean {
        return oldItem.primary == newItem.primary
    }

    override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean {
        return oldItem.messageBody == newItem.messageBody &&
                oldItem.sentTimestamp == newItem.sentTimestamp &&
                oldItem.editTimestamp == newItem.editTimestamp &&  // ← Добавлено
                oldItem.messageSendingState == newItem.messageSendingState &&  // ← Добавлено (state changes)
                oldItem.isOutgoing == newItem.isOutgoing &&
                oldItem.references == newItem.references &&
                oldItem.isUnread == newItem.isUnread &&
                oldItem.isChecked == newItem.isChecked &&
                oldItem.archivedId == newItem.archivedId &&
                oldItem.displayType == newItem.displayType  // ← Добавлено
    }
}