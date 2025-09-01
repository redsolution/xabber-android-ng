package com.xabber.presentation.application.fragments.chat

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.common.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.utils.observeMessages
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import com.xabber.utils.toMessageReferenceDto
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import com.xabber.xmpp.messages.message_archive.Page
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.O)
class ChatViewModel(
    private val chatId: String,
    private val owner: String,
    private val opponent: String,
    private val conversationType: ConversationType
) : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())

    private val _chat = MutableLiveData<ChatListDto?>()
    val chat: LiveData<ChatListDto?> = _chat

    private val _messages = MutableLiveData<List<MessageDto>>()
    val messages: LiveData<List<MessageDto>> = _messages

    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired

    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int> = _selectedCount

    private val _showSkeletonObserver = MutableLiveData<Boolean>()
    val showSkeletonObserver: LiveData<Boolean> = _showSkeletonObserver

    private val _searchTextObserver = MutableLiveData<String>()
    val searchTextObserver: LiveData<String> = _searchTextObserver

    private val _inSearchMode = MutableLiveData<Boolean>()
    val inSearchMode: LiveData<Boolean> = _inSearchMode

    private val _canUnpinMessage = MutableLiveData<Boolean>()
    val canUnpinMessage: LiveData<Boolean> = _canUnpinMessage

    private val TAG = "ChatViewModel"
    private var messageList = ArrayList<MessageDto>()
    private val selectedItems = HashSet<String>()
    private var count = 0
    private val datasourcePageSize = 50
    private var messagesObserver: RealmResults<MessageStorageItem>? = null
    private var unreadMessagePositionId: Int? = null
    private val currentPage = Page(minIndex = 0, maxIndex = datasourcePageSize)
    private var pageLoadingCompleted = false // New flag to track page loading completion
    private val loadingMutex = Mutex()
    private var isLoadingHistory = false
    private var messageArchiveManager: MessageArchiveManager? = null
    private var loadingTimeoutJob: Job? = null
    private var job: Job? = null
    private val activeQueries = mutableSetOf<String>() // Tracks active MAM query IDs

    init {
        messageArchiveManager = MessageArchiveManager(owner).apply {
            temporaryMessageReceiver = object : MessageArchiveManager.TemporaryMessageReceiver {
                override suspend fun didReceiveMessage(item: MessageStorageItem, queryId: String) {
                    viewModelScope.launch(Dispatchers.IO) {
                        updateMessages()
                    }
                }

                override fun didReceiveEndPage(queryId: String, fin: Boolean, first: String, last: String, count: Int) {
                    viewModelScope.launch(Dispatchers.IO) {
                        loadingMutex.withLock {
                            activeQueries.remove(queryId) // Remove completed query
                            pageLoadingCompleted = fin || count == 0 // Mark page as complete
                            if (activeQueries.isEmpty()) { // Only hide progress bar if no queries are active
                                isLoadingHistory = false
                                _isLoading.postValue(false)
                                loadingTimeoutJob?.cancel()
                                Log.d(TAG, "Hiding ProgressBar: queryId=$queryId, fin=$fin, count=$count, activeQueries=$activeQueries")
                            } else {
                                Log.d(TAG, "Keeping ProgressBar visible: queryId=$queryId, remaining activeQueries=$activeQueries")
                            }
                        }
                    }
                }
            }
        }
        initChatDataListener(chatId)
        initMessagesListener(owner, opponent)
        markAllMessageUnread(chatId)
        viewModelScope.launch(Dispatchers.IO) {
            delay(500L)
            loadInitialData()
        }
    }

    private suspend fun loadLastMessage(): List<MessageDto> = withContext(Dispatchers.IO) {
        val chat = realm.query<LastChatsStorageItem>("primary = $0", chatId).first().find()
        val lastMessage = chat?.lastMessage
        if (lastMessage != null && !lastMessage.isDeleted) {
            val dto = mapMessageStorageItemToDto(lastMessage)
            if (dto != null && conversationType == ConversationType.Regular) {
                val correctedDto = dto.copy(isOutgoing = !dto.isOutgoing, canEditMessage = !dto.canEditMessage, canDeleteMessage = !dto.canDeleteMessage!!)
                Log.d(TAG, "Loaded last message with inversion: primary=${lastMessage.primary}, original isOutgoing=${dto.isOutgoing}, corrected isOutgoing=${correctedDto.isOutgoing}, body=${correctedDto.messageBody.take(50)}")
                listOf(correctedDto)
            } else {
                listOfNotNull(dto)
            }
        } else {
            Log.d(TAG, "No last message found for chatId=$chatId")
            emptyList()
        }
    }

    private fun mapMessageStorageItemToDto(item: MessageStorageItem): MessageDto? = try {
        MessageDto(
            primary = item.primary,
            isOutgoing = item.outgoing,
            owner = item.owner,
            opponentJid = item.opponent,
            messageBody = item.body,
            messageSendingState = when {
                item.isRead -> MessageSendingState.Read
                item.outgoing -> MessageSendingState.Deliver
                else -> MessageSendingState.Sent
            },
            sentTimestamp = item.sentDate,
            editTimestamp = item.editDate,
            displayType = when (item.displayAs) {
                "system" -> MessageDisplayType.System
                else -> MessageDisplayType.Text
            },
            canEditMessage = item.outgoing,
            canDeleteMessage = item.outgoing,
            urlAvatar = null,
            isGroup = item.conversationType == ConversationType.Group,
            kind = null,
            isSelected = selectedItems.contains(item.primary),
            references = item.references.mapNotNull { ref ->
                try {
                    ref.toMessageReferenceDto()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to map MessageReferenceStorageItem to MessageReferenceDto: ${e.message}")
                    null
                }
            } as ArrayList<MessageReferenceDto>,
            isUnread = !item.isRead,
            isChecked = selectedItems.contains(item.primary),
            archivedId = item.archivedId
        ).also {
            Log.d(TAG, "Mapped message: primary=${it.primary}, messageId=${item.messageId}, sentTimestamp=${it.sentTimestamp}, body=${it.messageBody.take(50)}, isUnread=${it.isUnread}, isOutgoing=${it.isOutgoing}, archivedId=${it.archivedId}, conversationType=${item.conversationType_}")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to map MessageStorageItem to MessageDto: primary=${item.primary}, messageId=${item.messageId}, error=${e.message}")
        null
    }

    suspend fun mapAllMessages(): List<MessageDto> = withContext(Dispatchers.IO) {
        val realmList = realm.query<MessageStorageItem>(
            "owner = '$owner' AND opponent = '$opponent' AND isDeleted = false AND conversationType_ = '${conversationType.rawValue}'"
        ).sort("date", Sort.ASCENDING).find()

        val messageDtos = realmList.mapNotNull { item ->
            mapMessageStorageItemToDto(item)
        }.distinctBy { it.primary }.sortedBy { it.sentTimestamp }

        Log.d(TAG, "Mapped ${messageDtos.size} messages, first=${messageDtos.firstOrNull()?.primary}, last=${messageDtos.lastOrNull()?.primary}, lastMessageId=${messageDtos.lastOrNull()?.archivedId}")
        messageDtos
    }

    suspend fun loadInitialData() {
        _showSkeletonObserver.postValue(true)
        val initialMessages = loadLastMessage()
        if (initialMessages.isNotEmpty()) {
            messageList.clear()
            messageList.addAll(initialMessages)
            withContext(Dispatchers.Main) {
                _messages.value = messageList
                _unreadCount.value = messageList.count { it.isUnread }
                Log.d(TAG, "Displayed initial message: size=${messageList.size}, first=${messageList.firstOrNull()?.primary}")
            }
        }

        val chat = realm.query<LastChatsStorageItem>("primary = $0", chatId).first().find()
        val shouldLoadHistory = chat?.let {
            messageArchiveManager?.checkShouldLoadFullHistory(opponent, conversationType) ?: true
        } ?: true
        if (shouldLoadHistory) {
            getStreamAndSyncHistory(chatId)
        } else {
            Log.d(TAG, "Skipping history load for chatId=$chatId, already fully loaded")
            updateMessages()
            _isLoading.postValue(false)
            Log.d(TAG, "Hiding ProgressBar: history already loaded, chatId=$chatId")
        }
        _showSkeletonObserver.postValue(false)
    }

    fun loadOlderMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            loadingMutex.withLock {
                if (isLoadingHistory || pageLoadingCompleted) {
                    Log.d(TAG, "Skipping loadOlderMessages: historyLoading=$isLoadingHistory, pageLoadingCompleted=$pageLoadingCompleted")
                    return@launch
                }
                isLoadingHistory = true
                pageLoadingCompleted = false // Reset for new page load
            }
            try {
                val oldestMessage = messageList.minByOrNull { it.sentTimestamp }
                val oldestTimestamp = oldestMessage?.sentTimestamp?.let { Date(it) }
                val account = AccountManager.find(owner)
                if (account != null && account.stream != null) {
                    val isFullyLoaded = realm.query<LastChatsStorageItem>("primary = $0", chatId)
                        .first().find()?.fullArchiveLoaded ?: false
                    if (!isFullyLoaded) {
                        _isLoading.postValue(true)
                        Log.d(TAG, "Showing ProgressBar for loadOlderMessages, chatId=$chatId")
                        loadingTimeoutJob?.cancel()
                        loadingTimeoutJob = viewModelScope.launch(Dispatchers.IO) {
                            delay(10000L) // 10-second timeout
                            loadingMutex.withLock {
                                if (isLoadingHistory && !pageLoadingCompleted) {
                                    isLoadingHistory = false
                                    pageLoadingCompleted = true
                                    activeQueries.clear() // Clear any stuck queries
                                    _isLoading.postValue(false)
                                    Log.d(TAG, "Hiding ProgressBar: timeout reached for loadOlderMessages, chatId=$chatId")
                                }
                            }
                        }
                        val queryId = "MAM:${UUID.randomUUID().toString().take(6)}"
                        activeQueries.add(queryId) // Track new query
                        Log.d(TAG, "Starting loadOlderMessages query: queryId=$queryId, activeQueries=$activeQueries")
                        messageArchiveManager?.getHistoryByDate(
                            stream = account.stream!!,
                            jid = opponent,
                            conversationType = conversationType,
                            start = null,
                            end = oldestTimestamp,
                            reversed = true
                        )
                    } else {
                        Log.d(TAG, "Archive fully loaded for chatId=$chatId, no more messages to load")
                        pageLoadingCompleted = true
                        _isLoading.postValue(false)
                        Log.d(TAG, "Hiding ProgressBar: archive fully loaded, chatId=$chatId")
                    }
                } else {
                    Log.e(TAG, "Cannot load older messages: account or stream is null for owner=$owner")
                    pageLoadingCompleted = true
                    _isLoading.postValue(false)
                    Log.d(TAG, "Hiding ProgressBar: account/stream error, chatId=$chatId")
                }
            } finally {
                loadingMutex.withLock {
                    isLoadingHistory = false
                }
            }
        }
    }

    suspend fun getStreamAndSyncHistory(chatId: String) {
        loadingMutex.withLock {
            if (isLoadingHistory || pageLoadingCompleted) {
                Log.d(TAG, "Skipping getStreamAndSyncHistory: historyLoading=$isLoadingHistory, pageLoadingCompleted=$pageLoadingCompleted")
                return
            }
            isLoadingHistory = true
            pageLoadingCompleted = false // Reset for new page load
        }
        try {
            val account = AccountManager.find(owner)
            if (account != null) {
                val stream = account.stream
                if (stream != null) {
                    _isLoading.postValue(true)
                    Log.d(TAG, "Showing ProgressBar for getStreamAndSyncHistory, chatId=$chatId")
                    loadingTimeoutJob?.cancel()
                    loadingTimeoutJob = viewModelScope.launch(Dispatchers.IO) {
                        delay(10000L) // 10-second timeout
                        loadingMutex.withLock {
                            if (isLoadingHistory && !pageLoadingCompleted) {
                                isLoadingHistory = false
                                pageLoadingCompleted = true
                                activeQueries.clear() // Clear any stuck queries
                                _isLoading.postValue(false)
                                Log.d(TAG, "Hiding ProgressBar: timeout reached for getStreamAndSyncHistory, chatId=$chatId")
                            }
                        }
                    }
                    val queryId = "MAM:${UUID.randomUUID().toString().take(6)}"
                    activeQueries.add(queryId) // Track new query
                    Log.d(TAG, "Starting getStreamAndSyncHistory query: queryId=$queryId, activeQueries=$activeQueries")
                    messageArchiveManager?.syncChat(
                        stream = stream,
                        jid = opponent,
                        conversationType = conversationType,
                        callback = {
                            viewModelScope.launch(Dispatchers.IO) {
                                updateMessages()
                                if (activeQueries.isEmpty()) {
                                    _isLoading.postValue(false)
                                    Log.d(TAG, "Hiding ProgressBar after syncChat callback: chatId=$chatId, activeQueries=$activeQueries")
                                }
                            }
                        }
                    )
                } else {
                    Log.e(TAG, "Stream is null for account ${account.jid}")
                    pageLoadingCompleted = true
                    _isLoading.postValue(false)
                    Log.d(TAG, "Hiding ProgressBar: stream null, chatId=$chatId")
                }
            } else {
                Log.e(TAG, "Account not found for JID $owner")
                pageLoadingCompleted = true
                _isLoading.postValue(false)
                Log.d(TAG, "Hiding ProgressBar: account not found, chatId=$chatId")
            }
        } finally {
            loadingMutex.withLock {
                isLoadingHistory = false
            }
        }
    }

    suspend fun updateMessages() {
        val messageDtos = mapAllMessages()
        val currentMessages = messageList.associateBy { it.primary }.toMutableMap()
        var newMessagesAdded = false
        messageDtos.forEach { newMessage ->
            if (!currentMessages.containsKey(newMessage.primary)) {
                newMessagesAdded = true
            }
            currentMessages[newMessage.primary] = newMessage
            Log.d(TAG, "Merged message: primary=${newMessage.primary}, messageId=${newMessage.archivedId}, body=${newMessage.messageBody.take(50)}")
        }
        messageList.clear()
        messageList.addAll(currentMessages.values.sortedBy { it.sentTimestamp })
        val unreadCount = messageList.count { it.isUnread }
        Log.d(TAG, "Updated messages: ${messageList.size} messages, $unreadCount unread")
        withContext(Dispatchers.Main) {
            _messages.value = messageList
            _unreadCount.value = unreadCount
            val isFullyLoaded = realm.query<LastChatsStorageItem>("primary = $0", chatId)
                .first().find()?.fullArchiveLoaded ?: false
            if (!newMessagesAdded || isFullyLoaded || (pageLoadingCompleted && activeQueries.isEmpty())) {
                _isLoading.postValue(false)
                loadingTimeoutJob?.cancel()
                Log.d(TAG, "Hiding ProgressBar: newMessagesAdded=$newMessagesAdded, isFullyLoaded=$isFullyLoaded, pageLoadingCompleted=$pageLoadingCompleted, activeQueries=$activeQueries, chatId=$chatId")
            } else {
                Log.d(TAG, "Keeping ProgressBar visible: newMessagesAdded=$newMessagesAdded, isFullyLoaded=$isFullyLoaded, pageLoadingCompleted=$pageLoadingCompleted, activeQueries=$activeQueries, chatId=$chatId")
            }
        }
    }

    fun initMessagesListener(owner: String, opponentJid: String) {
        job = viewModelScope.launch(Dispatchers.IO) {
            observeMessages(owner, opponentJid, conversationType)
                .debounce(1000L)
                .distinctUntilChanged()
                .collect { messages ->
                    val currentMessages = messageList.associateBy { it.primary }.toMutableMap()
                    messages.forEach { newMessage ->
                        currentMessages[newMessage.primary] = newMessage
                        Log.d(TAG, "Merged message from Flow: primary=${newMessage.primary}, messageId=${newMessage.archivedId}, body=${newMessage.messageBody.take(50)}, sentTimestamp=${newMessage.sentTimestamp}")
                    }
                    messageList.clear()
                    messageList.addAll(currentMessages.values.sortedBy { it.sentTimestamp })
                    val unreadCount = messageList.count { it.isUnread }
                    Log.d(TAG, "Collected messages from Flow: ${messageList.size} messages, $unreadCount unread, first=${messageList.firstOrNull()?.primary}, last=${messageList.lastOrNull()?.primary}, lastMessageId=${messageList.lastOrNull()?.archivedId}")
                    withContext(Dispatchers.Main) {
                        _messages.value = messageList
                        _unreadCount.value = unreadCount
                    }
                }
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageDto>) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val insertedMessages = mutableListOf<MessageDto>()
                messages.forEach { messageDto ->
                    val existing = query<MessageStorageItem>("primary = $0", messageDto.primary).first().find()
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate message from receiver: primary=${messageDto.primary}, sentDate=${existing.sentDate}, body=${messageDto.messageBody.take(50)}")
                        return@forEach
                    }
                    val rreferences = realmListOf<MessageReferenceStorageItem>()
                    for (i in 0 until messageDto.references.size) {
                        val ref = copyToRealm(MessageReferenceStorageItem().apply {
                            primary = messageDto.references[i].id + "${System.currentTimeMillis()}"
                            uri = messageDto.references[i].uri
                            mimeType = messageDto.references[i].mimeType
                            isGeo = messageDto.references[i].isGeo
                            latitude = messageDto.references[i].latitude
                            longitude = messageDto.references[i].longitude
                            isAudioMessage = messageDto.references[i].isVoiceMessage
                            fileName = messageDto.references[i].fileName
                            fileSize = messageDto.references[i].size
                        })
                        rreferences.add(ref)
                    }
                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = messageDto.primary
                        owner = messageDto.owner
                        opponent = messageDto.opponentJid
                        body = messageDto.messageBody
                        date = messageDto.sentTimestamp
                        sentDate = messageDto.sentTimestamp
                        editDate = messageDto.editTimestamp
                        outgoing = messageDto.isOutgoing
                        isRead = !messageDto.isUnread
                        references = rreferences
                        conversationType_ = if (messageDto.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat"
                    })
                    insertedMessages.add(messageDto)
                    Log.d(TAG, "Inserted message from receiver: primary=${message.primary}, sentDate=${message.sentDate}, date=${Date(message.sentDate)}, body=${message.body.take(50)}, isRead=${message.isRead}, opponent=${message.opponent}")
                }
            }
            val list = messageList.toMutableList()
            val newMessages = messages.filter { m -> !list.any { it.primary == m.primary } }
            if (newMessages.isNotEmpty()) {
                if (newMessages.all { it.sentTimestamp >= (list.lastOrNull()?.sentTimestamp ?: 0) }) {
                    list.addAll(newMessages)
                } else {
                    list.addAll(0, newMessages)
                }
                count = list.count { it.isUnread }
                messageList = ArrayList(list.distinctBy { it.primary })
                messageList.sortBy { it.sentTimestamp }
                debugMessageList()
                Log.d(TAG, "After insertMessagesFromReceiver, messageListSize=${messageList.size}, unreadCount=$count, messages=${messages.map { "${it.primary}: ${Date(it.sentTimestamp)}" }}")
                withContext(Dispatchers.Main) {
                    _messages.value = messageList
                    _unreadCount.value = count
                }
            }
        }
    }

    fun debugMessageList() {
        Log.d(TAG, "Debugging messageList: size=${messageList.size}")
        messageList.forEachIndexed { index, msg ->
            Log.d(TAG, "Message[$index]: primary=${msg.primary}, sentTimestamp=${msg.sentTimestamp}, body=${msg.messageBody.take(50)}, isUnread=${msg.isUnread}")
        }
    }

    fun initChatDataListener(chatId: String) {
        val request = realm.query(LastChatsStorageItem::class, "primary = '$chatId'").find()
        val lastChatsFlow = request.asFlow()
        job = viewModelScope.launch(Dispatchers.IO) {
            lastChatsFlow.collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val chat = if (changes.list.isNotEmpty()) changes.list.first() else null
                        withContext(Dispatchers.Main) {
                            _chat.value = chat?.toChatListDto()
                            if (chat != null) {
                                _muteExpired.value = chat.muteExpired
                                _opponentName.value = chat.toChatListDto().getChatName()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getDraft(id: String): String? {
        var drafted: String? = null
        viewModelScope.launch {
            realm.writeBlocking {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                drafted = item?.draftMessage
            }
        }
        return drafted
    }

    fun getContactId(id: String): String? {
        var contactPrimary: String? = null
        viewModelScope.launch {
            realm.writeBlocking {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                contactPrimary = item?.rosterItem?.primary
            }
        }
        return contactPrimary
    }

    fun loadChat(chatId: String): ChatListDto? {
        var chatListDto: ChatListDto? = null
        realm.writeBlocking {
            val chat = this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
            if (chat != null) chatListDto = chat.toChatListDto()
        }
        return chatListDto
    }

    fun getMessageList(chatId: String) {
        val lastChatsStorageItem = realm.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
        val owner = lastChatsStorageItem?.owner
        val opponent = lastChatsStorageItem?.jid
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find()
            updateMessageList(realmList)
        }
    }

    fun getAccount(id: String): AccountDto? {
        var account: AccountDto? = null
        realm.writeBlocking {
            val acc = this.query(AccountStorageItem::class, "primary = '$id'").first().find()
            account = if (acc != null) acc.toAccountDto() else null
        }
        return account
    }

    fun insertMessage(chatId: String, messageDto: MessageDto) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val existing =
                    query<MessageStorageItem>("primary = $0", messageDto.primary).first().find()
                if (existing != null) {
                    Log.d(
                        "ChatViewModel",
                        "Skipping duplicate message insertion: primary=${messageDto.primary}, sentDate=${existing.sentDate}"
                    )
                    return@writeBlocking
                }
                val rreferences = realmListOf<MessageReferenceStorageItem>()
                for (i in 0 until messageDto.references.size) {
                    val ref = copyToRealm(MessageReferenceStorageItem().apply {
                        primary = messageDto.references[i].id + "${System.currentTimeMillis()}"
                        uri = messageDto.references[i].uri
                        mimeType = messageDto.references[i].mimeType
                        isGeo = messageDto.references[i].isGeo
                        latitude = messageDto.references[i].latitude
                        longitude = messageDto.references[i].longitude
                        isAudioMessage = messageDto.references[i].isVoiceMessage
                        fileName = messageDto.references[i].fileName
                        fileSize = messageDto.references[i].size
                    })
                    rreferences.add(ref)
                }
                val message = copyToRealm(MessageStorageItem().apply {
                    primary = messageDto.primary
                    owner = messageDto.owner
                    opponent = messageDto.opponentJid
                    body = messageDto.messageBody
                    date = messageDto.sentTimestamp
                    sentDate = messageDto.sentTimestamp
                    editDate = messageDto.editTimestamp
                    outgoing = messageDto.isOutgoing
                    isRead = !messageDto.isUnread
                    references = rreferences
                    conversationType_ =
                        if (messageDto.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat"
                })
                Log.d(
                    "ChatViewModel",
                    "Inserted message: primary=${message.primary}, sentDate=${message.sentDate}, body=${
                        message.body.take(50)
                    }, isRead=${message.isRead}, opponent=${message.opponent}"
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun queryRecentMessages(opponentJid: String) {
        val account = AccountManager.find(owner)
        if (account != null) {
            val stream = account.stream
            if (stream != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    messageArchiveManager?.getHistoryByDate(
                        stream = stream,
                        jid = opponentJid,
                        conversationType = conversationType,
                        start = null,
                        end = null,
                        reversed = false,
                        callback = {
                            Log.d(TAG, "Recent messages query completed")
                            viewModelScope.launch(Dispatchers.IO) {
                                updateMessages()
                            }
                        }
                    )
                }
            } else {
                Log.e(TAG, "Stream is null for account ${account.jid}")
            }
        } else {
            Log.e(TAG, "Account not found for JID $owner")
        }
    }

    fun selectMessage(primary: String, checked: Boolean) {
        if (checked) {
            selectedItems.add(primary)
        } else {
            selectedItems.remove(primary)
        }
        val position = messageList.indexOfFirst { it.primary == primary }
        if (position != -1) {
            val updatedMessage = messageList[position].copy(isSelected = checked, isChecked = checked)
            messageList[position] = updatedMessage
            Log.d(TAG, "Selected message: primary=$primary, checked=$checked, selectedItems=$selectedItems, messageListSize=${messageList.size}")
            viewModelScope.launch(Dispatchers.Main) {
                _selectedCount.value = selectedItems.size
                _messages.value = messageList // Trigger adapter update
            }
        }
    }

    fun clearAllSelected() {
        if (selectedItems.isNotEmpty()) {
            val updatedList = messageList.map { message ->
                if (selectedItems.contains(message.primary)) {
                    message.copy(isSelected = false, isChecked = false)
                } else {
                    message
                }
            }
            selectedItems.clear()
            messageList = ArrayList(updatedList)
            Log.d(TAG, "Cleared selection, messageListSize=${messageList.size}")
            viewModelScope.launch(Dispatchers.Main) {
                _selectedCount.value = 0
                _messages.value = messageList
            }
        }
    }

    fun isOutgoing(): Boolean {
        if (selectedItems.size != 1) return false
        val primary = selectedItems.first()
        var out = false
        realm.writeBlocking {
            val item = query(MessageStorageItem::class, "primary = '$primary'").first().find()
            if (item != null && item.outgoing) out = true
        }
        return out
    }

    fun deleteMessage(primary: String, forAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val deletedMessage = query(MessageStorageItem::class, "primary = '$primary'").first().find()
                if (deletedMessage != null) findLatest(deletedMessage)?.let { delete(it) }
            }
        }
        if (forAll) {
            // TODO: Implement server request to delete message
        }
    }

    fun getSelectedText(): String {
        var text = ""
        val selected = ArrayList(selectedItems)
        realm.writeBlocking {
            selected.forEach { primary ->
                val message = query(MessageStorageItem::class, "primary = '$primary'").first().find()
                if (message != null) text += "${message.body}\n"
            }
        }
        return text
    }

    fun getForwardMessagesText(): String {
        val selected = ArrayList(selectedItems)
        var text = ""
        realm.writeBlocking {
            selected.forEach { primary ->
                val message = query(MessageStorageItem::class, "primary = '$primary'").first().find()
                if (message != null) text += "${if (message.outgoing) message.owner else message.opponent}\n${message.body}\n"
            }
        }
        return text
    }

    fun getMessagePosition(primary: String): Int {
        return messageList.indexOfFirst { it.primary == primary }
    }

    fun setUnread(id: String) {
        // Uncomment if needed to mark a specific message as read
        /*
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val mes = query(MessageStorageItem::class, "primary = '$id'").first().find()
                mes?.isRead = true
            }
        }
        */
    }

    fun getMessage(primary: String? = null): MessageDto? {
        val selected = ArrayList(selectedItems)
        val id = primary ?: if (selected.isNotEmpty()) selected[0] else return null
        var message: MessageDto? = null
        realm.writeBlocking {
            val item = query(MessageStorageItem::class, "primary = '$id'").first().find()
            if (item != null) message = MessageDto(
                item.primary,
                item.outgoing,
                item.owner,
                item.opponent,
                item.body,
                when {
                    item.isRead -> MessageSendingState.Read
                    item.outgoing -> MessageSendingState.Deliver
                    else -> MessageSendingState.Sent
                },
                item.sentDate,
                editTimestamp = item.editDate,
                MessageDisplayType.Text,
                item.outgoing,
                item.outgoing,
                null,
                isUnread = !item.isRead,
                isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
                references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                isChecked = selectedItems.contains(item.primary)
            )
        }
        return message
    }

    fun markAllMessageUnread(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val chat = query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                if (chat != null) {
                    val owner = chat.owner
                    val opponent = chat.jid
                    val unreadMessages = query(MessageStorageItem::class, "isRead = false AND owner = '$owner' AND opponent = '$opponent'").find()
                    if (unreadMessages.isNotEmpty()) { // Only mark if there are unread messages
                        unreadMessages.forEach { it.isRead = true }
                        Log.d(TAG, "Marked all messages as read for chatId=$chatId, owner=$owner, opponent=$opponent, updated ${unreadMessages.size} messages")
                    } else {
                        Log.d(TAG, "No unread messages to mark for chatId=$chatId")
                    }
                } else {
                    Log.w(TAG, "No chat found for chatId=$chatId")
                }
            }
        }
    }

    fun getSelectedMessageText(): String {
        var text = ""
        val selected = ArrayList(selectedItems)
        if (selected.isNotEmpty()) {
            val id = selected[0]
            realm.writeBlocking {
                val item = query(MessageStorageItem::class, "primary = '$id'").first().find()
                if (item != null) text = item.body
            }
        }
        return text
    }

    fun getMessageId(): String {
        val selected = ArrayList(selectedItems)
        return if (selected.isNotEmpty()) selected[0] else ""
    }

    fun deleteMessages(forAll: Boolean) {
        val selected = ArrayList(selectedItems)
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                selected.forEach { primary ->
                    val deletedMessage = query(MessageStorageItem::class, "primary = '$primary'").first().find()
                    if (deletedMessage != null) findLatest(deletedMessage)?.let { delete(it) }
                }
            }
        }
        if (forAll) {
            // TODO: Implement server request to delete messages
        }
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val editableMessage = query(MessageStorageItem::class, "primary = '$primary'").first().find()
                editableMessage?.body = newBody
                editableMessage?.editDate = System.currentTimeMillis()
            }
        }
    }

    fun clearHistory(chatId: String, opponentJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val messages = query(MessageStorageItem::class, "opponent = '$opponentJid'").find()
                delete(messages)
            }
        }
    }

    fun deleteChat(id: String) {
        job?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) findLatest(item)?.let { delete(it) }
            }
        }
    }

    fun insertChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                copyToRealm(LastChatsStorageItem().apply {
                    primary = id
                    owner = this@ChatViewModel.owner
                    jid = this@ChatViewModel.opponent
                    conversationType_ = this@ChatViewModel.conversationType.rawValue
                })
            }
        }
    }

    fun saveDraft(id: String, draft: String?) {
        realm.writeBlocking {
            val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) {
                findLatest(item).also {
                    val oldDraft = it?.draftMessage
                    if (oldDraft != draft) {
                        it?.draftMessage = draft
                        if (!draft.isNullOrEmpty()) {
                            it?.messageDate = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }

    fun setMute(id: String, mute: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                Log.d("item", "$item")
                item?.muteExpired = mute
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        loadingTimeoutJob?.cancel()
        realm.close()
        CoroutineScope(Dispatchers.IO).launch {
            messageArchiveManager?.reset()

        }
        activeQueries.clear()
    }


    fun saveLastPosition(id: String, savedPosition: String) {
        realm.writeBlocking {
            val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) {
                findLatest(item).also {
                    it?.lastPosition = savedPosition
                }
            }
        }
    }

    fun getPositionMessage(lastPosition: String): Int {
        Log.d("mmm", "messageList = $messageList")
        messageList.sortBy { it.sentTimestamp }
        var pos = 0
        for (i in 0 until messageList.size) {
            if (messageList[i].primary == lastPosition) pos = i
        }
        return pos
    }

    fun lastPositionPrimary(id: String): String {
        var lastPosition = ""
        realm.writeBlocking {
            val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) lastPosition = item.lastPosition
        }
        return lastPosition
    }

    private suspend fun updateMessageList(messages: List<MessageStorageItem>) {
        val list = ArrayList<MessageDto>()
        realm.write {
            list.addAll(messages.mapNotNull { message ->
                MessageDto(
                    primary = message.primary,
                    isOutgoing = message.outgoing,
                    owner = message.owner,
                    opponentJid = message.opponent,
                    messageBody = message.body,
                    messageSendingState = when {
                        message.isRead -> MessageSendingState.Read
                        message.outgoing -> MessageSendingState.Deliver
                        else -> MessageSendingState.Sent
                    },
                    sentTimestamp = message.sentDate,
                    editTimestamp = message.editDate,
                    displayType = MessageDisplayType.Text,
                    canEditMessage = message.outgoing,
                    canDeleteMessage = message.outgoing,
                    urlAvatar = null,
                    isGroup = message.conversationType == ConversationType.Group,
                    kind = null,
                    isSelected = selectedItems.contains(message.primary),
                    references = message.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                    isUnread = !message.isRead,
                    isChecked = selectedItems.contains(message.primary),
                    archivedId = message.archivedId
                ).also {
                    Log.d(TAG, "Mapped: primary=${it.primary}, body=${it.messageBody.take(50)}")
                }
            })
        }
        count = list.count { it.isUnread }
        if (list != messageList || list.isNotEmpty()) {
            val newMessages = list.filter { m -> !messageList.any { it.primary == m.primary } }
            if (newMessages.isNotEmpty()) {
                if (newMessages.all { it.sentTimestamp >= (messageList.lastOrNull()?.sentTimestamp ?: 0) }) {
                    messageList.addAll(newMessages)
                } else {
                    messageList.addAll(0, newMessages)
                }
                messageList = ArrayList(messageList.distinctBy { it.primary })
            }
            messageList.sortBy { it.sentTimestamp }
            Log.d(TAG, "Updating messages: ${list.size} messages, $count unread")
            withContext(Dispatchers.Main) {
                _messages.postValue(messageList)
                _unreadCount.postValue(count)
            }
        }
    }
}