package com.xabber.dto

import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.presentation.application.fragments.chat.geo.Location
import retrofit2.http.Url

data class MessageDto(
    val primary: String,
    val isOutgoing: Boolean,
    val owner: String,
    val opponentJid: String,
    val messageBody: String,
    val messageSendingState: MessageSendingState,
    val sentTimestamp: Long,
    val editTimestamp: Long = 0L,
    val displayType: MessageDisplayType? = null,
    val canEditMessage: Boolean,
    val canDeleteMessage: Boolean?,
    val urlAvatar: Url? = null,
    val isGroup: Boolean,
    val kind: MessageKind? = null,
    var isSelected: Boolean = false,
    var references: ArrayList<MessageReferenceDto> = ArrayList(),
    val isUnread: Boolean = true,
    var isChecked: Boolean = false
) : Comparable<MessageDto> {
    override fun compareTo(other: MessageDto): Int =
        this.sentTimestamp.compareTo(other.sentTimestamp)
}
