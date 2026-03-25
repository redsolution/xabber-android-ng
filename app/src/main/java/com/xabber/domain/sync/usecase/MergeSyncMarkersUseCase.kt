package com.xabber.domain.sync.usecase

import com.xabber.domain.sync.model.SyncMarkers

class MergeSyncMarkersUseCase {

    fun execute(
        current: SyncMarkers?,
        incoming: SyncMarkers,
        lastMsgTimestampUs: Long?,
    ): SyncMarkers {
        return incoming.copy(
            displayedId = maxMarkerId(current?.displayedId, incoming.displayedId),
            deliveredId = maxMarkerId(current?.deliveredId, incoming.deliveredId),
        )
    }

    fun shouldRequestGapFill(
        localLastMessageDateMs: Long,
        serverLastMessageDateMs: Long,
        isGapFixedForSession: Boolean,
        toleranceMs: Long = 1_000L,
    ): Boolean {
        if (isGapFixedForSession || localLastMessageDateMs <= 0L) return false
        return serverLastMessageDateMs > localLastMessageDateMs + toleranceMs
    }

    private fun maxMarkerId(current: String?, incoming: String?): String? {
        if (incoming.isNullOrBlank()) return current
        if (current.isNullOrBlank()) return incoming
        val cn = current.toLongOrNull()
        val inn = incoming.toLongOrNull()
        return when {
            cn != null && inn != null -> if (inn > cn) incoming else current
            else -> incoming
        }
    }
}
