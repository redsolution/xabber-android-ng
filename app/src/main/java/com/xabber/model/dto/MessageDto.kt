package com.xabber.model.dto

import com.xabber.model.xmpp.messages.MessageDisplayType
import com.xabber.model.xmpp.messages.MessageSendingState
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
    val isGroup: Boolean,
    val kind: MessageKind? = null,
    var isSelected: Boolean = false,
    val references: ArrayList<FileDto>? = null,
    val uries: ArrayList<String>? = null
) : Comparable<MessageDto> {
    override fun compareTo(other: MessageDto): Int =
        other.sentTimestamp.compareTo(this.sentTimestamp)
}