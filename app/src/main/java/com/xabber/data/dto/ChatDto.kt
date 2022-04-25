package com.xabber.data.dto

import androidx.annotation.ColorRes
import com.xabber.presentation.application.fragments.chat.MessageState
import com.xabber.presentation.application.fragments.chat.ResourceStatus
import com.xabber.presentation.application.fragments.chat.RosterItemEntity

import java.util.*

data class ChatDto(
    val id: Int,
    var jid: String,   // xmpp id
    val owner: String, //
    val username: String, // name/surname
    val message: String,   // last message
    val date: Date,   // date of last message
    val state: MessageState,
    val isMuted: Boolean,
    val isSynced: Boolean, //
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    val unread: Int,
    val unreadString: String?, //
    @ColorRes
    val colorId: Int,
    val isDrafted: Boolean, //
    val hasAttachment: Boolean,  // dkj;tybt
    val userNickname: String?,
    val isSystemMessage: Boolean, //
    val isPinned: Boolean,
    val isArchived: Boolean
) : Comparable<ChatDto> {
    override fun compareTo(other: ChatDto): Int {
        var result = other.isPinned.compareTo(this.isPinned)
        if (!this.isPinned && !other.isPinned) result = other.date.compareTo(this.date)
        return result
    }
}