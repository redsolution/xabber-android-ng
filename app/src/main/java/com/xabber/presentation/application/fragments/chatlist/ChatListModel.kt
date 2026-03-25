package com.xabber.presentation.application.fragments.chatlist

import android.os.Build
import androidx.annotation.RequiresApi
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.ChatListDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
class ChatListModel {
    private val realm = Realm.open(defaultRealmConfig())

    fun getChatsFlow(showUnreadOnly: Boolean): Flow<List<ChatListDto>> {
        val accounts = getEnabledAccountIds()
        val query = buildQuery(accounts, showUnreadOnly)
        return realm.query<LastChatsStorageItem>(query)
            .sort("pinnedPosition" to Sort.DESCENDING, "messageDate" to Sort.DESCENDING)
            .asFlow()
            .map { changes ->
                val dtos = changes.list.map { item -> item.toChatListDto() }
                val top = dtos.firstOrNull()
                android.util.Log.d("ChatListModel", "getChatsFlow emit: size=${dtos.size}, top=${top?.opponentJid}, body='${top?.lastMessageBody?.take(30)}', unread=${top?.unread}, msgDate=${top?.lastMessageDate}")
                dtos
            }
    }

    suspend fun getChatsSnapshot(showUnreadOnly: Boolean): List<ChatListDto> = withContext(Dispatchers.IO) {
        val accounts = getEnabledAccountIds()
        val query = buildQuery(accounts, showUnreadOnly)
        val result = realm.query<LastChatsStorageItem>(query)
            .sort("pinnedPosition" to Sort.DESCENDING, "messageDate" to Sort.DESCENDING)
            .find()
        result.map { item ->
            val baseDto = item.toChatListDto()
            if (baseDto.isGroup) {
                baseDto
            } else {
                val presenceStatus = getBestPresence(item.owner, item.jid)
                baseDto.copy(status = presenceStatus)
            }
        }
    }

    private fun buildQuery(accounts: RealmSet<String>, showUnreadOnly: Boolean): String {
        val base = "owner IN {${accounts.joinToString { "'$it'" }}} AND isArchived = false " +
                "AND conversationType_ IN {'${ConversationType.Regular.rawValue}', " +
                "'${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', " +
                "'${ConversationType.Favorites.rawValue}'}"

        return if (showUnreadOnly) "$base AND unread > 0" else base
    }

    private fun getEnabledAccountIds(): RealmSet<String> {
        val set = realmSetOf<String>()
        val accounts = realm.query<com.xabber.data_base.models.account.AccountStorageItem>("enabled = true").find()
        accounts.forEach { set.add(it.primary) }
        return set
    }



    // === Бизнес-операции ===
    suspend fun pinChat(chatId: String) = withContext(Dispatchers.IO) {
        realm.write {
            query<LastChatsStorageItem>("primary = '$chatId'")
                .first()
                .find()
                ?.let { it.pinnedPosition = System.currentTimeMillis() }
        }
    }

    suspend fun unpinChat(chatId: String) = withContext(Dispatchers.IO) {
        realm.write {
            query<LastChatsStorageItem>("primary = '$chatId'")
                .first()
                .find()
                ?.let { it.pinnedPosition = -1L }
        }
    }

    suspend fun archiveChat(chatId: String) = withContext(Dispatchers.IO) {
        realm.write {
            query<LastChatsStorageItem>("primary = '$chatId'")
                .first()
                .find()
                ?.let { it.isArchived = true }
        }
    }

    suspend fun muteChat(chatId: String, muteUntil: Long) = withContext(Dispatchers.IO) {
        realm.write {
            query<LastChatsStorageItem>("primary = '$chatId'")
                .first()
                .find()
                ?.let { it.muteExpired = muteUntil }
        }
    }
    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        realm.write { delete(query<LastChatsStorageItem>("primary = '$chatId'")) }
    }


    suspend fun markAllAsRead() = withContext(Dispatchers.IO) {
        realm.write {
            query<LastChatsStorageItem>().find().forEach { it.unread = 0 }
            query<com.xabber.data_base.models.messages.MessageStorageItem>().find().forEach { it.isRead = true }
        }
    }

    fun close() = realm.close()

    private fun getBestPresence(owner: String, contactJid: String): ResourceStatus {
        val resources = realm.query<ResourceStorageItem>(
            "owner = $0 AND jid = $1",
            owner, contactJid
        ).find()
        if (resources.isEmpty()) return ResourceStatus.OFFLINE
        // Choose the resource with highest status rank, then highest priority
        return resources.maxWithOrNull(
            compareBy<ResourceStorageItem> { it.status.rank() }
                .thenByDescending { it.priority }
        )?.status ?: ResourceStatus.OFFLINE
    }

    fun observeAllPresences(): Flow<Map<String, ContactPresence>> {
        return realm.query<ResourceStorageItem>()
            .asFlow()
            .map { changes ->
                val resources = changes.list
                val presenceMap = mutableMapOf<String, ContactPresence>()
                resources.groupBy { "${it.owner}|${it.jid}" }
                    .forEach { (key, list) ->
                        val best = list.maxWithOrNull(
                            compareBy<ResourceStorageItem> { it.status.rank() }
                                .thenByDescending { it.priority }
                        ) ?: return@forEach
                        presenceMap[key] = ContactPresence(best.status, best.statusMessage)
                    }
                android.util.Log.d("ChatListModel", "observeAllPresences: ${resources.size} total resources, ${presenceMap.size} contacts")
                presenceMap
            }
            .distinctUntilChanged()
    }

    fun observePresencesForChats(chats: List<ChatListDto>): Flow<Map<String, ContactPresence>> {
        val query = buildPresenceQueryForChats(chats)
        if (query == null) {
            return flowOf(emptyMap())
        }

        return realm.query<ResourceStorageItem>(query)
            .asFlow()
            .map { changes ->
                val resources = changes.list
                val presenceMap = mutableMapOf<String, ContactPresence>()
                resources.groupBy { "${it.owner}|${it.jid}" }
                    .forEach { (key, list) ->
                        val best = list.maxWithOrNull(
                            compareBy<ResourceStorageItem> { it.status.rank() }
                                .thenByDescending { it.priority }
                        ) ?: return@forEach
                        presenceMap[key] = ContactPresence(best.status, best.statusMessage)
                    }
                android.util.Log.d("ChatListModel", "observePresencesForChats: tracked=${chats.size}, resources=${resources.size}, contacts=${presenceMap.size}")
                presenceMap
            }
            .distinctUntilChanged()
    }

    data class ContactPresence(val status: ResourceStatus, val statusMessage: String?)

    private fun ResourceStatus.rank(): Int = when (this) {
        ResourceStatus.CHAT    -> 5
        ResourceStatus.ONLINE  -> 4
        ResourceStatus.AWAY    -> 3
        ResourceStatus.DND     -> 2
        ResourceStatus.XA      -> 1
        ResourceStatus.OFFLINE -> 0
    }
}

internal fun buildPresenceQueryForChats(chats: List<ChatListDto>): String? {
    val targets = chats
        .asSequence()
        .filterNot { it.isGroup }
        .map { it.owner to it.opponentJid }
        .distinct()
        .toList()

    if (targets.isEmpty()) {
        return null
    }

    return targets.joinToString(
        separator = " OR ",
        prefix = "(",
        postfix = ")"
    ) { (owner, jid) ->
        "(owner = '${escapeRealmValue(owner)}' AND jid = '${escapeRealmValue(jid)}')"
    }
}

private fun escapeRealmValue(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
