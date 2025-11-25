package com.xabber.presentation.application.fragments.chatlist

import android.os.Build
import androidx.annotation.RequiresApi
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
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
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
class ChatListModel {
    private val realm = Realm.open(defaultRealmConfig())

    fun getChatsFlow(showUnreadOnly: Boolean): Flow<ResultsChange<LastChatsStorageItem>> {
        val accounts = getEnabledAccountIds()
        val query = buildQuery(accounts, showUnreadOnly)
        return realm.query<LastChatsStorageItem>(query)
            .sort("pinnedPosition" to Sort.DESCENDING, "messageDate" to Sort.DESCENDING)
            .asFlow()
    }

    suspend fun getChatsSnapshot(showUnreadOnly: Boolean): List<ChatListDto> = withContext(Dispatchers.IO) {
        val accounts = getEnabledAccountIds()
        val query = buildQuery(accounts, showUnreadOnly)
        val result = realm.query<LastChatsStorageItem>(query)
            .sort("pinnedPosition" to Sort.DESCENDING, "messageDate" to Sort.DESCENDING)
            .find()

        result.map { it.toChatListDto() } // ← только маппинг, без цветов
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
        realm.writeBlocking {
            val accounts = query<com.xabber.data_base.models.account.AccountStorageItem>("enabled = true").find()
            accounts.forEach { set.add(it.primary) }
        }
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
}