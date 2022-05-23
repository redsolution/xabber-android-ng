package com.xabber.data.dto

import androidx.annotation.ColorRes
import com.xabber.xmpp.messages.MessageSendingState

data class ChatListDto(
    val id: String,
    val owner: String,
    var jid: String,   // xmpp
    val username: String, // для групповых или обычных чатов (когда пересылаем сообщение)
    val lastMessageDate: Long,   // date of last message
    val lastMessageBody: String,
    val isSynced: Boolean,
    val isArchived: Boolean,
    val state: MessageSendingState,
    val isMuted: Boolean,
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    val unreadString: String?, //
    @ColorRes
    val colorId: Int,
    val isDrafted: Boolean, // черновик
    val hasAttachment: Boolean,  // вложения
    val userNickname: String?, // имя в чате
    val isSystemMessage: Boolean, // курсивом
    val isPinned: Boolean,   // закреплено

    val isMentioned: Boolean // @ упомянули в чате

) : Comparable<ChatListDto> {
    override fun compareTo(other: ChatListDto): Int {
        var result = other.isPinned.compareTo(this.isPinned)
        if (this.isPinned && other.isPinned) result = other.lastMessageDate.compareTo(this.lastMessageDate)
        if (!this.isPinned && !other.isPinned) result =
            other.lastMessageDate.compareTo(this.lastMessageDate)
        return result
    }
}