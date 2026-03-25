package com.xabber.domain.sync.model

import com.xabber.data_base.models.messages.MessageSendingState

data class StoredConversation(
    val jid: String,
    val type: String,
    val markers: SyncMarkers,
    val lastMessageDateMs: Long,
    val isGapFixedForSession: Boolean,
    val lastMessageState: MessageSendingState?,
)
