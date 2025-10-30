package com.xabber.presentation.application.fragments.chat.chatmodel

import android.util.Log
import androidx.lifecycle.*
import androidx.recyclerview.widget.DiffUtil
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

class ChatFragmentModel(
    private val chatId: String,
    private val owner: String,
    private val opponent: String,
    private val conversationType: ConversationType
) : ViewModel() {

    private val chatModel = ChatModel(chatId, owner, opponent, conversationType)
    private val realm: Realm = Realm.open(defaultRealmConfig())
    private lateinit var messageAdapter: MessageAdapter
    // === LiveData ===
    private val _messages = MutableLiveData<List<MessageDto>>()
    val messages: LiveData<List<MessageDto>> = _messages

    private val _chat = MutableLiveData<ChatListDto?>()
    val chat: LiveData<ChatListDto?> = _chat

    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired

    private val _selectedCount = MutableLiveData<Int>(0)
    val selectedCount: LiveData<Int> = _selectedCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isLoadingHistory = MutableLiveData<Boolean>(false)
    val isLoadingHistory: LiveData<Boolean> = _isLoadingHistory

    private var observingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // === Инициализация ===
    init {
        loadInitialData()
        startRealmObservation()
        startChatDataListener()
    }

    fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            val chatDto = chatModel.getChat()
            withContext(Dispatchers.Main) {
                _chat.value = chatDto
                _opponentName.value = chatDto?.getChatName() ?: ""
                _muteExpired.value = chatDto?.muteExpired ?: 0L
                _unreadCount.value = chatDto?.unread!!.toInt() ?: 0
            }

            val messages = chatModel.loadMessages().filterNotNull()
            chatModel.updateMessages(messages)
            withContext(Dispatchers.Main) {
                _messages.value = messages
            }
        }
    }

    fun setMessageAdapter(adapter: MessageAdapter) {
        this.messageAdapter = adapter
    }

    private fun startChatDataListener() {
        val request = realm.query(LastChatsStorageItem::class, "primary = '$chatId'").find()
        val flow = request.asFlow()

        observingJob = scope.launch {
            flow.collect { changes ->
                when (changes) {
                    is UpdatedResults -> {
                        val chatItem = changes.list.firstOrNull()
                        withContext(Dispatchers.Main) {
                            _chat.value = chatItem?.toChatListDto()
                            _opponentName.value = chatItem?.toChatListDto()?.getChatName() ?: ""
                            _muteExpired.value = chatItem?.muteExpired ?: 0L
                            _unreadCount.value = chatItem?.unread ?: 0
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startRealmObservation() {
        val query = realm.query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
            owner, opponent, conversationType.rawValue
        ).sort("sentDate", io.realm.kotlin.query.Sort.ASCENDING)

        scope.launch {
            query.asFlow()
                .debounce(300.milliseconds)
                .collect { changes ->
                    val newDtos = when (changes) {
                        is io.realm.kotlin.notifications.InitialResults -> changes.list
                        is UpdatedResults -> changes.list
                        else -> return@collect
                    }.mapNotNull { it.toMessageDto() }
                        .distinctBy { it.primary }
                        .sortedBy { it.sentTimestamp }

                    withContext(Dispatchers.Main) {
                        chatModel.updateMessages(newDtos)
                        _messages.value = newDtos
                        // Автоматически обновит RecyclerView
                        messageAdapter.submitList(newDtos)
                    }
                }
        }
    }

    // === Делегирование в ChatModel ===
    suspend fun selectMessage(primary: String, checked: Boolean) {
        val count = chatModel.selectMessage(primary, checked)
        _selectedCount.value = count
        _messages.value = chatModel.getMessages()
    }

    fun clearSelection() {
        val updated = chatModel.clearAllSelected()
        _selectedCount.value = 0
        _messages.value = updated
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatModel.editMessage(primary, newBody)
        }
    }

    fun deleteMessage(primary: String, forAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            chatModel.deleteMessage(primary, forAll)
        }
    }

    fun deleteSelectedMessages(forAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = chatModel.getMessages().filter { it.isChecked }.map { it.primary }
            chatModel.deleteMessages(selected, forAll)
        }
    }

    fun setMute(mute: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            chatModel.setMute(mute)
        }
    }

    fun saveDraft(draft: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            chatModel.saveDraft(draft)
        }
    }

    fun saveLastPosition(primary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatModel.saveLastPosition(primary)
        }
    }

    fun getDraft(): String? = chatModel.getChat()?.draftMessage

    fun getContactId(id: String): String? = chatModel.getContactId(id)

    fun getAccountJid(): String = chatModel.getAccount(owner)?.jid.orEmpty()

    fun getAccountColor(): String = chatModel.getAccount(owner)?.colorKey.orEmpty()

    // === UI Helpers ===
    fun getMessageAt(position: Int): MessageDto? = _messages.value?.getOrNull(position)

    fun getMessageCount(): Int = _messages.value?.size ?: 0

    fun getFirstUnreadPosition(): Int? {
        val unreadCount = _unreadCount.value ?: return null
        if (unreadCount == 0) return null
        return _messages.value?.indexOfFirst { it.isUnread }?.takeIf { it >= 0 }
    }

    fun getSelectedText(): String = chatModel.getSelectedText()
    fun getForwardText(): String = chatModel.getForwardMessagesText()
    fun getSelectedMessage(): MessageDto? = chatModel.getMessages().find { it.isChecked }
    fun getSelectedMessageId(): String = chatModel.getSelectedMessageId()

    // === История ===
    fun loadOlderMessages() {
        if (_isLoadingHistory.value == true) return
        _isLoadingHistory.value = true
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Реализовать MAM-запрос через AccountManager
            // Временно — просто снимаем флаг
            delay(1000)
            withContext(Dispatchers.Main) {
                _isLoadingHistory.value = false
                _isLoading.value = false
            }
        }
    }

    // === Sync ===
    fun startSyncIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Синхронизация MAM
        }
    }

    // === Очистка ===
    override fun onCleared() {
        observingJob?.cancel()
        scope.cancel()
        chatModel.close()
        realm.close()
        super.onCleared()
    }


    // === Интерфейсы ===
    interface MenuItemListener {
        fun copyText(text: String)
        fun pinMessage(messageDto: MessageDto)
        fun forwardMessage(messageDto: MessageDto)
        fun replyMessage(messageDto: MessageDto)
        fun deleteMessage(primary: String)
        fun editMessage(primary: String, text: String)
    }

    interface OnViewClickListener {
        suspend fun onLongClick(primary: String)
        suspend fun checkItem(isChecked: Boolean, primary: String)
        fun onImageOrVideoClick(startPosition: Int, messageId: String)
        fun onLocationClick(latitude: Double, longitude: Double)
    }
}


