package com.xabber.domain.sync.model

data class SyncConversation(
    val jid: String,
    val type: String,
    val stamp: String,
    val status: SyncStatus,
    val pinned: Long,
    val muteUntilMs: Long,   // -1 = not muted, Long.MAX_VALUE = forever
    val markers: SyncMarkers,
    val lastMessage: SyncMessage?,
)

enum class SyncStatus { ACTIVE, ARCHIVED, DELETED;
    companion object {
        fun from(raw: String) = when (raw) {
            "archived" -> ARCHIVED
            "deleted" -> DELETED
            else -> ACTIVE
        }
    }
}
