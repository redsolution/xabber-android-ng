package com.xabber.presentation.application.fragments.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.MessageDto
import com.xabber.databinding.ItemMessageBinding


class MessageAdapter(
    private val onAvatarClick: (MessageDto) -> Unit = {},
    private val onMessageClick: (MessageDto) -> Unit = {},
) : ListAdapter<MessageDto, MessageViewHolder>(DiffUtilCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when {
            true -> {
                MessageViewHolder(
                    ItemMessageBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
            else -> throw IllegalStateException("Unsupported message view type!")
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        return holder.bind(
            getItem(position),
            true,
            true
        )
    }
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