package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.account.AccountManager
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
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.InitialResults
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

@RequiresApi(Build.VERSION_CODES.O)
class ChatViewModel(
    private val chatId: String,
    val owner: String,
    val opponent: String,
    val conversationType: ConversationType
) : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private var lastLoadOlderMessagesTime = 0L
    private val _chat = MutableLiveData<ChatListDto?>()
    val chat: LiveData<ChatListDto?> = _chat
    private val messageListMutex = Mutex()
    val _messages = MutableLiveData<List<MessageDto>>()
    val messages: LiveData<List<MessageDto>> = _messages
    private var job: Job? = null
    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName
    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _isLocked = MutableLiveData<Boolean>()
//    val isLocked: LiveData<Boolean> = _isLocked
    private var isLoadingHistory = false
    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired
    val TAG = "ChatViewModel"
    private var messageList = ArrayList<MessageDto>()
    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int> = _selectedCount
    private val selectedItems = HashSet<String>()
    private var count = 0
    private val _showSkeletonObserver = MutableLiveData<Boolean>()
    val showSkeletonObserver: LiveData<Boolean> = _showSkeletonObserver
    private val _searchTextObserver = MutableLiveData<String>()
    val searchTextObserver: LiveData<String> = _searchTextObserver
    private val _inSearchMode = MutableLiveData<Boolean>()
    val inSearchMode: LiveData<Boolean> = _inSearchMode
    private var unreadMessagePositionId: Int? = null
    private val _canUnpinMessage = MutableLiveData<Boolean>()
    val canUnpinMessage: LiveData<Boolean> = _canUnpinMessage
    private val loadingMutex = Mutex()

    companion object {
        private val retryAttempts = mutableMapOf<String, Int>()
    }

    init {
        initChatDataListener(chatId)
//        initMessagesListener(owner, opponent)
        markAllMessageUnread(chatId)
        viewModelScope.launch(Dispatchers.IO) {
            delay(500L)
            loadInitialData()
        }
    }

    private fun mapMessageStorageItemToDto(item: MessageStorageItem): MessageDto? = try {
        MessageDto(
            primary = item.primary,
            isOutgoing = item.outgoing,
            owner = item.owner,
            opponentJid = item.opponent,
            messageBody = item.body ?: "",
            messageSendingState = when {
                item.isRead -> MessageSendingState.Read
                item.outgoing -> MessageSendingState.Deliver
                else -> MessageSendingState.Sent
            },
            sentTimestamp = item.sentDate,
            editTimestamp = item.editDate,
            displayType = when {
                item.conversationType_ == "https://xabber.com/protocol/groups#system-message" -> MessageDisplayType.System
                item.body.isNullOrEmpty() && item.references.isNotEmpty() -> MessageDisplayType.Images
                else -> MessageDisplayType.Text
            },
            canEditMessage = item.outgoing,
            canDeleteMessage = item.outgoing,
            urlAvatar = null,
            isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
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
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to map MessageStorageItem to MessageDto: primary=${item.primary}, messageId=${item.messageId}, error=${e.message}")
        null
    }

    private suspend fun mapAllMessages(): List<MessageDto> {
        return realm.write {
            query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                owner, opponent, conversationType.rawValue
            ).sort("sentDate", Sort.ASCENDING).find() // Sort by sentDate DESC
                .mapNotNull { it.toMessageDto() }
        }
    }

    suspend fun updateMessagesAndUnread(messages: List<MessageDto>) {
        messageListMutex.withLock {
            messageList.clear()
            messageList.addAll(messages)
        }
        _messages.postValue(messages)
        _unreadCount.postValue(messages.count { it.isUnread })

    }

    suspend fun loadInitialData() {
        _showSkeletonObserver.postValue(true)
        val initialMessages = mapAllMessages()  // Now ascending
        messageListMutex.withLock {
            messageList.clear()
            messageList.addAll(initialMessages)
            withContext(Dispatchers.Main) {
                _messages.value = messageList
                _unreadCount.value = messageList.count { it.isUnread }
            }
        }
        _showSkeletonObserver.postValue(false)
    }

    fun initMessagesListener(owner: String, opponentJid: String) {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            val query = realm.query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                owner, opponentJid, conversationType.rawValue
            ).sort("sentDate", Sort.ASCENDING)  // Changed to ASCENDING

            query.asFlow().collect { changes: ResultsChange<MessageStorageItem> ->
                val messages = when (changes) {
                    is InitialResults -> changes.list.mapNotNull { it.toMessageDto() }
                    is UpdatedResults -> changes.list.mapNotNull { it.toMessageDto() }
                }

                messageListMutex.withLock {
                    messageList.clear()
                    messageList.addAll(messages)
                }

                withContext(Dispatchers.Main) {
                    _messages.value = messages
                    _unreadCount.value = messages.count { it.isUnread }
                }
            }
        }
    }

    suspend fun updateMessageList(messages: List<MessageStorageItem>) {
        try {
            val list = ArrayList<MessageDto>()
            realm.write {
                list.addAll(messages.mapNotNull { item ->
                    mapMessageStorageItemToDto(item)
                })
            }
            messageListMutex.withLock {
                if (list != messageList || list.isNotEmpty()) {
                    val newMessages = list.filter { m ->
                        !messageList.any { it.primary == m.primary || (it.archivedId == m.archivedId && m.archivedId.isNotEmpty()) }
                    }
                    if (newMessages.isNotEmpty()) {

                        messageList.addAll(newMessages)
                        messageList.sortBy { it.sentTimestamp }  // Retained ascending sort after insert
                    }
                    count = messageList.count { it.isUnread }

                    withContext(Dispatchers.Main) {
                        _messages.value = messageList
                        _unreadCount.value = count
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message list: ${e.message}", e)
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageDto>) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                var lastMessageId = chat?.lastMessageId
                messages.forEach { messageDto ->
                    if (messageDto.archivedId.isEmpty() || messageDto.owner.isEmpty()) {
                        Log.w(TAG, "Skipping message with invalid archivedId or owner: archivedId=${messageDto.archivedId}, owner=${messageDto.owner}")
                        return@forEach
                    }

                    var primary = MessageStorageItem.genPrimary(messageDto.archivedId, messageDto.owner)
                    if (primary.isEmpty() || primary == "_${messageDto.owner}") {
                        Log.w(TAG, "Skipping message with invalid primary: archivedId=${messageDto.archivedId}, owner=${messageDto.owner}, primary=$primary")
                        return@forEach
                    }

                    val existing = query<MessageStorageItem>(
                        "primary = $0 OR (archivedId = $1 AND archivedId != '' AND conversationType_ = $2)",
                        primary, messageDto.archivedId, conversationType.rawValue
                    ).first().find()

                    if (existing != null && messageDto.archivedId != lastMessageId) {
                        return@forEach
                    }

//                    if (messageDto.messageBody.isEmpty() && messageDto.references.isEmpty()) {
//                        Log.d(TAG, "Skipping message with no body or references: primary=$primary, archivedId=${messageDto.archivedId}")
//                        return@forEach
//                    }

                    var references = realmListOf<MessageReferenceStorageItem>()
                    messageDto.references.forEach { ref ->
                        val refItem = copyToRealm(MessageReferenceStorageItem().apply {
                            this.primary = "${ref.id}_${System.currentTimeMillis()}"
                            uri = ref.uri
                            mimeType = ref.mimeType
                            isGeo = ref.isGeo
                            latitude = ref.latitude
                            longitude = ref.longitude
                            isAudioMessage = ref.isVoiceMessage
                            fileName = ref.fileName
                            fileSize = ref.size
                        })
                        references.add(refItem)
                    }

                    val bareOpponentJid = messageDto.opponentJid.removeSuffix("/${messageDto.opponentJid.substringAfterLast("/")}")
                    val validOwner = messageDto.owner.ifEmpty { this@ChatViewModel.owner } // Fallback to ViewModel owner
                    if (validOwner.isEmpty()) {
                        Log.e(TAG, "Skipping message insertion: empty owner for opponent=$bareOpponentJid")
                        return@forEach
                    }
                    val messageConversationType = if (messageDto.isGroup) ConversationType.Group else conversationType
                    val chatPrimary = LastChatsStorageItem.genPrimary(bareOpponentJid, validOwner, messageConversationType) // Now with valid owner
                    if (chatPrimary.isEmpty()) {
                        Log.w(TAG, "Skipping LastChatsStorageItem creation: invalid chatPrimary for jid=$bareOpponentJid, owner=$validOwner, type=${messageConversationType.rawValue}")
                        return@forEach
                    }
                    val message = copyToRealm(MessageStorageItem().apply {
                        this.primary = primary
                        owner = messageDto.owner
                        opponent = bareOpponentJid
                        body = messageDto.messageBody
                        date = messageDto.sentTimestamp
                        sentDate = messageDto.sentTimestamp
                        editDate = messageDto.editTimestamp
                        outgoing = messageDto.isOutgoing
                        isRead = !messageDto.isUnread
                        references = references
                        conversationType_ = messageConversationType.rawValue
                        archivedId = messageDto.archivedId
                        state = messageDto.messageSendingState
                        messageId = messageDto.archivedId
                    })

                    // Update or create LastChatsStorageItem
                    val existingChats = query<LastChatsStorageItem>(
                        "jid = $0 AND owner = $1", bareOpponentJid, messageDto.owner
                    ).find()
                    var targetChat: LastChatsStorageItem? = existingChats.find { it.conversationType_ == messageConversationType.rawValue }

                    if (targetChat == null && existingChats.isNotEmpty()) {
                        targetChat = existingChats.firstOrNull()
                        if (targetChat != null && messageDto.isGroup) {
                            findLatest(targetChat)?.apply {
                                conversationType_ = ConversationType.Group.rawValue
                            }
                        }
                    }

                    if (chatPrimary.isEmpty()) {
                        Log.w(TAG, "Skipping LastChatsStorageItem creation: invalid chatPrimary for jid=$bareOpponentJid, owner=${messageDto.owner}, type=${messageConversationType.rawValue}")
                        return@forEach
                    }

                    if (targetChat != null) {
                        findLatest(targetChat)?.apply {
                            if (message.sentDate > messageDate) {
                                lastMessage = message
                                messageDate = message.sentDate
                                lastMessageId = message.messageId
                                if (!messageDto.isOutgoing && muteExpired <= 0) {
                                    isArchived = false
                                    unread = (unread ?: 0) + if (messageDto.isUnread) 1 else 0
                                }
                            }
                        }
                    } else {
                        copyToRealm(LastChatsStorageItem().apply {
                            primary = chatPrimary
                            owner = messageDto.owner
                            jid = bareOpponentJid
                            conversationType_ = messageConversationType.rawValue
                            messageDate = message.sentDate
                            isSynced = true
                            isInitialArchiveLoaded = true
                            lastMessage = message
                            lastMessageId = message.messageId
                            if (!messageDto.isOutgoing && muteExpired <= 0) {
                                isArchived = false
                                unread = if (messageDto.isUnread) 1 else 0
                            }
                        }, UpdatePolicy.ALL)
                    }
                }
            }

            messageListMutex.withLock {
                val newMessages = messages.filter { m ->
                    !messageList.any { it.primary == m.primary || (it.archivedId == m.archivedId && m.archivedId.isNotEmpty()) }
                }
                if (newMessages.isNotEmpty()) {
                    messageList.addAll(newMessages)
                    messageList.sortBy { it.sentTimestamp }
                    count = messageList.count { it.isUnread }
                    withContext(Dispatchers.Main) {
                        _messages.postValue(messageList)
                        _unreadCount.postValue(count)
                    }
                } else {
                }
            }
        }
    }

    fun debugMessageList() {
        messageList.forEachIndexed { index, msg ->
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
        val lastChatsStorageItem = realm.query<LastChatsStorageItem>("primary = '$chatId'").first().find()
        val owner = lastChatsStorageItem?.owner
        val opponent = lastChatsStorageItem?.jid
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query<MessageStorageItem>(
                "owner = '$owner' AND opponent = '$opponent' AND conversationType_ = $0 AND isDeleted = false",
                conversationType.rawValue
            ).sort("date", Sort.DESCENDING).find()
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
            insertMessagesFromReceiver(listOf(messageDto))
        }
    }

    suspend fun selectMessage(primary: String, checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (checked) {
                selectedItems.add(primary)
            } else {
                selectedItems.remove(primary)
            }
            val position = messageList.indexOfFirst { it.primary == primary }
            if (position != -1) {
                val updatedMessage = messageList[position].copy(isSelected = checked, isChecked = checked)
                messageListMutex.withLock {
                    messageList[position] = updatedMessage
                }
                withContext(Dispatchers.Main) {
                    _selectedCount.value = selectedItems.size
                    _messages.value = messageList
                }
            }
        }
    }

    fun setMute(id: String, mute: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.muteExpired = mute
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
        return messageList.indexOfFirst { it.primary == primary }
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
                isChecked = selectedItems.contains(item.primary),
                archivedId = item.archivedId
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
                    if (unreadMessages.isNotEmpty()) {
                        unreadMessages.forEach { it.isRead = true }
                    }
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
            if (forAll) {
                // TODO: Implement server request to delete messages
            }
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

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        realm.close()
    }
    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }
}