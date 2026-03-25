package com.xabber.domain.sync.model

import com.xabber.data_base.models.messages.MessageSendingState

data class MessageStateResult(
    val state: MessageSendingState,
    val isRead: Boolean,
)
