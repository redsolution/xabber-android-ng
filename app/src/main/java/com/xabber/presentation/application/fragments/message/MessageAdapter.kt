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
        oldItem.messageId == newItem.messageId

    override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto) =
        oldItem.primary == newItem.primary &&
                oldItem.jid == newItem.jid &&
                oldItem.ownerJid == newItem.ownerJid &&
                oldItem.sender == newItem.sender &&
                oldItem.messageId == newItem.messageId &&
                oldItem.sentTimestamp == newItem.sentTimestamp &&
                oldItem.editTimestamp == newItem.editTimestamp &&
                oldItem.delayTimestamp == newItem.delayTimestamp &&
                oldItem.displayType == newItem.displayType &&
                oldItem.isWithAuthor == newItem.isWithAuthor &&
                oldItem.isWithAvatar == newItem.isWithAvatar &&
                oldItem.canPinMessage == newItem.canPinMessage &&
                oldItem.canEditMessage == newItem.canEditMessage &&
                oldItem.canDeleteMessage == newItem.canDeleteMessage &&
                oldItem.isOutgoing == newItem.isOutgoing &&
                oldItem.isEdited == newItem.isEdited &&
                oldItem.groupchatAuthorRole == newItem.groupchatAuthorRole &&
                oldItem.groupchatAuthorId == newItem.groupchatAuthorId &&
                oldItem.groupchatAuthorNickname == newItem.groupchatAuthorNickname &&
                oldItem.groupchatAuthorBadge == newItem.groupchatAuthorBadge &&
                oldItem.hasAttachedMessages == newItem.hasAttachedMessages &&
                oldItem.isDownloaded == newItem.isDownloaded &&
                oldItem.state == newItem.state &&
                oldItem.searchString == newItem.searchString &&
                oldItem.text == newItem.text
}