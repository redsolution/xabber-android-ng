package com.xabber.data.sync.mapper

import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.domain.sync.model.SyncConversation
import com.xabber.domain.sync.model.SyncMarkers

object SyncConversationMapper {
    // Applies domain data onto an existing (or newly created) LastChatsStorageItem.
    // Call inside a realm.write { } block.
    fun applyTo(
        chat: LastChatsStorageItem,
        conv: SyncConversation,
        finalMarkers: SyncMarkers,
        messageDateMs: Long,
        owner: String,
    ) {
        chat.conversationType_ = conv.type
        chat.pinnedPosition = conv.pinned
        chat.muteExpired = conv.muteUntilMs

        chat.unread = finalMarkers.unreadCount.toInt()
        chat.displayedId = finalMarkers.displayedId
        chat.deliveredId = finalMarkers.deliveredId

        // lastReadMessageDate: use unreadAfterUs if present, else message date when all read
        val unreadAfterMs = finalMarkers.unreadAfterUs?.div(1000L) ?: 0L
        chat.lastReadMessageDate = when {
            unreadAfterMs > 0L -> unreadAfterMs
            finalMarkers.unreadCount == 0L && messageDateMs > 0L -> messageDateMs
            else -> chat.lastReadMessageDate  // keep existing
        }

        if (messageDateMs > 0L) chat.messageDate = messageDateMs
    }
}
