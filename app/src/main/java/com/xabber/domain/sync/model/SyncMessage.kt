package com.xabber.domain.sync.model

import com.xabber.data_base.models.messages.MessageSendingState

data class SyncMessage(
    val id: String,
    val fromJid: String,
    val body: String,
    val timestampUs: Long,
    val isOutgoing: Boolean,
    val groupNickname: String?,
    // Pre-fetched from DB by ProcessSyncPageUseCase so DetermineMessageStateUseCase stays pure.
    val currentState: MessageSendingState? = null,
)
