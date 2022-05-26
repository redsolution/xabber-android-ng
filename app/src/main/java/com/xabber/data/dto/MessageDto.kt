package com.xabber.data.dto

import com.xabber.xmpp.messages.MessageDisplayType
import com.xabber.xmpp.messages.MessageSendingState

data class MessageDto(
    val primary: String,
    val isOutgoing: Boolean,
    val owner: String,
    val opponent: String,
    val messageBody: String,
    val messageSendingState: MessageSendingState,
    val sentTimestamp: Long,
    val editTimestamp: Long?,
    val displayType: MessageDisplayType?,
    val canEditMessage: Boolean?,
    val canDeleteMessage: Boolean?
)