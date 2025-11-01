package com.xabber.presentation.application.fragments.chat.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.view.ChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val chatId: String,
    val owner: String,
    val opponent: String,
    val conversationType: ConversationType
) : ViewModel() {

    private val model = ChatModel(chatId, owner, opponent, conversationType)

    private val _chat = MutableLiveData<ChatListDto?>()
    val chat: LiveData<ChatListDto?> = _chat

    private val _messages = MutableLiveData<List<MessageDto>>()
    val messages: LiveData<List<MessageDto>> = _messages

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

    private fun observeChat() {
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            model.observeChat().collectLatest { chatDto ->
                _chat.value = chatDto
                chatDto?.let {
                    _muteExpired.value = it.muteExpired
                    _opponentName.value = it.getChatName()
                }
            }
        }
    }

    private fun observeMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            model.observeMessages()
                .debounce(400L)
                .collectLatest { messageList ->
                    Log.d(TAG, "Emit: size=${messageList.size}, newest primary=${messageList.lastOrNull()?.primary}")
                    _messages.value = messageList
                    _unreadCount.value = messageList.count { it.isUnread }
                }
        }
    }

    private fun loadInitialData() {
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            val initialMessages = model.getMessages()
            _messages.value = initialMessages
            _unreadCount.value = initialMessages.count { it.isUnread }
            markAsReadOnLoad(initialMessages)  // Mark unread on initial load to prevent bind-loop
            _isLoading.value = false
        }
    }

    private suspend fun markAsReadOnLoad(messages: List<MessageDto>) {
        val unreadIds = messages.filter { it.isUnread && !it.isOutgoing }.map { it.primary }
        unreadIds.forEach { model.setUnread(it) }
    }

    fun loadChat(id: String): ChatListDto? = runBlocking { model.getChat() }

    fun getMessageList(id: String) {
        viewModelScope.launch {
            val messages = model.getMessages()
            _messages.value = messages
            _unreadCount.value = messages.count { it.isUnread }
        }
    }

    fun insertMessage(id: String, message: MessageDto) {
        viewModelScope.launch {
            model.insertMessage(id, message)
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageDto>) {
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

    fun markAllAsRead() {
        viewModelScope.launch { model.markAllMessageUnread(chatId) }
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
        viewModelScope.launch(Dispatchers.IO) {
            if (checked) {
                selectedItems.add(primary)
            } else {
                selectedItems.remove(primary)
            }

            // Обновляем список сообщений, чтобы отразить выбор
            _messages.value?.let { currentMessages ->
                val updatedMessages = currentMessages.map { msg ->
                    msg.copy(
                        isSelected = selectedItems.contains(msg.primary),
                        isChecked = selectedItems.contains(msg.primary)
                    )
                }

                withContext(Dispatchers.Main) {
                    _selectedCount.value = selectedItems.size
                    _messages.value = updatedMessages
                }
            }
        }
    }
    fun clearAllSelected() {
        selectedItems.clear()
        _selectedCount.value = 0
        _messages.value = _messages.value?.map { it.copy(isSelected = false, isChecked = false) }
    }

    fun isOutgoing(): Boolean = selectedItems.size == 1 && runBlocking { model.isOutgoing(selectedItems) }

    fun getSelectedText(): String = runBlocking { model.getSelectedText(selectedItems) }

    fun getForwardMessagesText(): String = runBlocking { model.getForwardMessagesText(selectedItems) }

    fun getMessage(): MessageDto? = runBlocking { model.getSelectedMessage(selectedItems) }

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

    fun updateMessagesAndUnread(messages: List<MessageDto>) {
        _messages.value = messages
        _unreadCount.value = messages.count { it.isUnread }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        chatJob?.cancel()
        loadingJob?.cancel()
        model.close()
    }
}