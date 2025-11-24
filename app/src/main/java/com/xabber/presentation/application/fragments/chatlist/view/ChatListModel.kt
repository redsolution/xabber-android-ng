package com.xabber.presentation.application.fragments.chatlist.view

import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.dto.ChatListDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatListModel {
    private val realm = Realm.open(defaultRealmConfig())

    fun observeChats(
        showUnreadOnly: Boolean,
        enabledOwnerIds: Set<String>
    ): Flow<List<ChatListDto>> {
        if (enabledOwnerIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())

        val query = buildString {
            append("owner IN {${enabledOwnerIds.joinToString { "'$it'" }}} AND isArchived = false")
            append(" AND conversationType_ IN {0,1,2,3}") // Regular, Group, Channel, Favorites
            if (showUnreadOnly) append(" AND unread > 0")
        }

        return realm.query<LastChatsStorageItem>(query)
            .sort("pinnedPosition", Sort.DESCENDING)
            .sort("messageDate", Sort.DESCENDING)
            .asFlow()
            .map { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> changes.list.map { it.toChatListDto() }
                    else -> emptyList()
                }
            }
    }

    suspend fun pinChat(id: String) = with(realm) {
        write {
            query<LastChatsStorageItem>("primary = '$id'").first().find()
                ?.let { findLatest(it)?.pinnedPosition = System.currentTimeMillis() }
        }
    }

    suspend fun unpinChat(id: String) = with(realm) {
        write {
            query<LastChatsStorageItem>("primary = '$id'").first().find()
                ?.let { findLatest(it)?.pinnedPosition = -1L }
        }
    }

    suspend fun setArchived(id: String, archived: Boolean = true) = with(realm) {
        write {
            query<LastChatsStorageItem>("primary = '$id'").first().find()
                ?.let { findLatest(it)?.isArchived = archived }
        }
    }

    suspend fun setMute(id: String, muteUntil: Long) = with(realm) {
        write {
            query<LastChatsStorageItem>("primary = '$id'").first().find()
                ?.let { findLatest(it)?.muteExpired = muteUntil }
        }
    }

    suspend fun deleteChat(id: String) = with(realm) {
        write { query<LastChatsStorageItem>("primary = '$id'").first().find()?.let { delete(it) } }
    }

    suspend fun markAllAsRead() = with(realm) {
        write {
            query<LastChatsStorageItem>().find().forEach { it.unread = 0 }
            query<com.xabber.data_base.models.messages.MessageStorageItem>().find()
                .forEach { it.isRead = true }
        }
    }

    fun close() = realm.close()
}