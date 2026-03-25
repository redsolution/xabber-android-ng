package com.xabber.domain.sync.model

sealed class ConversationWrite {
    data class Upsert(
        val conv: SyncConversation,
        val finalMarkers: SyncMarkers,  // merged result — the ONLY markers written to DB
                                        // conv.markers is raw server data; never write it directly
        val message: MessageUpdate?,
        val markersChanged: Boolean,     // false → applyBatch skips per-message state scan
        val createRosterIfMissing: Boolean,
    ) : ConversationWrite()

    data class MessageUpdate(val msg: SyncMessage, val state: MessageStateResult)

    data class Delete(val jid: String, val type: String) : ConversationWrite()
}
