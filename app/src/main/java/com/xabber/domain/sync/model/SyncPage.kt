package com.xabber.domain.sync.model

const val SYNC_PAGE_SIZE = 60

data class SyncPage(
    val stamp: String,
    val conversations: List<SyncConversation>,
    val isPush: Boolean,
) {
    // If server returns exactly 60 on the final page, one extra empty request is sent.
    // It returns empty and terminates the loop. Matches current behaviour.
    val isFullPage get() = !isPush && conversations.size >= SYNC_PAGE_SIZE
    val lastStamp get() = conversations.lastOrNull()?.stamp ?: stamp
}
