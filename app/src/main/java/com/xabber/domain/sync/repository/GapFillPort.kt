package com.xabber.domain.sync.repository

import com.xabber.domain.sync.model.GapFillRequest

// Domain port implemented by MessageArchiveManager adapter in the protocol layer.
// Keeps FillGapsUseCase free of xmpp/ dependencies.
interface GapFillPort {
    suspend fun requestArchive(
        request: GapFillRequest,
        owner: String,
        onComplete: suspend () -> Unit,
    )
}
