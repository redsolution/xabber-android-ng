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
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import com.xabber.utils.toMessageReferenceDto
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var job: Job? = null
    private var isFetchingOlder = false
    private var isFetchingNewer = false
    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired

    private val TAG = "ChatViewModel"
    private val messageBuffer = Collections.synchronizedList(mutableListOf<MessageDto>())
    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int> = _selectedCount
    private val selectedItems = ConcurrentHashMap.newKeySet<String>()
    private var count = 0
    private val pageSize = 50
    private var messageArchiveManager: MessageArchiveManager? = null
    private var fullArchiveLoaded = false

    init {
        messageArchiveManager = MessageArchiveManager(owner)
        messageArchiveManager?.temporaryMessageReceiver = object : MessageArchiveManager.TemporaryMessageReceiver {
            override suspend fun didReceiveMessage(item: MessageStorageItem, queryId: String) {
                viewModelScope.launch(Dispatchers.IO) {
                    updateMessageList(realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find())
                }
            }

            override fun didReceiveEndPage(queryId: String, complete: Boolean, first: String, last: String, count: Int) {
                Log.d(TAG, "Received MAM page: queryId=$queryId, complete=$complete, first=$first, last=$last, count=$count")
                if (complete || count == 0) {
                    fullArchiveLoaded = true
                    realm.writeBlocking {
                        val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                        if (chat != null) {
                            findLatest(chat)?.fullArchiveLoaded = true
                        }
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    updateMessageList(realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find())
                }
                viewModelScope.launch(Dispatchers.Main) {
                    _isLoading.value = false
                    isFetchingOlder = false
                    isFetchingNewer = false
                }
            }

            fun onError(queryId: String, error: String) {
                Log.e(TAG, "MAM error for queryId=$queryId: $error")
                viewModelScope.launch(Dispatchers.Main) {
                    _isLoading.value = false
                    isFetchingOlder = false
                    isFetchingNewer = false
                }
            }
        }
        initChatDataListener(chatId)
        initMessagesListener(owner, opponent)
        loadInitialData()
        viewModelScope.launch {
            if (messageArchiveManager?.checkShouldLoadFullHistory(opponent, conversationType) == true) {
                val account = AccountManager.find(owner)
                val stream = account?.stream ?: return@launch
                messageArchiveManager?.startLoadHistory(stream, opponent, conversationType)
                Log.d(TAG, "Started loading full history for opponent=$opponent")
            }
            loadNewerMessages()
        }
        isFetchingOlder = false
        isFetchingNewer = false
        _isLoading.value = false
    }

    private fun loadInitialData() {
        realm.writeBlocking {
            val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
            val isSynced = chat?.isSynced ?: false
            fullArchiveLoaded = chat?.fullArchiveLoaded ?: false
            if (!isSynced) {
                getStreamAndSyncHistory(chatId)
            } else {
                loadLocalMessages()
            }
        }
    }

    private fun loadLocalMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find()
            updateMessageList(realmList)
        }
    }

    private fun getStreamAndSyncHistory(chatId: String) {
        val account = AccountManager.find(owner)
        if (account != null) {
            val stream = account.stream
            if (stream != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    messageArchiveManager?.syncChat(
                        stream = stream,
                        jid = opponent,
                        conversationType = conversationType,
                        callback = {
                            Log.d(TAG, "Initial sync completed for chatId=$chatId")
                            loadInitialMessages()
                        }
                    )
                }
            } else {
                Log.e(TAG, "No stream available for account $owner")
            }
        } else {
            Log.e(TAG, "No account found for owner $owner")
        }
    }

    private fun loadInitialMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val account = AccountManager.find(owner)
            val stream = account?.stream ?: return@launch
            messageArchiveManager?.requestArchive(
                stream = stream,
                jid = opponent,
                isContinues = true,
                conversationType = conversationType,
                max = pageSize,
                callback = {
                    Log.d(TAG, "Initial messages loaded")
                    viewModelScope.launch(Dispatchers.IO) {
                        updateMessageList(realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find())
                    }
                }
            )
        }
    }

    fun loadOlderMessages() {
        if (isFetchingOlder || fullArchiveLoaded) {
            Log.d(TAG, "Skipping load older: fetching=$isFetchingOlder, fullLoaded=$fullArchiveLoaded")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val account = AccountManager.find(owner)
            val stream = account?.stream ?: return@launch
            isFetchingOlder = true
            val oldestMessageId = getOldestMessageId() ?: run { isFetchingOlder = false; return@launch }
            messageArchiveManager?.getPrevHistory(
                stream = stream,
                jid = opponent,
                conversationType = conversationType,
                messageId = oldestMessageId,
                callback = {
                    Log.d(TAG, "Older messages loaded before messageId=$oldestMessageId")
                    viewModelScope.launch(Dispatchers.IO) {
                        updateMessageList(realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find())
                    }
                    isFetchingOlder = false
                }
            )
        }
    }

    private fun loadNewerMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isFetchingNewer) return@launch
            isFetchingNewer = true
            _isLoading.postValue(true)
            val account = AccountManager.find(owner)
            val stream = account?.stream ?: run { isFetchingNewer = false; return@launch }
            val lastMessageId = synchronized(messageBuffer) { messageBuffer.maxByOrNull { it.sentTimestamp }?.archivedId }

            messageArchiveManager?.getNextHistory(
                stream = stream,
                jid = opponent,
                conversationType = conversationType,
                messageId = lastMessageId,
                callback = {
                    Log.d(TAG, "Newer messages loaded after messageId=$lastMessageId")
                    viewModelScope.launch(Dispatchers.IO) {
                        updateMessageList(realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find())
                    }
                    isFetchingNewer = false
                }
            )
        }
    }

    fun initMessagesListener(owner: String, opponentJid: String) {
        val request = realm.query(MessageStorageItem::class, "owner = '$owner' AND opponent = '$opponentJid'")
        val lastChatsFlow = request.asFlow().debounce(500).distinctUntilChanged() // Increased debounce to reduce UI updates
        viewModelScope.launch(Dispatchers.IO) {
            lastChatsFlow.collect { changes: ResultsChange<MessageStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        updateMessageList(changes.list)
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun updateMessageList(messages: List<MessageStorageItem>) {
        val list = ArrayList<MessageDto>()
        list.addAll(messages.map { item ->
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
                displayType = MessageDisplayType.Text,
                canEditMessage = item.outgoing,
                canDeleteMessage = item.outgoing,
                urlAvatar = null,
                isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
                kind = null,
                isSelected = selectedItems.contains(item.primary),
                references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                isUnread = !item.isRead,
                isChecked = selectedItems.contains(item.primary),
                archivedId = item.archivedId
            ).also {
                Log.d(
                    TAG,
                    "Mapped MessageStorageItem to MessageDto: primary=${it.primary}, sentTimestamp=${it.sentTimestamp}, date=${Date(it.sentTimestamp)}, body=${it.messageBody.take(50)}, isUnread=${it.isUnread}"
                )
            }
        })
        count = list.count { it.isUnread }
        for (i in 0 until list.size - 1) {
            val gapMs = list[i+1].sentTimestamp - list[i].sentTimestamp
            if (gapMs > 86_400_000) {  // >1 day
                viewModelScope.launch(Dispatchers.IO) {
                    val account = AccountManager.find(owner)
                    val stream = account?.stream ?: return@launch
                    messageArchiveManager?.requestArchive(
                        stream = stream,
                        jid = opponent,
                        isContinues = false,
                        conversationType = conversationType,
                        start = Date(list[i].sentTimestamp + 1),
                        end = Date(list[i+1].sentTimestamp - 1),
                        max = pageSize
                    )
                }
                break  // Fetch one gap at a time
            }
        }
        synchronized(messageBuffer) {
            val currentBuffer = ArrayList(messageBuffer)
            if (list != currentBuffer) {
                messageBuffer.clear()
                messageBuffer.addAll(list.distinctBy { it.primary }.sortedBy { it.sentTimestamp })
                Log.d(TAG, "Updating messages LiveData: ${list.size} messages, $count unread, messageBufferSize=${messageBuffer.size}")
                viewModelScope.launch(Dispatchers.Main) {
                    _messages.value = messageBuffer
                    _unreadCount.value = count
                }
            } else {
                Log.d(TAG, "No change in messages, skipping LiveData update, messageBufferSize=${messageBuffer.size}")
            }
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
                        fullArchiveLoaded = chat?.fullArchiveLoaded ?: false
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
            if (chat != null) {
                chatListDto = chat.toChatListDto()
                fullArchiveLoaded = chat.fullArchiveLoaded
            }
        }
        return chatListDto
    }

    fun getMessageList(chatId: String) {
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
                val existing = query<MessageStorageItem>("primary = $0", messageDto.primary).first().find()
                if (existing != null) {
                    Log.d(
                        TAG,
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
                    archivedId = messageDto.archivedId ?: UUID.randomUUID().toString()
                    conversationType_ = if (messageDto.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat"
                })
                val item = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                if (item != null) {
                    findLatest(item)?.apply {
                        lastMessage = message
                        messageDate = message.date
                        if (!messageDto.isOutgoing && muteExpired <= 0) {
                            isArchived = false
                            unread = (unread ?: 0) + if (messageDto.isUnread) 1 else 0
                        }
                    }
                }
                Log.d(
                    TAG,
                    "Inserted message: primary=${message.primary}, sentDate=${message.sentDate}, body=${
                        message.body.take(50)
                    }, isRead=${message.isRead}, opponent=${message.opponent}"
                )
            }
            synchronized(messageBuffer) {
                messageBuffer.add(messageDto)
                messageBuffer.sortBy { it.sentTimestamp }
            }
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageBuffer
                _unreadCount.value = messageBuffer.count { it.isUnread }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun queryRecentMessages(opponentJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val account = AccountManager.find(owner)
            val stream = account?.stream ?: return@launch
            messageArchiveManager?.requestArchive(
                stream = stream,
                jid = opponentJid,
                isContinues = true,
                conversationType = conversationType,
                max = pageSize,
                callback = {
                    Log.d(TAG, "Recent messages queried")
                    viewModelScope.launch(Dispatchers.IO) {
                        updateMessageList(realm.query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponentJid'").find())
                    }
                }
            )
        }
    }

    fun getOldestMessageId(): String? {
        synchronized(messageBuffer) {
            return messageBuffer.firstOrNull()?.archivedId
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageDto>) {
        Log.d(TAG, "insertMessagesFromReceiver called with ${messages.size} messages for chatId=$chatId")
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val insertedMessages = mutableListOf<MessageDto>()
                messages.forEach { messageDto ->
                    val existing = query<MessageStorageItem>("archivedId = $0", messageDto.archivedId).first().find()
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate message from receiver: archivedId=${messageDto.archivedId}, primary=${existing.primary}, body=${messageDto.messageBody.take(50)}")
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
                        archivedId = messageDto.archivedId ?: UUID.randomUUID().toString()
                        conversationType_ = if (messageDto.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat"
                    })
                    insertedMessages.add(messageDto)
                    Log.d(TAG, "Inserted message from receiver: primary=${message.primary}, sentDate=${message.sentDate}, date=${Date(message.sentDate)}, body=${message.body.take(50)}, isRead=${message.isRead}, opponent=${message.opponent}, archivedId=${message.archivedId}")
                }
                val item = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                if (item != null && insertedMessages.isNotEmpty()) {
                    findLatest(item)?.apply {
                        lastMessage = query<MessageStorageItem>("primary = $0", insertedMessages.last().primary).first().find()
                        messageDate = lastMessage?.date ?: messageDate
                        if (insertedMessages.any { !it.isOutgoing } && muteExpired <= 0) {
                            isArchived = false
                            unread = (unread ?: 0) + insertedMessages.count { !it.isOutgoing && it.isUnread }
                        }
                    }
                    Log.d(TAG, "Updated LastChatsStorageItem for chatId=$chatId: lastMessageId=${insertedMessages.last().primary}, unread=${item?.unread}")
                }
            }
            synchronized(messageBuffer) {
                val newMessages = messages.filter { m -> !messageBuffer.any { it.archivedId == m.archivedId } }
                messageBuffer.addAll(0, newMessages)
                messageBuffer.sortBy { it.sentTimestamp }
            }
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageBuffer
                _unreadCount.value = messageBuffer.count { it.isUnread }
            }
        }
    }

    fun selectMessage(primary: String, checked: Boolean) {
        if (checked) {
            selectedItems.add(primary)
        } else {
            selectedItems.remove(primary)
        }
        synchronized(messageBuffer) {
            val position = messageBuffer.indexOfFirst { it.primary == primary }
            if (position != -1) {
                val updatedMessage = messageBuffer[position].copy(isSelected = checked, isChecked = checked)
                messageBuffer[position] = updatedMessage
                Log.d(TAG, "Selected message: primary=$primary, checked=$checked, selectedItems=$selectedItems, messageBufferSize=${messageBuffer.size}")
                viewModelScope.launch(Dispatchers.Main) {
                    _selectedCount.value = selectedItems.size
                    _messages.value = messageBuffer
                }
            }
        }
    }

    fun clearAllSelected() {
        synchronized(messageBuffer) {
            if (selectedItems.isNotEmpty()) {
                val updatedList = messageBuffer.map { message ->
                    if (selectedItems.contains(message.primary)) {
                        message.copy(isSelected = false, isChecked = false)
                    } else {
                        message
                    }
                }
                selectedItems.clear()
                messageBuffer.clear()
                messageBuffer.addAll(updatedList)
                Log.d(TAG, "Cleared selection, messageBufferSize=${messageBuffer.size}")
                viewModelScope.launch(Dispatchers.Main) {
                    _selectedCount.value = 0
                    _messages.value = messageBuffer
                }
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
                val deletedMessage = query<MessageStorageItem>("primary = '$primary'").first().find()
                if (deletedMessage != null) {
                    delete(deletedMessage)
                    val item = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                    if (item != null) {
                        val newLastMessage = query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find().sortedByDescending { it.sentDate }.firstOrNull()
                        findLatest(item)?.apply {
                            lastMessage = newLastMessage
                            messageDate = newLastMessage?.sentDate ?: 0
                            if (newLastMessage == null) {
                                unread = 0
                            }
                        }
                        Log.d(TAG, "Updated LastChatsStorageItem after deleting message: newLastMessageId=${newLastMessage?.primary}")
                    }
                }
            }
            synchronized(messageBuffer) {
                messageBuffer.removeAll { it.primary == primary }
            }
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageBuffer
            }
            if (forAll) {
                // TODO: Implement server request to delete message
            }
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
        synchronized(messageBuffer) {
            return messageBuffer.indexOfFirst { it.primary == primary }
        }
    }

    fun setUnread(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val mes = query(MessageStorageItem::class, "primary = '$id'").first().find()
                mes?.isRead = true
            }
        }
    }

    fun getMessage(primary: String? = null): MessageDto? {
        val selected = ArrayList(selectedItems)
        val id = primary ?: if (selected.isNotEmpty()) selected[0] else return null
        var message: MessageDto? = null
        realm.writeBlocking {
            val item = query(MessageStorageItem::class, "primary = '$id'").first().find()
            if (item != null) message = MessageDto(
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
                displayType = MessageDisplayType.Text,
                canEditMessage = item.outgoing,
                canDeleteMessage = item.outgoing,
                urlAvatar = null,
                isUnread = !item.isRead,
                isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
                references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                isChecked = selectedItems.contains(item.primary),
                archivedId = item.archivedId
            )
        }
        return message
    }

    fun markAllMessageUnread(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val chat = query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                if (chat != null) {
                    val owner = chat.owner
                    val opponent = chat.jid
                    val unreadMessages = query(MessageStorageItem::class, "isRead = false AND owner = '$owner' AND opponent = '$opponent'").find()
                    if (unreadMessages.isNotEmpty()) {
                        unreadMessages.forEach { it.isRead = true }
                        chat.unread = 0
                        Log.d(TAG, "Marked all messages as read for chatId=$chatId, owner=$owner, opponent=$opponent, updated ${unreadMessages.size} messages")
                    } else {
                        Log.d(TAG, "No unread messages to mark for chatId=$chatId")
                    }
                } else {
                    Log.w(TAG, "No chat found for chatId=$chatId")
                }
            }
            synchronized(messageBuffer) {
                messageBuffer.forEachIndexed { index, message ->
                    if (message.isUnread) {
                        messageBuffer[index] = message.copy(isUnread = false)
                    }
                }
            }
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageBuffer
                _unreadCount.value = 0
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
                    val deletedMessage = query<MessageStorageItem>("primary = '$primary'").first().find()
                    if (deletedMessage != null) {
                        delete(deletedMessage)
                    }
                }
                val item = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                if (item != null) {
                    val newLastMessage = query<MessageStorageItem>("owner = '$owner' AND opponent = '$opponent'").find().sortedByDescending { it.sentDate }.firstOrNull()
                    findLatest(item)?.apply {
                        lastMessage = newLastMessage
                        messageDate = newLastMessage?.sentDate ?: 0
                        if (newLastMessage == null) {
                            unread = 0
                        }
                    }
                    Log.d(TAG, "Updated LastChatsStorageItem after deleting messages: newLastMessageId=${newLastMessage?.primary}")
                }
            }
            synchronized(messageBuffer) {
                messageBuffer.removeAll { selected.contains(it.primary) }
            }
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageBuffer
            }
            if (forAll) {
                // TODO: Implement server request to delete messages
            }
        }
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val editableMessage = query<MessageStorageItem>("primary = '$primary'").first().find()
                editableMessage?.body = newBody
                editableMessage?.editDate = System.currentTimeMillis()
            }
            synchronized(messageBuffer) {
                val index = messageBuffer.indexOfFirst { it.primary == primary }
                if (index != -1) {
                    messageBuffer[index] = messageBuffer[index].copy(
                        messageBody = newBody,
                        editTimestamp = System.currentTimeMillis()
                    )
                    viewModelScope.launch(Dispatchers.Main) {
                        _messages.value = messageBuffer
                    }
                }
            }
        }
    }

    fun clearHistory(chatId: String, opponentJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val messages = query(MessageStorageItem::class, "opponent = '$opponentJid'").find()
                delete(messages)
                val chat = query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                chat?.lastMessage = null
                chat?.lastPosition = ""
                chat?.unread = 0
            }
            synchronized(messageBuffer) {
                messageBuffer.clear()
            }
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageBuffer
                _unreadCount.value = 0
            }
        }
    }

    fun deleteChat(id: String) {
        job?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) findLatest(item)?.let { delete(it) }
            }
            synchronized(messageBuffer) {
                messageBuffer.clear()
            }
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageBuffer
            }
        }
    }

    fun insertChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
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
                        } else {
                            it?.messageDate = it?.lastMessage?.date ?: 0
                        }
                    }
                }
            }
        }
    }

    fun setMute(id: String, mute: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.muteExpired = mute
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageArchiveManager?.reset()
        job?.cancel()
        viewModelScope.cancel()
        realm.close()  // Close Realm instance
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
        synchronized(messageBuffer) {
            return messageBuffer.indexOfFirst { it.primary == lastPosition }
        }
    }

    fun lastPositionPrimary(id: String): String {
        var lastPosition = ""
        realm.writeBlocking {
            val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) lastPosition = item.lastPosition
        }
        return lastPosition
    }
}