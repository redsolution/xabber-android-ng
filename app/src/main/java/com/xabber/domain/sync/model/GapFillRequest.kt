package com.xabber.domain.sync.model

data class GapFillRequest(
    val jid: String,
    val conversationType: String,
    val localLastMessageDateMs: Long,
    val serverLastMessageDateMs: Long,
)
