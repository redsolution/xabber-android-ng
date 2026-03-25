package com.xabber.presentation.application.fragments.chatlist

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.dto.ChatListDto
import com.xabber.utils.applyAccountColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel : ViewModel() {

    private val model = ChatListModel()
    private val _chats = MutableLiveData<List<ChatListDto>>()
    val chats: LiveData<List<ChatListDto>> = _chats

    private val _selectedChatId = MutableLiveData<String?>()
    val selectedChatId: LiveData<String?> = _selectedChatId

    private val _showUnreadOnly = MutableLiveData(false)
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    private var chatsJob: Job? = null
    private var enrichmentJob: Job? = null
    private var initialLoadStarted = false
    private var enrichmentStarted = false
    private var initialPlainListSubmitted = false
    private var pendingEnrichmentStart = false
    private val startupMs = SystemClock.elapsedRealtime()

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
        restartActiveLoad()
    }

    fun toggleUnreadOnly() {
        _showUnreadOnly.value = !(_showUnreadOnly.value ?: false)
        restartActiveLoad()
    }

    fun startInitialLoad(force: Boolean = false) {
        if (initialLoadStarted && !force) return
        initialLoadStarted = true
        chatsJob?.cancel()
        chatsJob = viewModelScope.launch {
            val snapshot = model.getChatsSnapshot(_showUnreadOnly.value == true)
                .applyAccountColors()
                .sortedWith(compareByDescending<ChatListDto> { it.pinnedDate }
                    .thenByDescending { it.lastMessageDate })
            _chats.value = snapshot
            initialPlainListSubmitted = true
            logStartup("snapshot submitted size=${snapshot.size}")
            maybeStartPendingEnrichment()

            model.getChatsFlow(_showUnreadOnly.value == true)
                .map { chatList ->
                    chatList
                        .applyAccountColors()
                        .sortedWith(compareByDescending<ChatListDto> { it.pinnedDate }
                            .thenByDescending { it.lastMessageDate })
                }
                .collect { list ->
                    _chats.value = list
                    initialPlainListSubmitted = true
                    logStartup("plain list submitted size=${list.size}")
                    maybeStartPendingEnrichment()
                }
        }
    }

    fun startEnrichment(force: Boolean = false) {
        if (enrichmentStarted && !force) return
        if (!initialPlainListSubmitted && !force) {
            pendingEnrichmentStart = true
            return
        }
        enrichmentStarted = true
        pendingEnrichmentStart = false
        startInitialLoad(force = force)
        chatsJob?.cancel()
        enrichmentJob?.cancel()
        enrichmentJob = viewModelScope.launch {
            logStartup("enrichment started")
            model.getChatsFlow(_showUnreadOnly.value == true)
                .flatMapLatest { chatList: List<ChatListDto> ->
                    model.observePresencesForChats(chatList).map { presenceMap: Map<String, ChatListModel.ContactPresence> ->
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
                    }
                }
                .collect { list ->
                    _chats.value = list
                    logStartup("enriched list submitted size=${list.size}")
                }
        }
    }

    private fun restartActiveLoad() {
        initialPlainListSubmitted = false
        pendingEnrichmentStart = false
        when {
            enrichmentStarted -> startEnrichment(force = true)
            initialLoadStarted -> startInitialLoad(force = true)
        }
    }

    private fun maybeStartPendingEnrichment() {
        if (pendingEnrichmentStart && !enrichmentStarted) {
            startEnrichment(force = true)
        }
    }

    private fun logStartup(message: String) {
        Log.d("ChatListVM", "startup[ChatListViewModel] ${SystemClock.elapsedRealtime() - startupMs}ms $message")
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
        chatsJob?.cancel()
        enrichmentJob?.cancel()
        model.close()
        super.onCleared()
    }
}
