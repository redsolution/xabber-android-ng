package com.xabber.presentation.application.fragments.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.R
import com.xabber.data.dto.MessageDto


class MessageAdapter(
    private val onAvatarClick: (MessageDto) -> Unit = {},
    private val onMessageClick: (MessageDto) -> Unit = {},
) : ListAdapter<MessageDto, MessageViewHolder>(DiffUtilCallback) {

    companion object {
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view: View
        return when (viewType) {
            OUTGOING_MESSAGE -> {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_outgoing, parent, false)
                MessageViewHolder(view)
            }
            else -> {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_incoming, parent, false)
                MessageViewHolder(view)

            }
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        var a = ""
        if (getItem(position+1) != null)  a = getItem(position+1).owner
        return holder.bind(getItem(position), a)
    }


    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isOutgoing) OUTGOING_MESSAGE
        else INCOMING_MESSAGE
    }


    private object DiffUtilCallback : DiffUtil.ItemCallback<MessageDto>() {

        override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto) =
            oldItem.primary == newItem.primary

        override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto) =
            oldItem.primary == newItem.primary &&
                    oldItem.isOutgoing == newItem.isOutgoing &&
                    oldItem.owner == newItem.owner &&
                    oldItem.opponent == newItem.opponent &&
                    oldItem.messageBody == newItem.messageBody &&
                    oldItem.messageSendingState == newItem.messageSendingState &&
                    oldItem.sentTimestamp == newItem.sentTimestamp &&
                    oldItem.editTimestamp == newItem.editTimestamp &&
                    oldItem.displayType == newItem.displayType &&
                    oldItem.canEditMessage == newItem.canEditMessage &&
                    oldItem.canDeleteMessage == newItem.canDeleteMessage
    }
}