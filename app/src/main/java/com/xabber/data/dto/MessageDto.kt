package com.xabber.data.dto

import com.xabber.data.xmpp.messages.MessageDisplayType
import com.xabber.data.xmpp.messages.MessageSendingState
import retrofit2.http.Url

data class MessageDto(
    val primary: String,
    val isOutgoing: Boolean,
    val owner: String,
    val opponent: String,
    val messageBody: String?,
    val messageSendingState: MessageSendingState,
    val sentTimestamp: Long,
    val editTimestamp: Long?,
    val displayType: MessageDisplayType?,
    val canEditMessage: Boolean?,
    val canDeleteMessage: Boolean?,
    val urlAvatar: Url?,
    val isGroup: Boolean
) : Comparable<MessageDto> {
    override fun compareTo(other: MessageDto): Int =
        other.sentTimestamp.compareTo(this.sentTimestamp)
}