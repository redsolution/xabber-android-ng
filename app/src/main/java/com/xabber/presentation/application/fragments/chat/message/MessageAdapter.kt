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
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.message.IncomingMessageVH
import com.xabber.presentation.application.fragments.chat.message.MessageViewHolder
import com.xabber.presentation.application.fragments.chat.message.OutgoingMessageVH
import com.xabber.presentation.application.fragments.chat.message.SystemMessageVH
import com.xabber.utils.isSameDayWith

class MessageAdapter(
    private val layoutInflater: LayoutInflater,
    private val listener: MenuItemListener? = null,
    private val onViewClickListener: OnViewClickListener? = null,
    private val messages: ArrayList<MessageDto>,
    private val isGroup: Boolean,
    private val onBindListener: ((MessageDto?) -> Unit)? = null // Added to call ChatFragment's onBind
) : ListAdapter<MessageDto, MessageViewHolder>(DiffUtilCallback) {

    private var firstUnreadMessageID: String? = null
    private val checkedItemIds: MutableList<String> = ArrayList()

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
        Log.d("MessageAdapter", "Updating adapter with ${messageDtoList.size} messages: ${messageDtoList.map { it.primary to it.messageBody.take(50) }}")
        messages.clear()
        messages.addAll(messageDtoList)
        submitList(messages.toList()) {
            Log.d("MessageAdapter", "Submitted new list to DiffUtil, notifying adapter")
            notifyDataSetChanged() // Ensure UI is refreshed
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            messages[position].displayType == MessageDisplayType.System -> SYSTEM_MESSAGE
            messages[position].isOutgoing -> OUTGOING_MESSAGE
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
            else -> throw IllegalStateException("Unsupported view type!")
        }
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

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getMessageItem(position) ?: return
        Log.d("MessageAdapter", "Binding message: primary=${message.primary}, body=${message.messageBody.take(50)}, isOutgoing=${message.isOutgoing}, isUnread=${message.isUnread}")
        holder.setIsRecyclable(false)
        holder.messageId = message.primary
        val extraData = MessageVhExtraData(
            isUnread = message.isUnread && message.primary == firstUnreadMessageID,
            isChecked = checkedItemIds.contains(message.primary),
            isNeedTail = isMessageNeedTail(position),
            isNeedDate = isMessageNeedDate(position),
            isNeedName = isMessageNeedName(position),
            isGroup = isGroup
        )
        when (getItemViewType(position)) {
            INCOMING_MESSAGE -> (holder as? IncomingMessageVH)?.bind(message, extraData)
            OUTGOING_MESSAGE -> (holder as? OutgoingMessageVH)?.bind(message, extraData)
            SYSTEM_MESSAGE -> (holder as? SystemMessageVH)?.bind(message, extraData)
        }
        // Call ChatFragment's onBind to mark message as read
        onBindListener?.invoke(message)
    }

    fun getMessageItem(position: Int): MessageDto? =
        when {
            position == RecyclerView.NO_POSITION -> null
            position < messages.size -> messages[position]
            else -> null
        }

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<MessageDto>() {
        override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto) =
            oldItem.primary == newItem.primary

        override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto) =
            oldItem == newItem
    }

    companion object {
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
        const val SYSTEM_MESSAGE = 3
    }
}