package com.xabber.domain.sync.model

data class SyncMarkers(
    val unreadCount: Long,
    val unreadAfterUs: Long?,
    val displayedId: String?,
    val deliveredId: String?,
)
