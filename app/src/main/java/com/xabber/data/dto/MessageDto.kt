package com.xabber.data.dto

data class MessageDto(
    val primary: String,
    val jid: String,
    val ownerJid: String,
    val sender: Sender?,
    var messageId: String,

    val sentTimestamp: Long,
    val editTimestamp: Long?,
    val delayTimestamp: Long?,

    val kind: MessageKind?,
    val isWithAuthor: Boolean?,
    val isWithAvatar: Boolean?,
    val canPinMessage: Boolean?,
    val canEditMessage: Boolean?,
    val canDeleteMessage: Boolean?,
    // TODO форварды
//    val forwards: List<MessageForwards>,
    val isEdited: Boolean?,
    val groupchatAuthorRole: String?,
    val groupchatAuthorId: String?,
    val groupchatAuthorNickname: String?,
    val groupchatAuthorBadge: String?,
    val hasAttachedMessages: Boolean?,
    val isDownloaded: Boolean?,
    val state: MessageState,
    val searchString: String?,

    val text: String?,
) {
    val isOutgoing: Boolean
        get() =
            if (sender != null)
                sender.jid == ownerJid
            else
                false
}