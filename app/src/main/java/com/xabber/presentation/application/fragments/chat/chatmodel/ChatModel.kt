package com.xabber.presentation.application.fragments.chat.chatmodel

import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import com.xabber.utils.toMessageReferenceDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

class ChatModel(
    private val chatId: String,
    private val owner: String,
    private val opponent: String,
    private val conversationType: ConversationType
) {
    private val realm: Realm = Realm.open(defaultRealmConfig())
    private val messageListMutex = Mutex()
    private val _messages = CopyOnWriteArrayList<MessageDto>()
    private val selectedItems = HashSet<String>()

    // === Чтение данных ===

    fun getChat(): ChatListDto? = realm.writeBlocking {
        query<LastChatsStorageItem>("primary = '$chatId'").first().find()?.toChatListDto()
    }

    fun getAccount(accountId: String): AccountDto? = realm.writeBlocking {
        query<AccountStorageItem>("primary = '$accountId'").first().find()?.toAccountDto()
    }

    fun getContactId(id: String): String? = realm.writeBlocking {
        query<LastChatsStorageItem>("primary = '$id'").first().find()?.rosterItem?.primary
    }

    fun loadMessages(): List<MessageDto?> = realm.query<MessageStorageItem>(
        "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
        owner, opponent, conversationType.rawValue
    ).find().map { it.toMessageDto() }.sortedBy { it!!.sentTimestamp }

    fun getMessage(primary: String): MessageDto? = realm.writeBlocking {
        query<MessageStorageItem>("primary = '$primary'").first().find()?.toMessageDto()
    }

    fun getMessagePosition(primary: String): Int = _messages.indexOfFirst { it.primary == primary }

    fun getLastPositionPrimary(): String = realm.writeBlocking {
        query<LastChatsStorageItem>("primary = '$chatId'").first().find()?.lastPosition.orEmpty()
    }

    fun getUnreadCount(): Int = realm.writeBlocking {
        query<LastChatsStorageItem>("primary = '$chatId'").first().find()?.unread ?: 0
    }

    fun getMuteExpired(): Long = realm.writeBlocking {
        query<LastChatsStorageItem>("primary = '$chatId'").first().find()?.muteExpired ?: 0L
    }

    fun getOpponentName(): String = getChat()?.getChatName() ?: ""

    // === Запись данных ===

    fun saveDraft(draft: String?) = realm.writeBlocking {
        val item = query<LastChatsStorageItem>("primary = '$chatId'").first().find() ?: return@writeBlocking
        findLatest(item)?.apply {
            val oldDraft = draftMessage
            if (oldDraft != draft) {
                draftMessage = draft
                if (!draft.isNullOrEmpty()) {
                    messageDate = System.currentTimeMillis()
                }
            }
        }
    }

    fun saveLastPosition(savedPosition: String) = realm.writeBlocking {
        val item = query<LastChatsStorageItem>("primary = '$chatId'").first().find() ?: return@writeBlocking
        findLatest(item)?.lastPosition = savedPosition
    }

    fun setMute(mute: Long) = realm.writeBlocking {
        val item = query<LastChatsStorageItem>("primary = '$chatId'").first().find() ?: return@writeBlocking
        findLatest(item)?.muteExpired = mute
    }

    fun deleteMessage(primary: String, forAll: Boolean) = realm.writeBlocking {
        val message = query<MessageStorageItem>("primary = '$primary'").first().find() ?: return@writeBlocking
        findLatest(message)?.let { delete(it) }
    }

    fun deleteMessages(selected: List<String>, forAll: Boolean) = realm.writeBlocking {
        selected.forEach { primary ->
            val message = query<MessageStorageItem>("primary = '$primary'").first().find() ?: return@forEach
            findLatest(message)?.let { delete(it) }
        }
    }

    fun editMessage(primary: String, newBody: String) = realm.writeBlocking {
        val message = query<MessageStorageItem>("primary = '$primary'").first().find() ?: return@writeBlocking
        findLatest(message)?.apply {
            body = newBody
            editDate = System.currentTimeMillis()
        }
    }

    fun deleteChat() = realm.writeBlocking {
        val chat = query<LastChatsStorageItem>("primary = '$chatId'").first().find() ?: return@writeBlocking
        findLatest(chat)?.let { delete(it) }
    }

    // === Выбор сообщений ===

    suspend fun selectMessage(primary: String, checked: Boolean): Int = messageListMutex.withLock {
        if (checked) selectedItems.add(primary) else selectedItems.remove(primary)
        val position = _messages.indexOfFirst { it.primary == primary }
        if (position != -1) {
            _messages[position] = _messages[position].copy(isSelected = checked, isChecked = checked)
        }
        selectedItems.size
    }

    fun clearAllSelected(): List<MessageDto> = messageListMutex.run {
        if (selectedItems.isEmpty()) return _messages
        val updated = _messages.map { msg ->
            if (selectedItems.contains(msg.primary)) {
                msg.copy(isSelected = false, isChecked = false)
            } else msg
        }
        selectedItems.clear()
        _messages.clear()
        _messages.addAll(updated)
        updated
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun getSelectedText(): String = realm.writeBlocking {
        selectedItems.joinToString("\n") { primary ->
            query<MessageStorageItem>("primary = '$primary'").first().find()?.body.orEmpty()
        }
    }

    fun getForwardMessagesText(): String = realm.writeBlocking {
        selectedItems.joinToString("\n") { primary ->
            val msg = query<MessageStorageItem>("primary = '$primary'").first().find() ?: return@joinToString ""
            "${if (msg.outgoing) msg.owner else msg.opponent}\n${msg.body}"
        }
    }

    fun getSelectedMessageText(): String = realm.writeBlocking {
        selectedItems.firstOrNull()?.let { primary ->
            query<MessageStorageItem>("primary = '$primary'").first().find()?.body.orEmpty()
        }.orEmpty()
    }

    fun getSelectedMessageId(): String = selectedItems.firstOrNull().orEmpty()

    // === Внутреннее обновление списка ===

    fun updateMessages(newMessages: List<MessageDto>) = messageListMutex.run {
        _messages.clear()
        _messages.addAll(newMessages)
    }

    fun getMessages(): List<MessageDto> = _messages.toList()

    fun markAsRead(primary: String) = realm.writeBlocking {
        val msg = query<MessageStorageItem>("primary = '$primary'").first().find()
        if (msg != null && !msg.isRead) {
            val managedMsg = findLatest(msg)
            managedMsg?.isRead = true

            val chat = query<LastChatsStorageItem>("primary = '$chatId'").first().find()
            val managedChat = chat?.let { findLatest(it) }
            managedChat?.unread = maxOf(0, (managedChat!!.unread) - 1)
        }
    }

    // === Освобождение ресурсов ===

    fun close() {
        if (!realm.isClosed()) realm.close()
    }
}