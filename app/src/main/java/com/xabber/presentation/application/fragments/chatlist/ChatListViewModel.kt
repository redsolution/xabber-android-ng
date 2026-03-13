package com.xabber.presentation.application.fragments.chatlist

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.dto.ChatListDto
import com.xabber.utils.applyAccountColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
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

    private var combinedJob: Job? = null

    init {
        observeChatsAndPresences()
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
        observeChatsAndPresences()          // restart with new filter
    }

    fun toggleUnreadOnly() {
        _showUnreadOnly.value = !(_showUnreadOnly.value ?: false)
        observeChatsAndPresences()
    }

    private fun observeChatsAndPresences() {
        combinedJob?.cancel()
        combinedJob = viewModelScope.launch {
            // Show snapshot immediately (avoids empty list)
            val snapshot = model.getChatsSnapshot(_showUnreadOnly.value == true)
                .applyAccountColors()
                .sortedWith(compareByDescending<ChatListDto> { it.pinnedDate }
                    .thenByDescending { it.lastMessageDate })
            _chats.value = snapshot

            // Combine live flows for updates
            combine(
                model.getChatsFlow(_showUnreadOnly.value == true),
                model.observeAllPresences()
            ) { chatList, presenceMap ->
                chatList
                    .map { dto ->
                        if (dto.isGroup) {
                            dto
                        } else {
                            val key = "${dto.owner}|${dto.opponentJid}"
                            val newStatus = presenceMap[key]?.status ?: dto.status
                            dto.copy(status = newStatus)
                        }
                    }
                    .applyAccountColors()
                    .sortedWith(compareByDescending<ChatListDto> { it.pinnedDate }
                        .thenByDescending { it.lastMessageDate })
            }.collect { list ->
                val top = list.firstOrNull()
                Log.d("ChatListVM", "combine emit: size=${list.size}, top=${top?.opponentJid}, body='${top?.lastMessageBody?.take(30)}', unread=${top?.unread}, date=${top?.lastMessageDate}")
                _chats.value = list
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
        combinedJob?.cancel()
        model.close()
        super.onCleared()
    }
}