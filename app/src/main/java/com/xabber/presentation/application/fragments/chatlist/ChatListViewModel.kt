package com.xabber.presentation.application.fragments.chatlist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.account.AccountManager
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.dto.ChatListDto
import com.xabber.presentation.application.fragments.chatlist.view.ChatListModel
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatListViewModel : ViewModel() {

    private val model = ChatListModel()
    private val mutex = Mutex()
    private var localChatList: List<ChatListDto> = emptyList()

    private val _chats = MutableLiveData<List<ChatListDto>>()
    val chats: LiveData<List<ChatListDto>> = _chats

    private val _selectedChatId = MutableLiveData<String?>()
    val selectedChatId: LiveData<String?> = _selectedChatId

    private val _showUnreadOnly = MutableLiveData(false)
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    private var chatsJob: Job? = null
    private var accountsJob: Job? = null

    // Дебонс как в ChatViewModel
    private val updateTrigger = Channel<Unit>(Channel.CONFLATED)
    private val updateFlow = updateTrigger.receiveAsFlow()
        .onStart { emit(Unit) }
        .debounce(150)
        .map { localChatList }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    init {
        observeEnabledAccounts()
        observeChats()
        viewModelScope.launch(Dispatchers.Main) {
            updateFlow.collect { list ->
                _chats.value = list
            }
        }
    }

    // Используем Realm Flow напрямую — как у тебя в ChatViewModel
    private fun observeEnabledAccounts() {
        accountsJob?.cancel()
        accountsJob = viewModelScope.launch(Dispatchers.IO) {
            Realm.open(com.xabber.data_base.defaultRealmConfig())
                .query<com.xabber.data_base.models.account.AccountStorageItem>("enabled = true")
                .asFlow()
                .collect { changes ->
                    when (changes) {
                        is UpdatedResults -> {
                            val ownerIds = changes.list.map { it.jid }.toSet()
                            if (ownerIds.isEmpty()) {
                                mutex.withLock {
                                    localChatList = emptyList()
                                    updateTrigger.trySend(Unit)
                                }
                            } else {
                                observeChats(ownerIds)
                            }
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun observeChats(ownerIds: Set<String> = emptySet()) {
        chatsJob?.cancel()
        chatsJob = viewModelScope.launch(Dispatchers.IO) {
            val effectiveOwners = if (ownerIds.isNotEmpty()) ownerIds else getEnabledOwnerIdsBlocking()
            val unreadOnly = _showUnreadOnly.value == true

            model.observeChats(unreadOnly, effectiveOwners)
                .catch { Log.e("ChatListVM", "Error observing chats", it) }
                .collect { incomingList ->
                    mutex.withLock {
                        localChatList = incomingList.map { chat ->
                            val colorKey = AccountManager.getAccount(chat.owner)?.colorKey ?: "blue"
                            chat.copy(colorKey = colorKey)
                        }
                        updateTrigger.trySend(Unit)
                    }
                }
        }
    }

    private fun getEnabledOwnerIdsBlocking(): Set<String> {
        return runBlocking(Dispatchers.IO) {
            Realm.open(com.xabber.data_base.defaultRealmConfig())
                .query<com.xabber.data_base.models.account.AccountStorageItem>("enabled = true")
                .find()
                .map { it.jid }
                .toSet()
        }
    }

    fun selectChat(id: String?) {
        _selectedChatId.value = id
    }

    fun toggleUnreadOnly() {
        _showUnreadOnly.value = !_showUnreadOnly.value!!
        observeChats() // перезапускаем
    }

    fun isSavedHas(ownerJid: String): Boolean {
        return runBlocking(Dispatchers.IO) {
            Realm.open(com.xabber.data_base.defaultRealmConfig())
                .query<com.xabber.data_base.models.last_chats.LastChatsStorageItem>(
                    "owner = $0 AND jid = $1 AND conversationType_ = $2",
                    ownerJid, ownerJid, com.xabber.data_base.models.sync.ConversationType.Regular.rawValue
                )
                .first()
                .find() != null
        }
    }

    fun getChatList() {
        viewModelScope.launch(Dispatchers.IO) {
            observeChats()
        }
    }

    fun forwardMessage(chatId: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val realm = Realm.open(com.xabber.data_base.defaultRealmConfig())
                realm.write {
                    val lastChat = query<com.xabber.data_base.models.last_chats.LastChatsStorageItem>(
                        "primary = '$chatId'"
                    ).first().find() ?: return@write

                    val newMessageTimestamp = System.currentTimeMillis()
                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = com.xabber.data_base.models.messages.MessageStorageItem.genPrimary("forward_${newMessageTimestamp}", lastChat.owner)
                        owner = lastChat.owner
                        opponent = lastChat.jid
                        body = text
                        date = newMessageTimestamp
                        sentDate = newMessageTimestamp
                        outgoing = true
                        conversationType_ = lastChat.conversationType_
                        isRead = true
                        state = com.xabber.data_base.models.messages.MessageSendingState.Sent
                    })

                    findLatest(lastChat)?.apply {
                        lastMessage = message
                        lastMessageId = message.messageId
                        messageDate = newMessageTimestamp
                        unread = 0
                    }
                }
                realm.close()
            } catch (e: Exception) {
                Log.e("ChatListVM", "Failed to forward message", e)
            }
        }
    }

    fun pinChat(id: String) = viewModelScope.launch(Dispatchers.IO) { model.pinChat(id) }
    fun unpinChat(id: String) = viewModelScope.launch(Dispatchers.IO) { model.unpinChat(id) }
    fun archiveChat(id: String) = viewModelScope.launch(Dispatchers.IO) { model.setArchived(id) }
    fun muteChat(id: String, until: Long) = viewModelScope.launch(Dispatchers.IO) { model.setMute(id, until) }
    fun deleteChat(id: String) = viewModelScope.launch(Dispatchers.IO) { model.deleteChat(id) }
    fun markAllAsRead() = viewModelScope.launch(Dispatchers.IO) { model.markAllAsRead() }

    override fun onCleared() {
        super.onCleared()
        chatsJob?.cancel()
        accountsJob?.cancel()
        model.close()
    }
}