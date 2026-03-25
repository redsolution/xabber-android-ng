package com.xabber.xmpp.core.storage

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.stream.ProcessedMessageId
import com.xabber.stream.Stream
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query

class LegacyStreamPersistenceRepository(
    private val ownerJid: String,
) {
    private val realmLazy = lazy { Realm.open(defaultRealmConfig()) }
    private val realm: Realm by realmLazy
    private val tag = "LegacyStreamPersistence"

    suspend fun clearStaleProcessedMessages() {
        realm.write {
            val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            val stale = query<ProcessedMessageId>("owner = $0 AND timestamp < $1", ownerJid, threshold).find()
            delete(stale)
            Log.d(tag, "Cleared ${stale.size} stale ProcessedMessageId entries for owner=$ownerJid")
        }
    }

    fun loadProcessedIds(): MutableSet<String> {
        val stored = realm.query<ProcessedMessageId>("owner = $0", ownerJid).find()
        Log.d(tag, "Loaded ${stored.size} processed message IDs for owner=$ownerJid")
        return stored.mapTo(mutableSetOf()) { it.messageId }
    }

    suspend fun persistQueuedMessage(
        item: Stream.MessageQueueItem,
        processedIds: MutableSet<String>,
    ): Boolean {
        val message = item.message
        val messageId = message.id ?: return false
        val from = message.from?.bare() ?: return false
        val to = message.to?.bare() ?: return false

        if (processedIds.contains(messageId)) {
            val msgPrimary = MessageStorageItem.genPrimary(messageId, ownerJid)
            val existingMessage = realm.query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
            if (existingMessage != null) {
                Log.d(tag, "Confirmed messageId=$messageId already stored, skipping")
                return false
            }
            Log.w(tag, "messageId=$messageId marked processed but absent in DB, reprocessing")
        }

        var isOutgoing = item.isArchived || (from == ownerJid)
        val opponent = if (isOutgoing) to else from
        if (message.body.isNullOrEmpty()) {
            Log.d(tag, "Skipping bodyless message: id=$messageId")
            return false
        }

        realm.write {
            val msgPrimary = MessageStorageItem.genPrimary(messageId, ownerJid)
            val existing = query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
            if (existing != null) {
                Log.d(tag, "Skipping duplicate message in DB: id=$messageId")
                return@write
            }

            val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", opponent, ownerJid).first().find()
                ?: copyToRealm(RosterStorageItem().apply {
                    primary = RosterStorageItem.genPrimary(opponent, ownerJid)
                    jid = opponent
                    owner = ownerJid
                    customNickname = opponent
                }, UpdatePolicy.ALL)

            val isGroupChat = message.element("x", namespace = "https://xabber.com/protocol/groups") != null
            if (isGroupChat) {
                val userId = message.element("x", namespace = "https://xabber.com/protocol/groups")
                    ?.element("reference", namespace = "https://xabber.com/protocol/references")
                    ?.element("user", namespace = "https://xabber.com/protocol/groups")
                    ?.getAttribute("id")
                isOutgoing = userId == ownerJid
            }

            val persistedMessage = copyToRealm(MessageStorageItem().apply {
                primary = msgPrimary
                this.messageId = messageId
                owner = ownerJid
                this.opponent = opponent
                body = message.body ?: ""
                date = item.timestamp
                sentDate = item.timestamp
                editDate = 0L
                outgoing = isOutgoing
                conversationType_ = when {
                    item.isClientSync && to == "favorites.redsolution.com" -> "urn:xabber:favorites:0"
                    isGroupChat -> "https://xabber.com/protocol/groups"
                    else -> "urn:xabber:chat"
                }
                isRead = isOutgoing || item.isArchived
                state = if (isOutgoing) MessageSendingState.Deliver else MessageSendingState.Sent
                queryIds = item.queryId
                archivedId = message.element("archived", namespace = "urn:xmpp:mam:tmp")?.getAttribute("id") ?: ""
            }, UpdatePolicy.ALL)

            val conversationType = ConversationType.fromRaw(persistedMessage.conversationType_)
            val chatPrimary = LastChatsStorageItem.genPrimary(opponent, ownerJid, conversationType)
            val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find()
            if (chat == null) {
                copyToRealm(LastChatsStorageItem().apply {
                    primary = chatPrimary
                    jid = opponent
                    owner = ownerJid
                    conversationType_ = conversationType.rawValue
                    isArchived = false
                    unread = if (isOutgoing || item.isArchived) 0 else 1
                    messageDate = item.timestamp
                    lastMessageId = messageId
                    this.rosterItem = rosterItem
                    lastMessage = persistedMessage
                }, UpdatePolicy.ALL)
            } else {
                findLatest(chat)?.apply {
                    if (item.timestamp / 10000 > messageDate) {
                        unread = if (isOutgoing || item.isArchived) unread else unread + 1
                        messageDate = item.timestamp
                        lastMessageId = messageId
                        lastMessage = persistedMessage
                        isArchived = false
                    }
                }
            }

            processedIds.add(messageId)
            copyToRealm(ProcessedMessageId().apply {
                this.messageId = messageId
                owner = ownerJid
                timestamp = item.timestamp / 10000
            }, UpdatePolicy.ALL)
        }

        return true
    }

    suspend fun deleteSelfChats() {
        realm.write {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", ownerJid).find()
            delete(selfChats)
            Log.d(tag, "Deleted ${selfChats.size} self-chats for owner=$ownerJid")
        }
    }

    fun close() {
        if (realmLazy.isInitialized()) {
            realm.close()
        }
    }
}
