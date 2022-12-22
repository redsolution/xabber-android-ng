package com.xabber.model.dto

import android.os.Parcelable
import androidx.annotation.ColorRes
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatListDto(
    val id: String,
    val owner: String,   // наш jid
    val opponentJid: String,   // jid оппонента
    val displayName: String, // как себя записал оппонент
    val customName: String,  // как мы записали оппонента
    val lastMessageBody: String = "",
    val lastMessageDate: Long = 0,
    val lastMessageState: MessageSendingState? = null,
    val isArchived: Boolean,
    val isSynced: Boolean,
    val draftMessage: String? = null, // черновик
    val hasAttachment: Boolean = false,  // вложения
    val isSystemMessage: Boolean = false, // курсивом
    val isMentioned: Boolean = false, // @ упомянули в чате
    var muteExpired: Long = 0,
    var pinnedDate: Long = 0,
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    val unread: String = "",
    val lastPosition: String = "",
    @ColorRes
    val colorId: Int,
    val drawableId: Int,
    val isHide: Boolean = false,
    val outgoing: Boolean = false,
) : Comparable<ChatListDto>, Parcelable {
    override fun compareTo(other: ChatListDto): Int {
        var result = 0
            if (other.pinnedDate > 0 || this.pinnedDate > 0) {
                result = other.pinnedDate.compareTo(this.pinnedDate)
            } else {
                result = other.lastMessageDate.compareTo(this.lastMessageDate)
            }
        return result
    }
}
