package com.xabber.data.sync

import com.xabber.common.SettingManager
import com.xabber.data.sync.mapper.SyncConversationMapper
import com.xabber.data.sync.mapper.SyncMessageMapper
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.domain.sync.model.*
import com.xabber.domain.sync.repository.SyncRepository
import com.xabber.domain.sync.usecase.DetermineMessageStateUseCase
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query

class SyncRepositoryImpl(private val realm: Realm) : SyncRepository {

    private val determineState = DetermineMessageStateUseCase()

    // --- Version (SharedPreferences, not Realm) ---

    override suspend fun getVersion(owner: String): String =
        SettingManager.getClientSynchronizationVersion(owner) ?: "0"

    override suspend fun saveVersion(owner: String, version: String) {
        SettingManager.saveClientSynchronizationVersion(owner, version)
    }

    // --- Gap flags ---

    override suspend fun resetGapFlags(owner: String) {
        realm.write {
            query<LastChatsStorageItem>("owner = $0 AND isHistoryGapFixedForSession = true", owner)
                .find().forEach { findLatest(it)?.isHistoryGapFixedForSession = false }
        }
    }

    override suspend fun markGapFixed(owner: String, jid: String, type: String) {
        realm.write {
            val chat = query<LastChatsStorageItem>(
                "jid = $0 AND owner = $1 AND conversationType_ = $2", jid, owner, type
            ).first().find()
            chat?.let { findLatest(it)?.isHistoryGapFixedForSession = true }
        }
    }

    // --- Read ---

    override suspend fun getConversation(owner: String, jid: String, type: String): StoredConversation? {
        val chat = realm.query<LastChatsStorageItem>(
            "jid = $0 AND owner = $1 AND conversationType_ = $2", jid, owner, type
        ).first().find() ?: return null

        return StoredConversation(
            jid = jid,
            type = type,
            markers = SyncMarkers(
                unreadCount = chat.unread.toLong(),
                unreadAfterUs = null,
                displayedId = chat.displayedId,
                deliveredId = chat.deliveredId,
            ),
            lastMessageDateMs = chat.messageDate,
            isGapFixedForSession = chat.isHistoryGapFixedForSession,
            lastMessageState = chat.lastMessage?.state,
        )
    }

    override suspend fun getRosterJids(owner: String): Set<String> =
        realm.query<RosterStorageItem>("owner = $0", owner)
            .find()
            .map { it.jid }
            .toSet()

    // --- Batch write (single transaction per page) ---

    override suspend fun applyBatch(owner: String, writes: List<ConversationWrite>) {
        if (writes.isEmpty()) return
        realm.write {
            for (write in writes) {
                when (write) {
                    is ConversationWrite.Delete -> {
                        val chat = query<LastChatsStorageItem>(
                            "jid = $0 AND owner = $1 AND conversationType_ = $2",
                            write.jid, owner, write.type
                        ).first().find()
                        chat?.let { findLatest(it)?.let { latest -> delete(latest) } }
                    }
                    is ConversationWrite.Upsert -> applyUpsert(owner, write)
                }
            }
        }
    }

    private fun io.realm.kotlin.MutableRealm.applyUpsert(owner: String, write: ConversationWrite.Upsert) {
        val conv = write.conv
        val jid = conv.jid

        // Roster auto-creation
        if (write.createRosterIfMissing) {
            val rosterPrimary = RosterStorageItem.genPrimary(jid, owner)
            val existingRoster = query<RosterStorageItem>("primary = $0", rosterPrimary).first().find()
            if (existingRoster == null) {
                copyToRealm(RosterStorageItem().apply {
                    this.primary = rosterPrimary
                    this.owner = owner
                    this.jid = jid
                    this.nickname = jid
                }, UpdatePolicy.ALL)
            }
        }

        // Upsert LastChatsStorageItem
        val convType = ConversationType.fromRaw(conv.type)
        val primary = LastChatsStorageItem.genPrimary(jid, owner, convType)
        val existingChat = query<LastChatsStorageItem>("primary = $0", primary).first().find()
        val chat = if (existingChat != null) {
            findLatest(existingChat) ?: copyToRealm(LastChatsStorageItem().apply {
                this.primary = primary
                this.owner = owner
                this.jid = jid
            }, UpdatePolicy.ALL)
        } else {
            copyToRealm(LastChatsStorageItem().apply {
                this.primary = primary
                this.owner = owner
                this.jid = jid
            }, UpdatePolicy.ALL)
        }

        val messageDateMs = write.message?.msg?.timestampUs?.div(1000L) ?: chat.messageDate
        SyncConversationMapper.applyTo(chat, conv, write.finalMarkers, messageDateMs, owner)

        // Upsert last message
        write.message?.let { msgWrite ->
            val msgPrimary = MessageStorageItem.genPrimary(msgWrite.msg.id, owner)
            val existingMsg = query<MessageStorageItem>("primary = $0", msgPrimary).first().find()
            if (existingMsg == null) {
                val newMsg = SyncMessageMapper.toStorageItem(msgWrite.msg, msgWrite, owner, jid, conv.type)
                val persisted = copyToRealm(newMsg, UpdatePolicy.ALL)
                chat.lastMessage = persisted
                chat.lastMessageId = msgWrite.msg.id
            }
        }

        // Per-message state update (skipped when markers unchanged)
        if (write.markersChanged) {
            val allMessages = query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND conversationType_ = $2", owner, jid, conv.type
            ).find()
            val markers = write.finalMarkers
            allMessages.forEach { msg ->
                val domainMsg = SyncMessage(
                    id = msg.messageId,
                    fromJid = if (msg.outgoing) owner else jid,
                    body = msg.body,
                    timestampUs = msg.sentDate * 1000L,
                    isOutgoing = msg.outgoing,
                    groupNickname = null,
                    currentState = msg.state,
                )
                val result = determineState.execute(domainMsg, markers)
                msg.state = result.state
                msg.isRead = result.isRead
            }
        }
    }

    override fun close() = realm.close()
}
