package com.xabber.models.dto

import android.os.Parcelable
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatListDto(
    val id: String,
    val owner: String,
    val opponentJid: String,
    val opponentNickname: String,
    val customNickname: String = "",
    val lastMessageBody: String = "",
    val lastMessageDate: Long = 0,
    var lastMessageState: MessageSendingState = MessageSendingState.None,
    val isArchived: Boolean = false,
    val isSynced: Boolean = true,
    var draftMessage: String? = null,
    val hasAttachment: Boolean = false,    // вложения
    val isSystemMessage: Boolean = false,  // курсивом
    val isMentioned: Boolean = false,      // @ упомянули в чате
    var muteExpired: Long = 0,
    var pinnedDate: Long = 0,
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    var unread: String = "",
    val lastPosition: String = "",
    val drawableId: Int,
    val isHide: Boolean = false,
    val lastMessageIsOutgoing: Boolean = false,
    var colorKey: String = "blue"
) : Comparable<ChatListDto>, Parcelable {
    override fun compareTo(other: ChatListDto): Int {
        return if (other.pinnedDate > 0 || this.pinnedDate > 0) {
            other.pinnedDate.compareTo(this.pinnedDate)
        } else {
            other.lastMessageDate.compareTo(this.lastMessageDate)
        }
    }

    fun getChatName(): String =
        if (customNickname.isNotEmpty()) customNickname else if (opponentNickname.isNotEmpty()) opponentNickname else opponentJid

}
