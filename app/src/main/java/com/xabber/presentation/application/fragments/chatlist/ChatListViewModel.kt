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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
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

    private var chatsJob: Job? = null

    init {
        updateChatList()
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
        updateChatList()
    }

    private fun updateChatList() {
        chatsJob?.cancel()
        chatsJob = viewModelScope.launch {
            model.getChatsFlow(_showUnreadOnly.value == true).collectLatest { changes ->
                if (changes is io.realm.kotlin.notifications.UpdatedResults) {
                    val list = changes.list
                        .map { it.toChatListDto() }
                        .applyAccountColors()  // Теперь работает!
                    _chats.postValue(list)
                }
            }
        }
        // Первичная загрузка
        viewModelScope.launch {
            _chats.value = model.getChatsSnapshot(_showUnreadOnly.value == true)
                .applyAccountColors()  // И здесь тоже
        }
    }

    fun toggleUnreadOnly() {
        _showUnreadOnly.value = !(_showUnreadOnly.value ?: false)
        updateChatList() // ← обязательно! чтобы перезапустить flow с новым фильтром
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
        super.onCleared()
    }
}