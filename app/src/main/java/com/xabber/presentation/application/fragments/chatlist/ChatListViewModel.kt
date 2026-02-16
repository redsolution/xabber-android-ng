package com.xabber.presentation.application.fragments.chatlist

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.dto.ChatListDto
import com.xabber.utils.applyAccountColors
import com.xabber.utils.toChatListDto
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class ChatListViewModel : ViewModel() {

    private val model = ChatListModel()
    private val _chats = MutableLiveData<List<ChatListDto>>()
    val chats: LiveData<List<ChatListDto>> = _chats

    private val _selectedChatId = MutableLiveData<String?>()
    val selectedChatId: LiveData<String?> = _selectedChatId

    private val _showUnreadOnly = MutableLiveData(false)
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    private var lastEmittedList: List<ChatListDto>? = null
    private var collectionJob: Job? = null

    private var chatsJob: Job? = null

    init {
        updateChatList()
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
        updateChatList()
    }

    fun toggleUnreadOnly() {
        _showUnreadOnly.value = !(_showUnreadOnly.value ?: false)
        updateChatList()
    }

    private fun updateChatList() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            val showUnread = _showUnreadOnly.value ?: false

            // Create flows
            val chatsFlow = model.getChatsFlow(showUnread)
            val resourcesFlow = model.observeAllResources()

            // Combine: re-emit when either chats or resources change
            combine(chatsFlow, resourcesFlow) { chats, _ -> chats }
                .debounce(300L) // avoid too many updates
                .collectLatest { list ->
                    val processed = list
                        .applyAccountColors()
                        .sortedWith(
                            compareByDescending<ChatListDto> { it.pinnedDate }
                                .thenByDescending { it.lastMessageDate }
                        )
                    if (processed != lastEmittedList) {
                        lastEmittedList = processed
                        _chats.postValue(processed)
                    }
                }
            // Первичная загрузка
            viewModelScope.launch {
                val snapshotList = model.getChatsSnapshot(_showUnreadOnly.value == true)
                    .applyAccountColors()
                    .sortedWith(compareByDescending<ChatListDto> { it.pinnedDate }
                        .thenByDescending { it.lastMessageDate })  // Fixed: Same for snapshot
                _chats.value = snapshotList
            }
        }
    }



    fun selectChat(chatId: String) {
        _selectedChatId.value = chatId
    }

    fun pinChat(chatId: String) = viewModelScope.launch { model.pinChat(chatId) }
    fun unpinChat(chatId: String) = viewModelScope.launch { model.unpinChat(chatId) }
    fun archiveChat(chatId: String) = viewModelScope.launch { model.archiveChat(chatId) }
    fun deleteChat(chatId: String) = viewModelScope.launch { model.deleteChat(chatId) }
    fun muteChat(chatId: String, until: Long) = viewModelScope.launch { model.muteChat(chatId, until) }
    fun markAllAsRead() = viewModelScope.launch { model.markAllAsRead() }

    override fun onCleared() {
        model.close()
        collectionJob?.cancel()
        super.onCleared()
    }
}