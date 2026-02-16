package com.xabber.presentation.application.fragments.chat.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.account.AccountManager
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.presentation.application.fragments.chat.message.ChatItem
import com.xabber.presentation.application.fragments.chat.message.toChatItems
import com.xabber.presentation.XabberApplication.Companion.applicationContext as appContext
import com.xabber.presentation.application.fragments.chat.view.ChatModel
import com.xabber.xmpp.jid.XMPPJID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@RequiresApi(Build.VERSION_CODES.O)
class ChatViewModel(
    private val chatId: String,
    val owner: String,
    val opponent: String,
    val conversationType: ConversationType
) : ViewModel() {

    private val model = ChatModel(chatId, owner, opponent, conversationType)

    private val _opponentPresence = MutableLiveData<OpponentPresence>()
    val opponentPresence: LiveData<OpponentPresence> = _opponentPresence


    private val _chat = MutableLiveData<LastChatsStorageItem?>()
    val chat: LiveData<LastChatsStorageItem?> = _chat

    private val activeArchiveLoads = AtomicInteger(0)
    private val _isArchiveLoading = MutableLiveData<Boolean>()
    val isArchiveLoading: LiveData<Boolean> = _isArchiveLoading

    // Убираем старый LiveData messages и добавляем chatItems
    private val _chatItems = MutableLiveData<List<ChatItem>>()
    val chatItems: LiveData<List<ChatItem>> = _chatItems

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired

    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName

    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int> = _selectedCount

    private val _isLocked = MutableLiveData<Boolean>()
    val isLocked: LiveData<Boolean> = _isLocked

    // Для обратной совместимости (можно временно оставить)
    private val _messages = MutableLiveData<List<MessageStorageItem>>()
    val messages: LiveData<List<MessageStorageItem>> = _messages

    private val selectedItems = mutableSetOf<String>()
    private var messagesJob: Job? = null
    private var chatJob: Job? = null
    private var loadingJob: Job? = null

    private val TAG = "ChatViewModel"


    data class OpponentPresence(val status: ResourceStatus, val statusMessage: String?)

    init {
        observeChat()
        observeMessages()
        loadInitialData()
        markAllAsRead()

        viewModelScope.launch {
            model.observeOpponentPresence().collect { presence ->
                _opponentPresence.postValue(presence)
            }
        }
    }

    fun startArchiveLoad() {
        val wasZero = activeArchiveLoads.getAndIncrement() == 0
        if (wasZero) {
            _isArchiveLoading.postValue(true)
        }
    }

    fun finishArchiveLoad() {
        val becameZero = activeArchiveLoads.decrementAndGet() == 0
        if (becameZero) {
            viewModelScope.launch {
                _isArchiveLoading.postValue(false)
            }
        }
    }

    private fun observeChat() {
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            model.observeChat().collectLatest { chatItem ->
                _chat.value = chatItem
                chatItem?.let {
                    _muteExpired.value = it.muteExpired
                    _opponentName.value = it.jid // or rosterItem?.customNickname if available
                }
            }
        }
    }

    fun loadOlderMessages(firstArchivedId: String?) {
        viewModelScope.launch {
            try {
                startArchiveLoad() // включаем индикатор загрузки

                val bareOwner = XMPPJID(fullJID = owner).bare()
                val bareOpponent = XMPPJID(fullJID = opponent).bare()

                val account = AccountManager.find(bareOwner)
                account?.action { acc, stream ->
                    acc.messageArchiveManager!!.getPrevHistory(
                        stream = stream,
                        jid = bareOpponent,
                        conversationType = conversationType,
                        messageId = firstArchivedId ?: "",
                        callback = {
                            viewModelScope.launch(Dispatchers.Main) {
                                delay(2250L)
                                finishArchiveLoad() // выключаем индикатор
                            }
                        }
                    )
                } ?: Log.e("ChatViewModel", "Account not found for owner=$bareOwner")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading older messages", e)
                finishArchiveLoad()
            }
        }
    }

    private fun observeMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            model.observeMessages().collectLatest { messageList ->
                // Сохраняем для обратной совместимости
                _messages.value = messageList

                // Вычисляем количество непрочитанных
                val unread = messageList.count { !it.isRead && !it.outgoing }
                _unreadCount.value = unread

                // Преобразуем в ChatItems
                val chatItems = messageList.toChatItems(unread)
                _chatItems.value = chatItems

                Log.d(TAG, "Observed ${messageList.size} messages -> ${chatItems.size} chat items")
            }
        }
    }

    private fun loadInitialData() {
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            val initialMessages = model.getMessages()

            // Для обратной совместимости
            _messages.value = initialMessages

            val unread = initialMessages.count { !it.isRead && !it.outgoing }
            _unreadCount.value = unread

            // Преобразуем в ChatItems
            val chatItems = initialMessages.toChatItems(unread)
            _chatItems.value = chatItems

            _isLoading.value = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun markAsRead(id: String) {
        viewModelScope.launch { model.markAsRead(id) }
    }

    fun loadChat(id: String): ChatListDto? = runBlocking { model.getChat() }

    fun getMessageList(id: String) {
        viewModelScope.launch {
            val messages = model.getMessages()

            // Для обратной совместимости
            _messages.value = messages

            val unread = messages.count { !it.isRead && !it.outgoing }
            _unreadCount.value = unread

            // Преобразуем в ChatItems
            val chatItems = messages.toChatItems(unread)
            _chatItems.value = chatItems
        }
    }

    fun insertMessage(id: String, message: MessageStorageItem) {
        viewModelScope.launch {
            model.insertMessage(id, message)
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageStorageItem>) {
        viewModelScope.launch {
            model.insertMessagesFromReceiver(messages)
        }
    }

    fun deleteMessage(primary: String, forAll: Boolean = false) {
        viewModelScope.launch { model.deleteMessage(primary, forAll) }
    }

    fun deleteMessages(forAll: Boolean = false) {
        viewModelScope.launch {
            model.deleteMessages(selectedItems, forAll)
            clearAllSelected()
        }
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch { model.editMessage(primary, newBody) }
    }

    fun setMute(id: String, mute: Long) {
        viewModelScope.launch { model.setMute(id, mute) }
    }

    fun setUnread(id: String) {
        viewModelScope.launch { model.setUnread(id) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun markAllAsRead() {
        viewModelScope.launch {
            model.markAllAsRead(chatId)
            NotificationManagerCompat.from(appContext()).cancel(chatId.hashCode())
        }
    }

    fun saveDraft(id: String, draft: String?) {
        viewModelScope.launch { model.saveDraft(id, draft) }
    }

    fun saveLastPosition(id: String, position: String) {
        viewModelScope.launch { model.saveLastPosition(id, position) }
    }

    fun clearHistory(id: String, opponentJid: String) {
        viewModelScope.launch { model.clearHistory(id, opponentJid) }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch { model.deleteChat(id) }
    }

    fun insertChat(id: String) {
        viewModelScope.launch { model.insertChat(id) }
    }

    fun selectMessage(primary: String, checked: Boolean) {
        if (checked) selectedItems.add(primary)
        else selectedItems.remove(primary)

        _selectedCount.value = selectedItems.size
    }

    fun clearAllSelected() {
        selectedItems.clear()
        _selectedCount.value = 0
    }

    suspend fun getOldestMessageId(): String? = withContext(Dispatchers.IO) {
        model.getOldestMessageId()
    }

    suspend fun isOutgoing(): Boolean = withContext(Dispatchers.IO) {
        selectedItems.size == 1 && model.isOutgoing(selectedItems)
    }

    fun getSelectedText(): String = runBlocking { model.getSelectedText(selectedItems) }

    fun getForwardMessagesText(): String = runBlocking { model.getForwardMessagesText(selectedItems) }

    fun getMessage(): MessageStorageItem? = runBlocking { model.getSelectedMessage(selectedItems) }

    fun getSelectedMessageText(): String = runBlocking { model.getSelectedMessageText(selectedItems) }

    fun getMessageId(): String = runBlocking { model.getMessageId(selectedItems) }

    // Обновляем метод для работы с ChatItems
    fun getMessagePosition(primary: String): Int {
        val items = _chatItems.value ?: return -1
        return items.indexOfFirst {
            it is ChatItem.MessageItem && it.message.primary == primary
        }
    }

    fun getPositionMessage(lastPosition: String): Int {
        val items = _chatItems.value ?: return 0
        return items.indexOfFirst {
            it is ChatItem.MessageItem && it.message.primary == lastPosition
        }
    }

    fun lastPositionPrimary(id: String): String = runBlocking { model.lastPositionPrimary(id) }

    fun getContactId(id: String): String? = runBlocking { model.getContactId(id) }

    fun getAccount(id: String): AccountDto? = runBlocking { model.getAccount(id) }

    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    fun updateMessagesAndUnread(messages: List<MessageStorageItem>) {
        // Для обратной совместимости
        _messages.value = messages

        val unread = messages.count { !it.isRead && !it.outgoing }
        _unreadCount.value = unread

        // Преобразуем в ChatItems
        val chatItems = messages.toChatItems(unread)
        _chatItems.value = chatItems
    }

    // Новый метод для получения MessageStorageItem по position из ChatItems
    fun getMessageItemByPosition(position: Int): MessageStorageItem? {
        val items = _chatItems.value ?: return null
        if (position !in 0 until items.size) return null

        return when (val item = items[position]) {
            is ChatItem.MessageItem -> item.message
            else -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        chatJob?.cancel()
        loadingJob?.cancel()
        model.close()
    }
}