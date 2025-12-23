package com.xabber.presentation.application.fragments.chat.viewmodel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.presentation.application.fragments.chat.view.ChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ChatViewModel(
    private val chatId: String,
    val owner: String,
    val opponent: String,
    val conversationType: ConversationType
) : ViewModel() {

    private val model = ChatModel(chatId, owner, opponent, conversationType)

    private val _chat = MutableLiveData<LastChatsStorageItem?>()
    val chat: LiveData<LastChatsStorageItem?> = _chat

    private val activeArchiveLoads = AtomicInteger(0)
    private val _isArchiveLoading = MutableLiveData<Boolean>()
    val isArchiveLoading: LiveData<Boolean> = _isArchiveLoading

    private val _messages = MutableLiveData<List<MessageStorageItem>>()
    val messages: LiveData<List<MessageStorageItem>> = _messages

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

    private val selectedItems = mutableSetOf<String>()
    private var messagesJob: Job? = null
    private var chatJob: Job? = null
    private var loadingJob: Job? = null

    private val TAG = "ChatViewModel"

    init {
        observeChat()
        observeMessages()
        loadInitialData()
        markAllAsRead()
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
                delay(600)
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

    private fun observeMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            model.observeMessages().collectLatest { messageList ->
                _messages.value = messageList
                _unreadCount.value = messageList.count { !it.isRead && !it.outgoing }
                Log.d(TAG, "Observed ${messageList.size} messages")
            }
        }
    }

    private fun loadInitialData() {
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            val initialMessages = model.getMessages()
            _messages.value = initialMessages
            _unreadCount.value = initialMessages.count { !it.isRead && !it.outgoing }
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
            _messages.value = messages
            _unreadCount.value = messages.count { !it.isRead && !it.outgoing }
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
        viewModelScope.launch { model.markAllAsRead(chatId) }
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

        // No need to update list items for isSelected/isChecked since we removed those fields
        // Selection state is managed separately in adapter via extra data
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

    fun getMessagePosition(primary: String): Int = _messages.value?.indexOfFirst { it.primary == primary } ?: -1

    fun getPositionMessage(lastPosition: String): Int = _messages.value?.indexOfFirst { it.primary == lastPosition } ?: 0

    fun lastPositionPrimary(id: String): String = runBlocking { model.lastPositionPrimary(id) }

    fun getContactId(id: String): String? = runBlocking { model.getContactId(id) }

    fun getAccount(id: String): AccountDto? = runBlocking { model.getAccount(id) }


    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    fun updateMessagesAndUnread(messages: List<MessageStorageItem>) {
        _messages.value = messages
        _unreadCount.value = messages.count { !it.isRead && !it.outgoing }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        chatJob?.cancel()
        loadingJob?.cancel()
        model.close()
    }
}