package com.xabber.xmpp.XEP_0CCC

internal data class SyncChatMarkersSnapshot(
    val unread: Int,
    val displayedId: String?,
    val deliveredId: String?,
    val lastReadMessageDate: Long,
)

internal fun mergeSyncChatMarkers(
    current: SyncChatMarkersSnapshot,
    actualUnread: Int,
    incomingDisplayedId: String?,
    incomingDeliveredId: String?,
    unreadCount: Long,
    unreadAfterUs: Long?,
    messageDateMs: Long,
): SyncChatMarkersSnapshot {
    val unreadAfterMs = unreadAfterUs?.div(1000L) ?: 0L
    val mergedDisplayedId = maxMarkerId(current.displayedId, incomingDisplayedId)
    val mergedDeliveredId = maxMarkerId(current.deliveredId, incomingDeliveredId)
    val mergedLastReadMessageDate = maxOf(
        current.lastReadMessageDate,
        when {
            unreadAfterMs > 0L -> unreadAfterMs
            unreadCount == 0L -> messageDateMs
            else -> current.lastReadMessageDate
        }
    )

    return SyncChatMarkersSnapshot(
        unread = actualUnread,
        displayedId = mergedDisplayedId,
        deliveredId = mergedDeliveredId,
        lastReadMessageDate = mergedLastReadMessageDate,
    )
}

internal fun shouldRequestGapFill(
    previousMessageDateMs: Long,
    serverLastMessageDateMs: Long,
    isHistoryGapFixedForSession: Boolean,
    toleranceMs: Long = 1000L,
): Boolean {
    if (isHistoryGapFixedForSession || previousMessageDateMs <= 0L) {
        return false
    }
    return serverLastMessageDateMs > previousMessageDateMs + toleranceMs
}

private fun maxMarkerId(currentId: String?, incomingId: String?): String? {
    if (incomingId.isNullOrBlank()) {
        return currentId
    }
    if (currentId.isNullOrBlank()) {
        return incomingId
    }

    val currentNumeric = currentId.toLongOrNull()
    val incomingNumeric = incomingId.toLongOrNull()
    return when {
        currentNumeric != null && incomingNumeric != null -> {
            if (incomingNumeric > currentNumeric) incomingId else currentId
        }
        else -> incomingId
    }
}
