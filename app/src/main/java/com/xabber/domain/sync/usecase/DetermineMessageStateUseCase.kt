package com.xabber.domain.sync.usecase

import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.domain.sync.model.MessageStateResult
import com.xabber.domain.sync.model.SyncMarkers
import com.xabber.domain.sync.model.SyncMessage

class DetermineMessageStateUseCase {

    fun execute(msg: SyncMessage, markers: SyncMarkers): MessageStateResult {
        return if (msg.isOutgoing) {
            val displayedUs = markers.displayedId?.toLongOrNull()
            val deliveredUs = markers.deliveredId?.toLongOrNull()
            val state = when {
                displayedUs != null && msg.timestampUs <= displayedUs -> MessageSendingState.Read
                deliveredUs != null && msg.timestampUs <= deliveredUs -> MessageSendingState.Deliver
                else -> {
                    val cur = msg.currentState
                    if (cur != null && cur.rawValue >= MessageSendingState.Sent.rawValue) cur
                    else MessageSendingState.Sent
                }
            }
            MessageStateResult(state, isRead = true)
        } else {
            val isRead = when {
                markers.unreadCount == 0L -> true
                markers.unreadAfterUs != null && markers.unreadAfterUs > 0L ->
                    msg.timestampUs <= markers.unreadAfterUs
                else -> false
            }
            val state = if (isRead) MessageSendingState.Read else MessageSendingState.Deliver
            MessageStateResult(state, isRead)
        }
    }
}
