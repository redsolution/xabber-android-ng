package com.xabber.data.dto

import androidx.annotation.ColorRes

data class ChatDto(
    val id: Int,
    var jid: String,   // xmpp
    val owner: String, //
    val username: String, // для групповых или обычных чатов (когда пересылаем сообщение)
    val message: String,   // last message
    val date: String,   // date of last message
    val state: MessageState,
    val isMuted: Boolean,
    val isSynced: Boolean, // маленькая серая точка
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
    val isArchived: Boolean, // заархивировано
    val isMentioned : Boolean // @ упомянули в чате

) : Comparable<ChatDto> {
    override fun compareTo(other: ChatDto): Int {
        var result = other.isPinned.compareTo(this.isPinned)
        if (this.isPinned && other.isPinned) result = other.date.compareTo(this.date)
        if (!this.isPinned && !other.isPinned) result = other.date.compareTo(this.date)
        return result
    }
}