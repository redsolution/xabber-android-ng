package com.xabber.presentation.application.fragments.chat.view

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import com.xabber.xmpp.jid.XMPPJID
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChatModel(
    private val chatId: String,
    private val owner: String,
    private val opponent: String,
    private val conversationType: ConversationType
) {
    private val realm = Realm.open(defaultRealmConfig())
    private val TAG = "ChatModel"

    // === Наблюдение за данными (Flows) ===

    fun observeChat(): Flow<ChatListDto?> {
        return realm.query<LastChatsStorageItem>("primary = $0", chatId)
            .asFlow()
            .map { changes ->
                when (changes) {
                    is ResultsChange<*> -> changes.list.firstOrNull()?.toChatListDto()
                    else -> changes.list.firstOrNull()?.toChatListDto()
                }
            }
    }

    @OptIn(FlowPreview::class)
    fun observeMessages(): Flow<List<MessageDto>> {
        return realm.query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
            owner, opponent, conversationType.rawValue
        )
            .sort("sentDate", Sort.ASCENDING)
            .asFlow()
            .map { changes ->
                changes.list.mapNotNull { it.toMessageDto() }
            }
            .debounce(500L)
    }

    // === Чтение данных ===

    suspend fun getChat(): ChatListDto? = with(realm) {
        query<LastChatsStorageItem>("primary = $0", chatId).first().find()?.toChatListDto()
    }

    suspend fun getMessages(): List<MessageDto> = with(realm) {
        query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
            owner, opponent, conversationType.rawValue
        )
            .sort("sentDate", Sort.ASCENDING)
            .find()
            .mapNotNull { it.toMessageDto() }
    }

    suspend fun getDraft(id: String): String? = with(realm) {
        query<LastChatsStorageItem>("primary = $0", id).first().find()?.draftMessage
    }

    suspend fun getContactId(id: String): String? = with(realm) {
        query<LastChatsStorageItem>("primary = $0", id).first().find()?.rosterItem?.primary
    }

    suspend fun getAccount(id: String): AccountDto? = with(realm) {
        query<AccountStorageItem>("primary = $0", id).first().find()?.toAccountDto()
    }

    suspend fun getMessage(primary: String?): MessageDto? = with(realm) {
        primary?.let {
            query<MessageStorageItem>("primary = $0", it).first().find()?.let { item ->
                item.toMessageDto()
            }
        }
    }

    suspend fun getOldestMessageId(): String? = with(realm) {
        val oldest = query<MessageStorageItem>(
            "owner == $0 AND opponent == $1 AND conversationType_ == $2",
            owner, opponent, conversationType.rawValue
        )
            .sort("sentDate", Sort.DESCENDING)
            .first()
            .find()
        Log.w(TAG, "message id check $oldest")
        oldest?.messageId
    }

    suspend fun getSelectedMessage(selectedItems: Set<String>): MessageDto? = with(realm) {
        if (selectedItems.isNotEmpty()) {
            val id = selectedItems.first()
            query<MessageStorageItem>("primary = $0", id).first().find()?.let { item ->
                item.toMessageDto()
            }
        } else null
    }

    suspend fun getSelectedText(selectedItems: Set<String>): String = with(realm) {
        var text = ""
        selectedItems.forEach { primary ->
            query<MessageStorageItem>("primary = $0", primary).first().find()?.let { message ->
                text += "${message.body}\n"
            }
        }
        text
    }

    suspend fun getForwardMessagesText(selectedItems: Set<String>): String = with(realm) {
        var text = ""
        selectedItems.forEach { primary ->
            query<MessageStorageItem>("primary = $0", primary).first().find()?.let { message ->
                text += "${if (message.outgoing) message.owner else message.opponent}\n${message.body}\n"
            }
        }
        text
    }

    suspend fun getSelectedMessageText(selectedItems: Set<String>): String = with(realm) {
        if (selectedItems.isNotEmpty()) {
            val id = selectedItems.first()
            query<MessageStorageItem>("primary = $0", id).first().find()?.body ?: ""
        } else ""
    }

    suspend fun getMessageId(selectedItems: Set<String>): String = selectedItems.firstOrNull() ?: ""

    suspend fun getMessagePosition(primary: String, messages: List<MessageDto>): Int {
        return messages.indexOfFirst { it.primary == primary }
    }

    suspend fun lastPositionPrimary(id: String): String = with(realm) {
        query<LastChatsStorageItem>("primary = $0", id).first().find()?.lastPosition ?: ""
    }

    suspend fun getPositionMessage(lastPosition: String, messages: List<MessageDto>): Int {
        messages.forEachIndexed { index, message ->
            if (message.primary == lastPosition) return index
        }
        return 0
    }

    suspend fun isOutgoing(selectedItems: Set<String>): Boolean = with(realm) {
        if (selectedItems.size != 1) return false
        val primary = selectedItems.first()
        query<MessageStorageItem>("primary = $0", primary).first().find()?.outgoing ?: false
    }


    // === Запись данных ===

    suspend fun insertMessage(chatId: String, messageDto: MessageDto) = with(realm) {
        writeBlocking {
            val bareOpponentJid = XMPPJID(fullJID = messageDto.opponentJid).bare().toString()
            val primary = MessageStorageItem.genPrimary(messageDto.archivedId, messageDto.owner)
            if (primary.isEmpty()) {
                Log.w(TAG, "Skipping message with invalid primary: archivedId=${messageDto.archivedId}, owner=${messageDto.owner}")
                return@writeBlocking
            }

            val existing = query<MessageStorageItem>(
                "primary = $0 OR (archivedId = $1 AND archivedId != '' AND conversationType_ = $2)",
                primary, messageDto.archivedId, conversationType.rawValue
            ).first().find()

            if (existing != null) {
                findLatest(existing)?.apply {
                    state = messageDto.messageSendingState
                    isRead = !messageDto.isUnread
                    if (messageDto.editTimestamp > editDate) {
                        editDate = messageDto.editTimestamp
                        body = messageDto.messageBody
                    }
                }
                Log.d(TAG, "Updated existing message: $primary")
                return@writeBlocking
            }

            val references: RealmList<MessageReferenceStorageItem> = realmListOf()
            messageDto.references.forEach { ref ->
                val refItem = copyToRealm(MessageReferenceStorageItem().apply {
                    this.primary = "${ref.id}_${System.currentTimeMillis()}"
                    uri = ref.uri
                    mimeType = ref.mimeType
                    isGeo = ref.isGeo
                    latitude = ref.latitude
                    longitude = ref.longitude
                    isAudioMessage = ref.isVoiceMessage
                    fileName = ref.fileName
                    fileSize = ref.size
                }, UpdatePolicy.ALL)
                references.add(refItem)
            }

            val validOwner = messageDto.owner.ifEmpty { this@ChatModel.owner }
            val messageConversationType = if (messageDto.isGroup) ConversationType.Group else conversationType
            val chatPrimary = LastChatsStorageItem.genPrimary(bareOpponentJid, validOwner, messageConversationType)

            val message = copyToRealm(MessageStorageItem().apply {
                this.primary = primary
                this.owner = messageDto.owner
                this.opponent = bareOpponentJid
                body = messageDto.messageBody
                date = messageDto.sentTimestamp
                sentDate = messageDto.sentTimestamp
                editDate = messageDto.editTimestamp
                outgoing = messageDto.isOutgoing
                isRead = !messageDto.isUnread
                this.references = references
                conversationType_ = messageConversationType.rawValue
                archivedId = messageDto.archivedId
                state = messageDto.messageSendingState
                messageId = messageDto.archivedId
            }, UpdatePolicy.ALL)

            // Update or create LastChatsStorageItem
            val existingChats = query<LastChatsStorageItem>(
                "jid = $0 AND owner = $1", bareOpponentJid, messageDto.owner
            ).find()
            var targetChat: LastChatsStorageItem? = existingChats.find { it.conversationType_ == messageConversationType.rawValue }

            if (targetChat == null && existingChats.isNotEmpty()) {
                targetChat = existingChats.firstOrNull()
                if (targetChat != null && messageDto.isGroup) {
                    findLatest(targetChat)?.conversationType_ = ConversationType.Group.rawValue
                }
            }

            if (targetChat != null) {
                findLatest(targetChat)?.apply {
                    if (message.sentDate > messageDate) {
                        lastMessage = message
                        messageDate = message.sentDate
                        lastMessageId = message.messageId
                        if (!messageDto.isOutgoing && muteExpired <= 0) {
                            isArchived = false
                            unread = (unread ?: 0) + if (messageDto.isUnread) 1 else 0
                        }
                    }
                }
            } else {
                copyToRealm(LastChatsStorageItem().apply {
                    this.primary = chatPrimary
                    this.owner = messageDto.owner
                    jid = bareOpponentJid
                    conversationType_ = messageConversationType.rawValue
                    messageDate = message.sentDate
                    isSynced = true
                    isInitialArchiveLoaded = true
                    lastMessage = message
                    lastMessageId = message.messageId
                    if (!messageDto.isOutgoing && muteExpired <= 0) {
                        isArchived = false
                        unread = if (messageDto.isUnread) 1 else 0
                    }
                }, UpdatePolicy.ALL)
            }
        }
    }

    suspend fun insertMessagesFromReceiver(messages: List<MessageDto>) {
        messages.forEach { insertMessage(chatId, it) }
    }

    suspend fun deleteMessage(primary: String, forAll: Boolean = false) = with(realm) {
        writeBlocking {
            query<MessageStorageItem>("primary = $0", primary).first().find()?.let { delete(it) }
        }
        if (forAll) {
            // TODO: Implement server request to delete message
            Log.d(TAG, "Server delete for all not implemented")
        }
    }

    suspend fun deleteMessages(selectedItems: Set<String>, forAll: Boolean = false) {
        selectedItems.forEach { deleteMessage(it, forAll) }
    }

    suspend fun editMessage(primary: String, newBody: String) = with(realm) {
        writeBlocking {
            query<MessageStorageItem>("primary = $0", primary).first().find()?.apply {
                body = newBody
                editDate = System.currentTimeMillis()
            }
        }
    }

    suspend fun setMute(id: String, mute: Long) = with(realm) {
        writeBlocking {
            query<LastChatsStorageItem>("primary = $0", id).first().find()?.muteExpired = mute
        }
    }

    suspend fun setUnread(id: String) = with(realm) {
        writeBlocking {
            query<MessageStorageItem>("primary = $0", id).first().find()?.isRead = true
        }
    }

    suspend fun markAllMessageUnread(chatId: String) = with(realm) {
        write {
            query<LastChatsStorageItem>("primary = $0", chatId).first().find()?.let { chat ->
                val owner = chat.owner
                val opponent = chat.jid
                query<MessageStorageItem>(
                    "isRead = false AND owner = $0 AND opponent = $1",
                    owner, opponent
                ).find().forEach { it.isRead = true }
            }
        }
    }

    suspend fun saveDraft(id: String, draft: String?) = with(realm) {
        writeBlocking {
            query<LastChatsStorageItem>("primary = $0", id).first().find()?.apply {
                val oldDraft = draftMessage
                if (oldDraft != draft) {
                    draftMessage = draft
                    if (!draft.isNullOrEmpty()) {
                        messageDate = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    suspend fun saveLastPosition(id: String, savedPosition: String) = with(realm) {
        writeBlocking {
            query<LastChatsStorageItem>("primary = $0", id).first().find()?.lastPosition = savedPosition
        }
    }

    suspend fun clearHistory(chatId: String, opponentJid: String) = with(realm) {
        write {
            query<MessageStorageItem>("opponent = $0", opponentJid).find().forEach { delete(it) }
        }
    }

    suspend fun deleteChat(id: String) = with(realm) {
        writeBlocking {
            query<LastChatsStorageItem>("primary = $0", id).first().find()?.let { delete(it) }
        }
    }

    suspend fun insertChat(id: String) = with(realm) {
        writeBlocking {
            copyToRealm(LastChatsStorageItem().apply {
                primary = id
                this.owner = this@ChatModel.owner
                jid = this@ChatModel.opponent
                conversationType_ = this@ChatModel.conversationType.rawValue
            }, UpdatePolicy.ALL)
        }
    }

    // === Утилиты ===

    suspend fun getVoiceLength(path: String): Long {
        // TODO: Implement voice length calculation
        return 0L
    }

    fun close() {
        realm.close()
    }
}