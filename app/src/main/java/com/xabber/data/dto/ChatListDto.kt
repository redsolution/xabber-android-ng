package com.xabber.data.dto

import androidx.annotation.ColorRes
import com.xabber.xmpp.messages.MessageSendingState
import com.xabber.xmpp.presences.ResourceStatus
import com.xabber.xmpp.presences.RosterItemEntity

data class ChatListDto(
    val id: String,
    val owner: String,
    var jid: String,   // xmpp
    val displayName: String, // для групповых или обычных чатов (когда пересылаем сообщение)
    val lastMessageBody: String,
    val lastMessageDate: Long,
    val lastMessageState: MessageSendingState,
    val isArchived: Boolean,
    val isSynced: Boolean,
    val DraftMessage: String?, // черновик
    val hasAttachment: Boolean,  // вложения
    val isSystemMessage: Boolean, // курсивом
    val isMentioned: Boolean, // @ упомянули в чате
    val muteExpired: Double,
    val pinnedDate: Double,
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    val unreadString: String?,
 //   @ColorRes
 //   val colorId: Int,
  //  val userNickname: String?, // имя в чате
) : Comparable<ChatListDto> {
    override fun compareTo(other: ChatListDto): Int {
        var result = other.pinnedDate.compareTo(this.pinnedDate)
        if (!this.pinnedDate.equals(0) && !other.pinnedDate.equals(0)) result = other.lastMessageDate.compareTo(this.lastMessageDate)
        if (this.pinnedDate.equals(0) && other.pinnedDate.equals(0)) result =
            other.lastMessageDate.compareTo(this.lastMessageDate)
        return result
    }
}