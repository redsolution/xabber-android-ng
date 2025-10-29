package com.xabber.presentation.application.fragments.chat.message

import androidx.recyclerview.widget.DiffUtil
import com.xabber.dto.MessageDto

class MessageDiffCallback : DiffUtil.ItemCallback<MessageDto>() {

    override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean =
        oldItem.primary == newItem.primary

    override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto): Boolean =
        oldItem == newItem
}