package com.xabber.data.dto

import android.os.Parcelable
import androidx.annotation.ColorRes
import com.xabber.data.xmpp.messages.MessageSendingState
import com.xabber.data.xmpp.presences.ResourceStatus
import com.xabber.data.xmpp.presences.RosterItemEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatListDto(
    val id: String,
    val owner: String,
    val jid: String,   // xmpp
    val displayName: String, // для групповых или обычных чатов (когда пересылаем сообщение)
    val lastMessageBody: String? = null,
    val lastMessageDate: Long = 0,
    val lastMessageState: MessageSendingState? = null,
    val isArchived: Boolean,
    val isSynced: Boolean,
    val DraftMessage: String? = null, // черновик
    val hasAttachment: Boolean = false,  // вложения
    val isSystemMessage: Boolean = false, // курсивом
    val isMentioned: Boolean = false, // @ упомянули в чате
    val muteExpired: Double = 0.0,
    val pinnedDate: Double = 0.0,
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    val unreadString: String?,
    val lastPosition: Int = 0,
    @ColorRes
    val colorId: Int,
    val drawableId: Int,
    val contactDto: ContactDto
) : Comparable<ChatListDto>, Parcelable {
    override fun compareTo(other: ChatListDto): Int {
        var result = other.pinnedDate.compareTo(this.pinnedDate)
        if (this.pinnedDate == 0.0 && other.pinnedDate == 0.0) {
            result = other.lastMessageDate.compareTo(this.lastMessageDate)
        }; return result
    }
}