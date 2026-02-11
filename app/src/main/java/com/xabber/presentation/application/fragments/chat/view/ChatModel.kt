package com.xabber.presentation.application.fragments.chat.view

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.stream.StreamState
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

class ChatModel(
    private val chatId: String,
    private val owner: String,
    private val opponent: String,
    private val conversationType: ConversationType
) {
    private val realm = Realm.open(defaultRealmConfig())
    private val TAG = "ChatModel"

    // === Наблюдение за данными (Flows) ===

    fun observeChat(): Flow<LastChatsStorageItem?> {
        return realm.query<LastChatsStorageItem>("primary = $0", chatId)
            .asFlow()
            .map { changes ->
                when (changes) {
                    is ResultsChange<*> -> changes.list.firstOrNull()
                    else -> changes.list.firstOrNull()
                }
            }
    }

    @OptIn(FlowPreview::class)
    fun observeMessages(): Flow<List<MessageStorageItem>> {
        return realm.query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND conversationType_ = $2",
            owner, opponent, conversationType.rawValue
        )
            .sort("sentDate", Sort.ASCENDING)
            .asFlow()
            .map { changes -> changes.list }
            .debounce(300L)
    }

    // === Чтение данных ===

    suspend fun getChat(): ChatListDto? = with(realm) {
        query<LastChatsStorageItem>("primary = $0", chatId).first().find()?.toChatListDto()
    }

    suspend fun getMessages(): List<MessageStorageItem> = with(realm) {
        query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
            owner, opponent, conversationType.rawValue
        )
            .sort("sentDate", Sort.ASCENDING)
            .find()
    }

    suspend fun getDraft(id: String): String? = with(realm) {
        query<LastChatsStorageItem>("primary = $0", id).first().find()?.draftMessage
    }

    suspend fun getContactId(id: String): String? = with(realm) {
        query<LastChatsStorageItem>("primary = $0", id).first().find()?.rosterItem?.primary
    }

    suspend fun getMessage(primary: String?): MessageStorageItem? = with(realm) {
        primary?.let {
            query<MessageStorageItem>("primary = $0", it).first().find()
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

    suspend fun getSelectedMessage(selectedItems: Set<String>): MessageStorageItem? = with(realm) {
        if (selectedItems.isNotEmpty()) {
            val id = selectedItems.first()
            query<MessageStorageItem>("primary = $0", id).first().find()
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

    suspend fun getMessagePosition(primary: String, messages: List<MessageStorageItem>): Int {
        return messages.indexOfFirst { it.primary == primary }
    }

    suspend fun lastPositionPrimary(id: String): String = with(realm) {
        query<LastChatsStorageItem>("primary = $0", id).first().find()?.lastPosition ?: ""
    }

    suspend fun getPositionMessage(lastPosition: String, messages: List<MessageStorageItem>): Int {
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

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun markAllAsRead(chatId: String) = withContext(Dispatchers.IO) {
        var lastReadPrimary: String? = null

        realm.write {
            query<LastChatsStorageItem>("primary = $0", chatId).first().find()?.let { chat ->
                val owner = chat.owner
                val opponent = chat.jid
                val convType = chat.conversationType

                val unreadMessages = query<MessageStorageItem>(
                    "isRead = false AND owner = $0 AND opponent = $1 AND conversationType_ = $2",
                    owner, opponent, convType.rawValue
                ).find()

                unreadMessages.forEach { msg ->
                    msg.isRead = true
                    msg.readDate = System.currentTimeMillis() / 1000
                    msg.state = MessageSendingState.Read
                }

                chat.lastMessage?.let { lastMsg ->
                    if (!lastMsg.outgoing) {
                        lastReadPrimary = lastMsg.primary
                    }
                }

                findLatest(chat)?.unread = 0
            }
        }

        lastReadPrimary?.let { primary ->
            sendDisplayedIfNeeded(primary)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun markAsRead(id: String) = withContext(Dispatchers.IO) {
        var messagePrimaryToMark: String? = null
        var chatPrimaryToUpdate: String? = null

        realm.write {
            query<MessageStorageItem>("primary = $0", id).first().find()?.let { msg ->
                if (!msg.isRead && !msg.outgoing) {
                    msg.isRead = true
                    msg.readDate = System.currentTimeMillis() / 1000
                    msg.state = MessageSendingState.Read

                    messagePrimaryToMark = msg.primary
                    chatPrimaryToUpdate = LastChatsStorageItem.genPrimary(msg.opponent, msg.owner, msg.conversationType)
                }
            }
        }

        chatPrimaryToUpdate?.let { primary ->
            realm.write {
                query<LastChatsStorageItem>("primary = $0", primary).first().find()?.let { chat ->
                    chat.unread = chat.unread - 1
                    if (chat.unread < 0) chat.unread = 0
                }
            }
        }

        messagePrimaryToMark?.let { primary ->
            sendDisplayedIfNeeded(primary)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun sendDisplayedIfNeeded(messagePrimary: String) {
        val account = AccountManager.find(owner) ?: return
        val stream = account.stream ?: return
        if (stream.state != StreamState.CONNECTED) return

        account.chatMarkers!!.displayed(stream, messagePrimary)
    }

    // === Запись данных ===

    suspend fun insertMessage(chatId: String, message: MessageStorageItem) = with(realm) {
        writeBlocking {
            val bareOpponentJid = XMPPJID(fullJID = message.opponent).bare().toString()
            val primary = MessageStorageItem.genPrimary(message.archivedId, message.owner)
            if (primary.isEmpty()) {
                Log.w(TAG, "Skipping message with invalid primary: archivedId=${message.archivedId}, owner=${message.owner}")
                return@writeBlocking
            }

            val existing = query<MessageStorageItem>(
                "primary = $0 OR (archivedId = $1 AND archivedId != '' AND conversationType_ = $2)",
                primary, message.archivedId, conversationType.rawValue
            ).first().find()

            if (existing != null) {
                findLatest(existing)?.apply {
                    state = message.state
                    isRead = message.isRead
                    if (message.editDate > editDate) {
                        editDate = message.editDate
                        body = message.body
                    }
                }
                Log.d(TAG, "Updated existing message: $primary")
                return@writeBlocking
            }

            val validOwner = message.owner.ifEmpty { this@ChatModel.owner }
            val messageConversationType = if (message.conversationType == ConversationType.Group) ConversationType.Group else conversationType
            val chatPrimary = LastChatsStorageItem.genPrimary(bareOpponentJid, validOwner, messageConversationType)

            val managedMessage = copyToRealm(message.apply {
                this.primary = primary
                this.opponent = bareOpponentJid
                conversationType_ = messageConversationType.rawValue
            }, UpdatePolicy.ALL)

            val existingChats = query<LastChatsStorageItem>(
                "jid = $0 AND owner = $1", bareOpponentJid, validOwner
            ).find()
            var targetChat: LastChatsStorageItem? = existingChats.find { it.conversationType_ == messageConversationType.rawValue }

            if (targetChat == null && existingChats.isNotEmpty()) {
                targetChat = existingChats.firstOrNull()
                if (targetChat != null && messageConversationType == ConversationType.Group) {
                    findLatest(targetChat)?.conversationType_ = ConversationType.Group.rawValue
                }
            }

            if (targetChat != null) {
                findLatest(targetChat)?.apply {
                    if (managedMessage.sentDate >= messageDate) {
                        lastMessage = managedMessage
                        messageDate = managedMessage.sentDate
                        lastMessageId = managedMessage.messageId
                        isSynced = true
                        if (!managedMessage.outgoing && muteExpired <= 0) {
                            isArchived = false
                            unread = (unread ?: 0) + if (!managedMessage.isRead) 1 else 0
                        } else if (managedMessage.outgoing) {
                            unread = 0
                            isArchived = false
                        }
                    }
                }
            } else {
                copyToRealm(LastChatsStorageItem().apply {
                    this.primary = chatPrimary
                    this.owner = validOwner
                    jid = bareOpponentJid
                    conversationType_ = messageConversationType.rawValue
                    messageDate = managedMessage.sentDate
                    isSynced = true
                    isInitialArchiveLoaded = true
                    lastMessage = managedMessage
                    lastMessageId = managedMessage.messageId
                    if (!managedMessage.outgoing && muteExpired <= 0) {
                        isArchived = false
                        unread = if (!managedMessage.isRead) 1 else 0
                    } else if (managedMessage.outgoing) {
                        isArchived = false
                        unread = 0
                    }
                }, UpdatePolicy.ALL)
            }
        }
    }

    suspend fun insertMessagesFromReceiver(messages: List<MessageStorageItem>) {
        messages.forEach { insertMessage(chatId, it) }
    }

    suspend fun deleteMessage(primary: String, forAll: Boolean = false) = with(realm) {
        writeBlocking {
            query<MessageStorageItem>("primary = $0", primary).first().find()?.let { delete(it) }
        }
        if (forAll) {
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

    suspend fun getAccount(id: String): AccountDto? = with(realm) {
        query<AccountStorageItem>("primary = $0", id).first().find()?.toAccountDto()
    }

    suspend fun getVoiceLength(path: String): Long {
        return 0L
    }

    fun close() {
        realm.close()
    }
}