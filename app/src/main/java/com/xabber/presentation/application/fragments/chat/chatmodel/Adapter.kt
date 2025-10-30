package com.xabber.presentation.application.fragments.chat.chatmodel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.chatmodel.ChatFragmentModel
import com.xabber.presentation.application.fragments.chat.message.IncomingMessageVH
import com.xabber.presentation.application.fragments.chat.message.MessageViewHolder
import com.xabber.presentation.application.fragments.chat.message.OutgoingMessageVH
import com.xabber.presentation.application.fragments.chat.message.SystemMessageVH
import com.xabber.utils.isSameDayWith

class MessageAdapter(
    private val inflater: LayoutInflater,
    private val menuItemListener: ChatFragmentModel.MenuItemListener?,
    private val onViewClickListener: ChatFragmentModel.OnViewClickListener?,
    private val uiMapper: MessageUiMapper
) : ListAdapter<MessageDto, MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when (viewType) {
            INCOMING -> IncomingMessageVH(
                inflater.inflate(R.layout.item_message_incoming, parent, false),
                inflater, menuItemListener, onViewClickListener
            )
            OUTGOING -> OutgoingMessageVH(
                inflater.inflate(R.layout.item_message_outgoing, parent, false),
                inflater, menuItemListener, onViewClickListener
            )
            SYSTEM -> SystemMessageVH(
                inflater.inflate(R.layout.item_message_system, parent, false),
                inflater, menuItemListener, onViewClickListener
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val extraData = MessageVhExtraData(
            isUnread = message.isUnread,
            isChecked = message.isChecked,
            isNeedTail = uiMapper.isNeedTail(position, currentList),
            isNeedDate = uiMapper.isNeedDate(position, currentList),
            isNeedName = uiMapper.isNeedName(position, currentList),
            isGroup = message.isGroup
        )
        uiMapper.bind(holder, message, extraData)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            getItem(position).displayType == com.xabber.data_base.models.messages.MessageDisplayType.System -> SYSTEM
            getItem(position).isOutgoing -> OUTGOING
            else -> INCOMING
        }
    }

    companion object {
        const val INCOMING = 1
        const val OUTGOING = 2
        const val SYSTEM = 3
    }
}


class MessageUiMapper {

    fun isNeedDate(position: Int, messages: List<MessageDto>): Boolean {
        if (position == 0) return true
        val current = messages[position].sentTimestamp
        val previous = messages[position - 1].sentTimestamp
        return !current.isSameDayWith(previous)
    }

    fun isNeedTail(position: Int, messages: List<MessageDto>): Boolean {
        if (messages.isEmpty()) return true
        val message = messages[position]
        if (message.references.isNotEmpty() && message.messageBody.isEmpty()) return false
        return if (ChatSettingsManager.bottom) {
            position == messages.size - 1 || message.isOutgoing != messages[position + 1].isOutgoing
        } else {
            position == 0 || message.isOutgoing != messages[position - 1].isOutgoing
        }
    }

    fun isNeedName(position: Int, messages: List<MessageDto>): Boolean {
        if (!messages.getOrNull(position)?.isGroup!!) return false
        if (position == 0) return true
        val current = messages[position]
        val previous = messages[position - 1]
        return current.isOutgoing != previous.isOutgoing || current.opponentJid != previous.opponentJid
    }

    fun bind(holder: MessageViewHolder, message: MessageDto, extraData: MessageVhExtraData) {
        holder.bind(message, extraData)
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<MessageDto>() {
    override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean =
        oldItem.primary == newItem.primary

    override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean =
        oldItem == newItem
}