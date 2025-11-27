package com.xabber.presentation.application.fragments.chat.viewmodel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.account.AccountManager
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.view.ChatModel
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
class ChatViewModel(
    private val chatId: String,
    val owner: String,
    val opponent: String,
    val conversationType: ConversationType
) : ViewModel(), MessageArchiveManager.TemporaryMessageReceiver {

    private val model = ChatModel(chatId, owner, opponent, conversationType)
    private var isLoadingOlderMessages = false
    private val _chat = MutableLiveData<ChatListDto?>()
    val chat: LiveData<ChatListDto?> = _chat
    private var currentOlderLoadQueryId: String? = null
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
    private val _isLoadingOlder = MutableLiveData<Boolean>(false)
    val isLoadingOlder: LiveData<Boolean> = _isLoadingOlder
    // NEW: Flag for full archive load (prevents further older message loads)
    private val _isArchiveFullyLoaded = MutableLiveData(false)
    val isArchiveFullyLoaded: LiveData<Boolean> = _isArchiveFullyLoaded

    private val selectedItems = mutableSetOf<String>()
    private var messagesJob: Job? = null
    private var chatJob: Job? = null
    private var loadingJob: Job? = null
    private val messageListMutex = Mutex()
    private var localMessageList: MutableList<MessageDto> = mutableListOf()

    // Debounce flow for rapid message inserts (prevents UI jumping)
    private val _messagesTrigger = Channel<Unit>(Channel.CONFLATED)
    @OptIn(FlowPreview::class)
    private val messagesFlow = _messagesTrigger.receiveAsFlow()
        .onStart { emit(Unit) }
        .map { localMessageList.toList() }
        .debounce(400)
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val TAG = "ChatViewModel"

    init {
        observeChat()
        initMessagesListener()
        loadInitialData()
        markAllAsRead()

        viewModelScope.launch(Dispatchers.Main) {
            messagesFlow.collect { list ->
                _messages.value = list
                _unreadCount.value = list.count { it.isUnread }
            }
        }
    }

    fun setLoadingOlder(loading: Boolean) {
        if (_isLoadingOlder.value != loading) {
            _isLoadingOlder.postValue(loading)
        }
    }

    fun initialSyncChat() {
        setLocked(true)
            viewModelScope.launch {
                val bareOwner = XMPPJID(fullJID = loadChat(chatId)!!.owner).bare()
                val bareOpponent = XMPPJID(fullJID = loadChat(chatId)!!.opponentJid).bare()
                val account = AccountManager.find(bareOwner)
                if (account != null) {
                    account.action { acc, stream ->
                        Log.d("ChatView", "Starting MAM sync for chat: owner=$chat, opponent=$bareOpponent, type=${conversationType}")
                        acc.messageArchiveManager.syncChat(
                            stream = stream,
                            jid = bareOpponent,
                            conversationType = conversationType
                        )
                    }
                } else {
                    Log.e("ChatView", "Account not found for owner=$bareOwner")
                }
            }
        setLocked(false)
    }

    override fun didReceiveEndPage(
        queryId: String,
        fin: Boolean,
        first: String,
        last: String,
        count: Int
    ) {
        if (queryId != currentOlderLoadQueryId) return

        viewModelScope.launch(Dispatchers.Main) {
            setLoadingOlder(false)  // <--- ЕДИНСТВЕННОЕ место выключения
            if (fin || count == 0) {
                setArchiveFullyLoaded(true)
            }
        }
    }

    override fun didStartPageLoad(queryId: String) {
        currentOlderLoadQueryId = queryId
        setLoadingOlder(true)
    }

    override suspend fun didReceiveMessage(item: MessageStorageItem, queryId: String) {
        // Важно: это вызывается в IO-потоке
        val messageDto = item.toMessageDto() ?: return

        withContext(Dispatchers.Main) {
            insertMessage(chatId, messageDto, fromMAM = true)
        }
    }

    fun loadOlderMessages(firstVisibleArchivedId: String? = null) {
        if (isLoadingOlderMessages || _isArchiveFullyLoaded.value == true) return

        isLoadingOlderMessages = true
        setLoadingOlder(true)

        viewModelScope.launch {
            try {
                val bareOwner = XMPPJID(fullJID = owner).bare()
                val bareOpponent = XMPPJID(fullJID = opponent).bare()
                val account = AccountManager.find(bareOwner) ?: return@launch

                // Обязательно устанавливаем receiver ДО запроса!
                account.messageArchiveManager.temporaryMessageReceiver = this@ChatViewModel

                account.action { acc, stream ->
                    acc.messageArchiveManager.getPrevHistory(
                        stream = stream,
                        jid = bareOpponent,
                        conversationType = conversationType,
                        messageId = firstVisibleArchivedId.orEmpty()
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "loadOlderMessages error", e)
                withContext(Dispatchers.Main) {
                    isLoadingOlderMessages = false
                }
            }
        }
    }



    fun setArchiveFullyLoaded(fullyLoaded: Boolean) {
        if (fullyLoaded != _isArchiveFullyLoaded.value) {
            _isArchiveFullyLoaded.postValue(fullyLoaded)
        }
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

    private fun insertIntoSortedList(list: MutableList<MessageDto>, newItem: MessageDto): Int {
        // Binary search for insertion point (O(log n))
        val timestamp = newItem.sentTimestamp
        var low = 0
        var high = list.size
        while (low < high) {
            val mid = low + (high - low) / 2
            if (list[mid].sentTimestamp < timestamp) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        // Insert at position (O(n) shift, but only once per message – amortized fine for n=1000)
        list.add(low, newItem)
        return low  // Optional: return pos for logging
    }

    fun initMessagesListener() {
        messagesJob?.cancel() // Отменяем предыдущий job, чтобы избежать дубликатов
        messagesJob = viewModelScope.launch(SupervisorJob() + Dispatchers.IO) {
            try {
                model.observeMessages()
                    .distinctUntilChanged()
                    .debounce(100)
                    .collectLatest { incomingMessages ->
                        Log.d(TAG, "Messages Flow collected: ${incomingMessages.size} messages, chatId=$chatId, opponent=$opponent")

                        messageListMutex.withLock {
                            val currentMessages = localMessageList.associateBy { it.primary }.toMutableMap()
                            var newMessagesAdded = false // Флаг для новых сообщений (можно использовать для уведомлений)

                            incomingMessages.forEach { newMessage ->
                                if (!currentMessages.containsKey(newMessage.primary)) {
                                    newMessagesAdded = true
                                }
                                currentMessages[newMessage.primary] = newMessage
                                Log.d(TAG, "Merged message from Flow: primary=${newMessage.primary}, messageId=${newMessage.archivedId}, body=${newMessage.messageBody.take(50)}, sentTimestamp=${newMessage.sentTimestamp}")
                            }

                            localMessageList.clear()
                            localMessageList.addAll(currentMessages.values.sortedBy { it.sentTimestamp })

                            // Считаем unread
                            val unreadCount = localMessageList.count { it.isUnread }
                            Log.d(TAG, "Collected messages from Flow: ${localMessageList.size} messages, $unreadCount unread, first=${localMessageList.firstOrNull()?.primary}, last=${localMessageList.lastOrNull()?.primary}, lastMessageId=${localMessageList.lastOrNull()?.archivedId}")


                            _messagesTrigger.trySend(Unit)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in messages Flow collector: ${e.message}", e)
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

    fun insertMessage(id: String, message: MessageDto, fromMAM: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            messageListMutex.withLock {
                val idx = localMessageList.indexOfFirst { it.primary == message.primary }
                if (idx == -1) {
                    insertIntoSortedList(localMessageList, message)
                } else {
                    localMessageList[idx] = message
                }
            }
            if (!fromMAM) {
                model.insertMessage(id, message)
            }
            _messagesTrigger.trySend(Unit)
            Log.d(TAG, "Inserted message ${message.primary} at pos via binary search, total size=${localMessageList.size}, fromMAM=$fromMAM")
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageDto>) {
        viewModelScope.launch {
            model.insertMessagesFromReceiver(messages)
            val refreshed = model.getMessages()
            messageListMutex.withLock {
                localMessageList.clear()
                localMessageList.addAll(refreshed.sortedBy { it.sentTimestamp })
            }
            withContext(Dispatchers.Main) {
                _unreadCount.value = localMessageList.count { it.isUnread }
                Log.d(TAG, "Force refresh after receiver insert: ${messages.size} new msgs")
            }
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