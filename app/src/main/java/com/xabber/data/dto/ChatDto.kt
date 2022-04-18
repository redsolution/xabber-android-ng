package com.xabber.data.dto

import androidx.annotation.ColorRes
import com.xabber.presentation.application.fragments.chat.MessageState
import com.xabber.presentation.application.fragments.chat.ResourceStatus
import com.xabber.presentation.application.fragments.chat.RosterItemEntity
import java.util.*

data class ChatDto(
    var jid: String,
    val owner: String,
    val username: String,
    val message: String,
    val date: Date,
    val state: MessageState,
    val isMuted: Boolean,
    val isSynced: Boolean,
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    val unread: Int,
    val unreadString: String?,
    @ColorRes
    val colorId: Int,
    val isDrafted: Boolean,
    val hasAttachment: Boolean,
    val userNickname: String?,
    val isSystemMessage: Boolean,
    val isPinned: Boolean,
)