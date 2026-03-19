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
import com.xabber.presentation.application.fragments.chat.message.appendToChatItems
import com.xabber.presentation.application.fragments.chat.message.toChatItems
import com.xabber.presentation.XabberApplication.Companion.applicationContext as appContext
import com.xabber.presentation.application.fragments.chat.view.ChatModel
import com.xabber.utils.toChatListDto
import com.xabber.xmpp.jid.XMPPJID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@RequiresApi(Build.VERSION_CODES.O)
class ChatViewModel(
    private val chatId: String,
    val owner: String,
    val opponent: String,
    val conversationType: ConversationType
) : ViewModel() {

    val isGroup = conversationType == ConversationType.Group ||
            conversationType == ConversationType.Incognito ||
            conversationType == ConversationType.Private

    private val model = ChatModel(chatId, owner, opponent, conversationType)

    private val _opponentPresence = MutableLiveData<OpponentPresence>()
    val opponentPresence: LiveData<OpponentPresence> = _opponentPresence

    private val _groupInfo = MutableLiveData<ChatModel.GroupInfo?>()
    val groupInfo: LiveData<ChatModel.GroupInfo?> = _groupInfo

    private val _chat = MutableLiveData<LastChatsStorageItem?>()
    val chat: LiveData<LastChatsStorageItem?> = _chat

    // Cached ChatListDto for synchronous access (populated from observeChat + initial load)
    @Volatile
    private var cachedChatDto: ChatListDto? = null

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

    private val _accountColorKey = MutableLiveData<String>()
    val accountColorKey: LiveData<String> = _accountColorKey

    // Для обратной совместимости (можно временно оставить)
    private val _messages = MutableLiveData<List<MessageStorageItem>>()
    val messages: LiveData<List<MessageStorageItem>> = _messages

    private val selectedItems = mutableSetOf<String>()
    private var messagesJob: Job? = null
    private var chatJob: Job? = null
    private var loadingJob: Job? = null
    private var markReadJob: Job? = null
    private val pendingMarkRead = mutableSetOf<String>()

    private val TAG = "ChatViewModel"

    // --- Incremental update infrastructure ---

    private data class MessageSignature(
        val primary: String,
        val bodyHash: Int,
        val sentDate: Long,
        val editDate: Long,
        val outgoing: Boolean,
        val state: Int
    )

    private enum class ChangeType { READ_ONLY, APPENDED, FULL }

    @Volatile
    private var cachedSignatures: List<MessageSignature> = emptyList()
    @Volatile
    private var cachedChatItemsList: List<ChatItem> = emptyList()

    private fun MessageStorageItem.toSignature() = MessageSignature(
        primary = primary,
        bodyHash = body.hashCode(),
        sentDate = sentDate,
        editDate = editDate,
        outgoing = outgoing,
        state = state_
    )

    /**
     * Compares the previous message signatures with the new message list to
     * determine what kind of change occurred:
     * - READ_ONLY: same messages, only isRead/readDate changed → skip ChatItem rebuild
     * - APPENDED: new messages added at the end → incremental append
     * - FULL: anything else (edit, delete, prepend, reorder) → full rebuild
     */
    private fun detectChangeType(
        prevSignatures: List<MessageSignature>,
        newMessages: List<MessageStorageItem>
    ): ChangeType {
        if (prevSignatures.isEmpty()) return ChangeType.FULL

        val oldSize = prevSignatures.size
        val newSize = newMessages.size

        // Same count: check if only read-status fields changed
        if (newSize == oldSize) {
            for (i in prevSignatures.indices) {
                val prev = prevSignatures[i]
                val msg = newMessages[i]
                if (prev.primary != msg.primary ||
                    prev.bodyHash != msg.body.hashCode() ||
                    prev.sentDate != msg.sentDate ||
                    prev.editDate != msg.editDate ||
                    prev.outgoing != msg.outgoing ||
                    prev.state != msg.state_) {
                    return ChangeType.FULL
                }
            }
            return ChangeType.READ_ONLY
        }

        // More messages: check if the new ones were appended at the end
        if (newSize > oldSize) {
            for (i in prevSignatures.indices) {
                if (prevSignatures[i].primary != newMessages[i].primary) {
                    return ChangeType.FULL
                }
            }
            return ChangeType.APPENDED
        }

        // Fewer messages (deletion) or other structural change
        return ChangeType.FULL
    }

    // --- End incremental update infrastructure ---

    data class OpponentPresence(val status: ResourceStatus, val statusMessage: String?)

    // LiveData that emits the ChatListDto once loaded (replaces runBlocking loadChat)
    private val _chatDto = MutableLiveData<ChatListDto?>()
    val chatDto: LiveData<ChatListDto?> = _chatDto

    init {
        loadChatDto()
        observeChat()
        observeMessages()
        loadInitialData()
        markAllAsRead()
        observeAccountColor()

        if (isGroup) {
            viewModelScope.launch {
                model.observeGroupInfo().collect { info ->
                    _groupInfo.postValue(info)
                }
            }
            refreshGroupInfo()
        } else {
            viewModelScope.launch {
                model.observeOpponentPresence().collect { presence ->
                    _opponentPresence.postValue(presence)
                }
            }
        }
    }

    /** Preload ChatListDto into cache + LiveData (non-blocking) */
    private fun loadChatDto() {
        viewModelScope.launch {
            val dto = withContext(Dispatchers.IO) { model.getChat() }
            cachedChatDto = dto
            _chatDto.value = dto
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

    /**
     * Reset archive-load state after a reconnect so the fragment can
     * re-trigger syncChat() without the counter being stuck > 0.
     * Called by ChatView when the account transitions back to online.
     */
    fun resetArchiveLoadState() {
        activeArchiveLoads.set(0)
        _isArchiveLoading.postValue(false)
    }

    private fun observeChat() {
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            model.observeChat().collectLatest { chatItem ->
                _chat.value = chatItem
                chatItem?.let {
                    _muteExpired.value = it.muteExpired
                    _opponentName.value = it.rosterItem?.displayName ?: it.jid
                    cachedChatDto = it.toChatListDto()
                }
            }
        }
    }

    private fun observeAccountColor() {
        viewModelScope.launch {
            model.observeAccountColorKey(owner).collectLatest { colorKey ->
                if (colorKey != null) _accountColorKey.postValue(colorKey)
            }
        }
    }

    fun refreshGroupInfo() {
        if (!isGroup) return
        viewModelScope.launch {
            try {
                val bareOwner = XMPPJID(fullJID = owner).bare()
                val bareOpponent = XMPPJID(fullJID = opponent).bare()
                AccountManager.find(bareOwner)?.action { acc, stream ->
                    acc.groupchatManager?.requestGroupInfo(stream, bareOpponent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request group info: ${e.message}")
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
                val prevSigs = cachedSignatures
                val prevItems = cachedChatItemsList

                val (chatItems, unread, newSigs) = withContext(Dispatchers.Default) {
                    val unread = messageList.count { !it.isRead && !it.outgoing }
                    val changeType = detectChangeType(prevSigs, messageList)
                    Log.d(TAG, "observeMessages changeType=$changeType, old=${prevSigs.size}, new=${messageList.size}")

                    when (changeType) {
                        ChangeType.READ_ONLY -> {
                            // Only isRead/readDate changed — skip ChatItem rebuild entirely.
                            // Reuse previous signatures since visual fields are identical.
                            Triple(null, unread, prevSigs)
                        }
                        ChangeType.APPENDED -> {
                            // New messages at the end — incrementally append ChatItems
                            val newSigs = messageList.map { it.toSignature() }
                            val items = appendToChatItems(prevItems, messageList, prevSigs.size, isGroup)
                            Triple(items, unread, newSigs)
                        }
                        ChangeType.FULL -> {
                            val newSigs = messageList.map { it.toSignature() }
                            val items = messageList.toChatItems(unread, isGroup)
                            Triple(items, unread, newSigs)
                        }
                    }
                }

                // Update caches
                cachedSignatures = newSigs

                // Always update backward-compat list and unread badge
                _messages.value = messageList
                _unreadCount.value = unread

                // Only push to adapter when visual content actually changed
                if (chatItems != null) {
                    cachedChatItemsList = chatItems
                    _chatItems.value = chatItems
                }
            }
        }
    }

    private fun loadInitialData() {
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            val initialMessages = model.getMessages()

            val (chatItems, unread, sigs) = withContext(Dispatchers.Default) {
                val unread = initialMessages.count { !it.isRead && !it.outgoing }
                val items = initialMessages.toChatItems(unread, isGroup)
                val sigs = initialMessages.map { it.toSignature() }
                Triple(items, unread, sigs)
            }

            cachedSignatures = sigs
            cachedChatItemsList = chatItems
            _messages.value = initialMessages
            _unreadCount.value = unread
            _chatItems.value = chatItems
            _isLoading.value = false
        }
    }

    /** Lightweight enqueue — no coroutine work, just adds to set. Use during scroll. */
    fun enqueueMarkRead(id: String) {
        synchronized(pendingMarkRead) {
            pendingMarkRead.add(id)
        }
    }

    /** Flush all enqueued mark-read IDs. Call when scroll stops. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun flushPendingMarkRead() {
        val ids: List<String>
        synchronized(pendingMarkRead) {
            if (pendingMarkRead.isEmpty()) return
            ids = pendingMarkRead.toList()
            pendingMarkRead.clear()
        }
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            model.markAsReadBatch(ids)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun markAsRead(id: String) {
        synchronized(pendingMarkRead) {
            pendingMarkRead.add(id)
        }
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            delay(500L) // batch reads within 500ms to reduce Realm writes during fast scroll
            val ids: List<String>
            synchronized(pendingMarkRead) {
                ids = pendingMarkRead.toList()
                pendingMarkRead.clear()
            }
            if (ids.isNotEmpty()) {
                model.markAsReadBatch(ids)
            }
        }
    }

    /** Returns cached ChatListDto synchronously (non-blocking, from memory cache) */
    fun getCachedChat(): ChatListDto? = cachedChatDto

    /** Async version for when you need a guaranteed fresh load */
    suspend fun loadChatAsync(): ChatListDto? = withContext(Dispatchers.IO) {
        model.getChat().also { cachedChatDto = it }
    }

    fun getMessageList(id: String) {
        viewModelScope.launch {
            val messages = model.getMessages()

            val (chatItems, unread, sigs) = withContext(Dispatchers.Default) {
                val unread = messages.count { !it.isRead && !it.outgoing }
                val items = messages.toChatItems(unread, isGroup)
                val sigs = messages.map { it.toSignature() }
                Triple(items, unread, sigs)
            }

            cachedSignatures = sigs
            cachedChatItemsList = chatItems
            _messages.value = messages
            _unreadCount.value = unread
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
        if ((_unreadCount.value ?: 0) == 0) return
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

    suspend fun getSelectedText(): String = withContext(Dispatchers.IO) { model.getSelectedText(selectedItems) }

    suspend fun getForwardMessagesText(): String = withContext(Dispatchers.IO) { model.getForwardMessagesText(selectedItems) }

    suspend fun getMessage(): MessageStorageItem? = withContext(Dispatchers.IO) { model.getSelectedMessage(selectedItems) }

    suspend fun getSelectedMessageText(): String = withContext(Dispatchers.IO) { model.getSelectedMessageText(selectedItems) }

    suspend fun getMessageId(): String = withContext(Dispatchers.IO) { model.getMessageId(selectedItems) }

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

    suspend fun lastPositionPrimary(id: String): String = withContext(Dispatchers.IO) { model.lastPositionPrimary(id) }

    suspend fun getContactId(id: String): String? = withContext(Dispatchers.IO) { model.getContactId(id) }

    suspend fun getAccount(id: String): AccountDto? = withContext(Dispatchers.IO) { model.getAccount(id) }

    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    fun updateMessagesAndUnread(messages: List<MessageStorageItem>) {
        viewModelScope.launch {
            val (chatItems, unread, sigs) = withContext(Dispatchers.Default) {
                val unread = messages.count { !it.isRead && !it.outgoing }
                val items = messages.toChatItems(unread, isGroup)
                val sigs = messages.map { it.toSignature() }
                Triple(items, unread, sigs)
            }

            cachedSignatures = sigs
            cachedChatItemsList = chatItems
            _messages.value = messages
            _unreadCount.value = unread
            _chatItems.value = chatItems
        }
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
        markReadJob?.cancel()
        model.close()
    }
}