package com.xabber.domain.sync.usecase

import com.xabber.domain.sync.model.*
import com.xabber.domain.sync.repository.SyncRepository

class ProcessSyncPageUseCase(
    private val determineState: DetermineMessageStateUseCase,
    private val mergeMarkers: MergeSyncMarkersUseCase,
    private val repo: SyncRepository,
) {
    suspend fun execute(page: SyncPage, owner: String): List<GapFillRequest> {
        val gaps = mutableListOf<GapFillRequest>()
        val writes = mutableListOf<ConversationWrite>()
        val existingRosterJids = repo.getRosterJids(owner)

        for (conv in page.conversations) {
            // Skip self-JID, XEN, and server-domain conversations (matches existing CSM filter)
            if (conv.jid == owner
                || conv.type == "urn:xabber:xen:0"
                || conv.jid == owner.substringAfter("@")) continue

            if (conv.status == SyncStatus.DELETED) {
                writes += ConversationWrite.Delete(conv.jid, conv.type)
                continue
            }

            val existing = repo.getConversation(owner, conv.jid, conv.type)
            val merged = mergeMarkers.execute(existing?.markers, conv.markers, conv.lastMessage?.timestampUs)

            val markersChanged = existing == null
                || existing.markers.displayedId != merged.displayedId
                || existing.markers.deliveredId != merged.deliveredId
                || existing.markers.unreadCount != merged.unreadCount

            val message = conv.lastMessage?.let { msg ->
                val msgWithState = msg.copy(
                    currentState = existing?.lastMessageState,
                    isOutgoing = msg.fromJid == owner,
                )
                val state = determineState.execute(msgWithState, conv.markers)
                ConversationWrite.MessageUpdate(msgWithState, state)
            }

            detectGap(existing, conv)?.let { gaps += it }

            writes += ConversationWrite.Upsert(
                conv = conv,
                finalMarkers = merged,
                message = message,
                markersChanged = markersChanged,
                createRosterIfMissing = conv.jid !in existingRosterJids,
            )
        }

        repo.applyBatch(owner, writes)
        return gaps
    }

    private fun detectGap(existing: StoredConversation?, conv: SyncConversation): GapFillRequest? {
        val serverLastMsgMs = conv.lastMessage?.timestampUs?.div(1000L) ?: return null
        val localLastMsgMs = existing?.lastMessageDateMs ?: return null
        val isFixed = existing.isGapFixedForSession
        if (!mergeMarkers.shouldRequestGapFill(localLastMsgMs, serverLastMsgMs, isFixed)) return null
        return GapFillRequest(
            jid = conv.jid,
            conversationType = conv.type,
            localLastMessageDateMs = localLastMsgMs,
            serverLastMessageDateMs = serverLastMsgMs,
        )
    }
}
