// ChatViewModel.kt
package com.xabber.presentation.application.fragments.chat

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.common.Account
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.O)
class ChatViewModel(
    private val chatId: String,
    private val owner: String,
    private val opponent: String,
    private val conversationType: ConversationType
) : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())
    private val _chat = MutableLiveData<ChatListDto?>()
    val chat: LiveData<ChatListDto?> = _chat
    private val _messages = MutableLiveData<List<MessageDto>>()
    val messages: LiveData<List<MessageDto>> = _messages
    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName
    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount
    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading
    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int> = _selectedCount
    private val _pinnedDate = MutableLiveData<String>()
    val pinnedDate: LiveData<String> = _pinnedDate
    private val selectedItems = HashSet<String>()
    private var messageList = ArrayList<MessageDto>()
    private var minIndex: Int = 0
    private var maxIndex: Int = 50
    private val pageSize: Int = 50
    private var job: Job? = null
    private var isLoading = false
    private val TAG = "ChatViewModel"

    init {
        initChatDataListener(chatId)
        initMessagesListener(owner, opponent)
        markAllMessagesRead(chatId)
    }

    fun initMessagesListener(owner: String, opponentJid: String) {
        val request = realm.query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
            owner, opponentJid, conversationType.rawValue
        )
        viewModelScope.launch(Dispatchers.IO) {
            request.asFlow().debounce(100).distinctUntilChanged().collect { changes: ResultsChange<MessageStorageItem> ->
                when (changes) {
                    is UpdatedResults -> updateMessageList(changes.list)
                    else -> {}
                }
            }
        }
    }

    private suspend fun updateMessageList(messages: List<MessageStorageItem>) {
        // Limit the number of messages processed to prevent memory issues
        val limitedMessages = messages.take(pageSize * 2) // Process up to 2 pages to reduce memory usage
        val list = ArrayList<MessageDto>()
        var unreadId: String? = null
        withContext(Dispatchers.IO) {
            realm.write {
                list.addAll(limitedMessages.map { item ->
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
                        canEditMessage = item.outgoing && item.archivedId.isNotEmpty(),
                        canDeleteMessage = item.outgoing && listOf<MessageSendingState>(
                            MessageSendingState.Deliver,
                            MessageSendingState.Read
                        ).contains(item.state),
                        urlAvatar = null,
                        isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
                        kind = null,
                        isSelected = selectedItems.contains(item.primary),
                        references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                        isUnread = !item.isRead,
                        isChecked = selectedItems.contains(item.primary),
                        archivedId = item.archivedId
                    )
                })
                val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                unreadId = if (chat?.unread == 0) null else chat?.lastReadId
            }
        }
        val count = list.count { it.isUnread }
        if (list != messageList) {
            messageList = ArrayList(list.distinctBy { it.primary }.sortedBy { it.sentTimestamp })
            val finalList = addMarkersAndSeparators(messageList, unreadId)
            _messages.postValue(finalList)
            _unreadCount.postValue(count)
            updatePinnedDate()
            Log.d(TAG, "Updated messages: size=${messageList.size}, unread=$count")
        }
    }

    private fun addMarkersAndSeparators(messages: List<MessageDto>, unreadId: String?): List<MessageDto> {
        val result = mutableListOf<MessageDto>()
        messages.forEachIndexed { index, message ->
            result.add(message)
            if (unreadId != null && message.archivedId == unreadId) {
                result.add(
                    MessageDto(
                        primary = "${message.primary}_unread",
                        isOutgoing = message.isOutgoing,
                        owner = message.owner,
                        opponentJid = message.opponentJid,
                        messageBody = "Unread messages",
                        messageSendingState = MessageSendingState.None,
                        sentTimestamp = message.sentTimestamp,
                        displayType = MessageDisplayType.System,
                        canEditMessage = false,
                        canDeleteMessage = false,
                        urlAvatar = null,
                        isGroup = message.isGroup,
                        kind = null,
                        isSelected = false,
                        references = ArrayList(),
                        isUnread = false,
                        isChecked = false,
                        archivedId = "${message.archivedId}_unread"
                    )
                )
            }
            if (index < messages.size - 1) {
                val nextMessage = messages[index + 1]
                if (!isSameDay(message.sentTimestamp, nextMessage.sentTimestamp)) {
                    result.add(
                        MessageDto(
                            primary = "${message.primary}_date",
                            isOutgoing = message.isOutgoing,
                            owner = message.owner,
                            opponentJid = message.opponentJid,
                            messageBody = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(message.sentTimestamp)),
                            messageSendingState = MessageSendingState.None,
                            sentTimestamp = message.sentTimestamp,
                            displayType = MessageDisplayType.System,
                            canEditMessage = false,
                            canDeleteMessage = false,
                            urlAvatar = null,
                            isGroup = message.isGroup,
                            kind = null,
                            isSelected = false,
                            references = ArrayList(),
                            isUnread = false,
                            isChecked = false,
                            archivedId = "${message.archivedId}_date"
                        )
                    )
                }
            }
        }
        return result
    }

    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { time = Date(timestamp1) }
        val cal2 = Calendar.getInstance().apply { time = Date(timestamp2) }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun updatePinnedDate() {
        val messages = messageList
        if (messages.size < 5) {
            _pinnedDate.postValue("")
            return
        }
        val firstVisibleIndex = minOf(messages.size - 1, maxIndex - 1)
        if (firstVisibleIndex >= 0) {
            val dateString = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(messages[firstVisibleIndex].sentTimestamp))
            _pinnedDate.postValue(dateString)
        }
    }

    fun initChatDataListener(chatId: String) {
        val request = realm.query<LastChatsStorageItem>("primary = $0", chatId).find()
        job = viewModelScope.launch(Dispatchers.IO) {
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val chat = changes.list.firstOrNull()?.toChatListDto()
                        withContext(Dispatchers.Main) {
                            _chat.value = chat
                            if (chat != null) {
                                _muteExpired.value = chat.muteExpired
                                _opponentName.value = chat.getChatName()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun loadInitialMessages(callback: (List<MessageDto>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            val chat = realm.query<LastChatsStorageItem>("primary = $0", chatId).first().find()
            if (chat?.isSynced != true) {
                val account = AccountManager.find(owner)
                account?.stream?.let { stream ->
                    MessageArchiveManager(owner).syncChat(
                        stream = stream,
                        jid = opponent,
                        conversationType = conversationType,
                        callback = {
                            viewModelScope.launch(Dispatchers.IO) {
                                val messages = realm.query<MessageStorageItem>(
                                    "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                                    owner, opponent, conversationType.rawValue
                                ).find()
                                updateMessageList(messages.take(pageSize))
                                callback(messageList)
                                _loading.postValue(false)
                            }
                        }
                    )
                } ?: run {
                    callback(emptyList())
                    _loading.postValue(false)
                }
            } else {
                val messages = realm.query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                    owner, opponent, conversationType.rawValue
                ).find()
                updateMessageList(messages.take(pageSize))
                callback(messageList)
                _loading.postValue(false)
            }
        }
    }

    fun loadPreviousMessages(callback: (List<MessageDto>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            val messages = realm.query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                owner, opponent, conversationType.rawValue
            ).find()
            val chat = realm.query<LastChatsStorageItem>("primary = $0", chatId).first().find()
            if (minIndex <= 0 && messages.isNotEmpty() && !(chat?.fullArchiveLoaded ?: true)) {
                val oldestMessage = messageList.firstOrNull()
                if (oldestMessage != null) {
                    val account = AccountManager.find(owner)
                    account?.stream?.let { stream ->
                        MessageArchiveManager(owner).getPrevHistory(
                            stream = stream,
                            jid = opponent,
                            conversationType = conversationType,
                            messageId = oldestMessage.archivedId
                        ) {
                            viewModelScope.launch(Dispatchers.IO) {
                                minIndex = maxOf(0, minIndex - pageSize)
                                updateMessageList(messages.subList(maxOf(0, minIndex), maxIndex))
                                callback(messageList)
                                _loading.postValue(false)
                            }
                        }
                    } ?: run {
                        callback(emptyList())
                        _loading.postValue(false)
                    }
                } else {
                    callback(emptyList())
                    _loading.postValue(false)
                }
            } else {
                minIndex = maxOf(0, minIndex - pageSize)
                updateMessageList(messages.subList(maxOf(0, minIndex), maxIndex))
                callback(messageList)
                _loading.postValue(false)
            }
        }
    }

    fun loadNextMessages(callback: (List<MessageDto>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            val messages = realm.query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                owner, opponent, conversationType.rawValue
            ).find()
            val chat = realm.query<LastChatsStorageItem>("primary = $0", chatId).first().find()
            if (maxIndex < messages.size || !chat?.fullArchiveLoaded!!) {
                val newestMessage = messageList.lastOrNull()
                if (chat != null) {
                    if (newestMessage != null && !chat.fullArchiveLoaded) {
                        val account = AccountManager.find(owner)
                        account?.stream?.let { stream ->
                            MessageArchiveManager(owner).getNextHistory(
                                stream = stream,
                                jid = opponent,
                                conversationType = conversationType,
                                messageId = newestMessage.archivedId
                            ) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    maxIndex = minOf(maxIndex + pageSize, messages.size)
                                    updateMessageList(messages.subList(minIndex, maxIndex))
                                    callback(messageList)
                                    _loading.postValue(false)
                                }
                            }
                        } ?: run {
                            callback(emptyList())
                            _loading.postValue(false)
                        }
                    } else {
                        maxIndex = minOf(maxIndex + pageSize, messages.size)
                        updateMessageList(messages.subList(minIndex, maxIndex))
                        callback(messageList)
                        _loading.postValue(false)
                    }
                }
            } else {
                callback(emptyList())
                _loading.postValue(false)
            }
        }
    }

    fun getDraft(id: String): String? {
        var drafted: String? = null
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<LastChatsStorageItem>("primary = $0", id).first().find()
                drafted = item?.draftMessage
            }
        }
        return drafted
    }

    fun getContactId(id: String): String? {
        var contactPrimary: String? = null
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<LastChatsStorageItem>("primary = $0", id).first().find()
                contactPrimary = item?.rosterItem?.primary
            }
        }
        return contactPrimary
    }

    fun loadChat(chatId: String): ChatListDto? {
        var chatListDto: ChatListDto? = null
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                if (chat != null) chatListDto = chat.toChatListDto()
            }
        }
        return chatListDto
    }

    fun getMessageList(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val messages = realm.query<MessageStorageItem>(
                "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                owner, opponent, conversationType.rawValue
            ).find()
            updateMessageList(messages)
        }
    }

    fun getAccount(id: String): AccountDto? {
        var account: AccountDto? = null
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val acc = query<AccountStorageItem>("jid = $0", id).first().find()
                account = acc?.toAccountDto()
            }
        }
        return account
    }

    fun queryRecentMessages(opponentJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val queryId = UUID.randomUUID().toString()
            val mamQuery = """
                <iq type='set' id='$queryId'>
                    <query xmlns='urn:xmpp:mam:2'>
                        <x xmlns='jabber:x:data' type='submit'>
                            <field var='FORM_TYPE' type='hidden'>
                                <value>urn:xmpp:mam:2</value>
                            </field>
                            <field var='with'>
                                <value>$opponentJid</value>
                            </field>
                        </x>
                        <set xmlns='http://jabber.org/protocol/rsm'>
                            <max>50</max>
                        </set>
                        <flip-page/>
                    </query>
                </iq>
            """.trimIndent()
            Log.d(TAG, "Sending MAM query for JID: $opponentJid, queryId: $queryId")
            AccountManager.find(AccountStorageItem().jid)?.stream?.socket?.write(mamQuery)?.also { success ->
                if (success) Log.d(TAG, "MAM query sent successfully") else Log.e(TAG, "Failed to send MAM query")
            }
        }
    }

    fun insertMessage(chatId: String, messageDto: MessageDto) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val existing = query<MessageStorageItem>("primary = $0", messageDto.primary).first().find()
                if (existing != null) {
                    Log.d(TAG, "Skipping duplicate message insertion: primary=${messageDto.primary}")
                    return@write
                }
                val rreferences = realmListOf<MessageReferenceStorageItem>()
                messageDto.references.forEach { ref ->
                    rreferences.add(copyToRealm(MessageReferenceStorageItem().apply {
                        primary = "${ref.id}_${System.currentTimeMillis()}"
                        uri = ref.uri
                        mimeType = ref.mimeType
                        isGeo = ref.isGeo
                        latitude = ref.latitude
                        longitude = ref.longitude
                        isAudioMessage = ref.isVoiceMessage
                        fileName = ref.fileName
                        fileSize = ref.size
                    }))
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
                    state_ = messageDto.messageSendingState.rawValue
                    archivedId = messageDto.archivedId
                })
                val item = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                if (item != null) {
                    findLatest(item)?.apply {
                        lastMessage = message
                        messageDate = message.date
                        if (!messageDto.isOutgoing && muteExpired <= 0) {
                            isArchived = false
                            unread = (unread ?: 0) + 1
                        }
                    }
                }
            }
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageDto>) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val insertedMessages = mutableListOf<MessageDto>()
                messages.take(pageSize).forEach { messageDto -> // Limit to pageSize to reduce memory usage
                    val existing = query<MessageStorageItem>("primary = $0", messageDto.primary).first().find()
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate message from receiver: primary=${messageDto.primary}")
                        return@forEach
                    }
                    val rreferences = realmListOf<MessageReferenceStorageItem>()
                    messageDto.references.forEach { ref ->
                        rreferences.add(copyToRealm(MessageReferenceStorageItem().apply {
                            primary = "${ref.id}_${System.currentTimeMillis()}"
                            uri = ref.uri
                            mimeType = ref.mimeType
                            isGeo = ref.isGeo
                            latitude = ref.latitude
                            longitude = ref.longitude
                            isAudioMessage = ref.isVoiceMessage
                            fileName = ref.fileName
                            fileSize = ref.size
                        }))
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
                        state_ = messageDto.messageSendingState.rawValue
                        archivedId = messageDto.archivedId
                    })
                    insertedMessages.add(messageDto)
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
                }
            }
            updateMessageList(
                realm.query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                    owner, opponent, conversationType.rawValue
                ).find()
            )
        }
    }

    fun markAllMessagesRead(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                if (chat != null) {
                    val unreadMessages = query<MessageStorageItem>(
                        "isRead = false AND owner = $0 AND opponent = $1 AND conversationType_ = $2",
                        owner, opponent, conversationType.rawValue
                    ).find()
                    if (unreadMessages.isNotEmpty()) {
                        unreadMessages.forEach { it.isRead = true }
                        findLatest(chat)?.unread = 0
                    }
                }
            }
        }
    }

    fun selectMessage(primary: String, checked: Boolean) {
        if (checked) selectedItems.add(primary) else selectedItems.remove(primary)
        val position = messageList.indexOfFirst { it.primary == primary }
        if (position != -1) {
            messageList[position] = messageList[position].copy(isSelected = checked, isChecked = checked)
            _selectedCount.postValue(selectedItems.size)
            _messages.postValue(messageList)
        }
    }

    fun clearAllSelected() {
        if (selectedItems.isNotEmpty()) {
            messageList = ArrayList(messageList.map { it.copy(isSelected = false, isChecked = false) })
            selectedItems.clear()
            _selectedCount.postValue(0)
            _messages.postValue(messageList)
        }
    }

    fun isOutgoing(): Boolean {
        if (selectedItems.size != 1) return false
        val primary = selectedItems.first()
        var out = false
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<MessageStorageItem>("primary = $0", primary).first().find()
                if (item != null && item.outgoing) out = true
            }
        }
        return out
    }

    fun deleteMessage(primary: String, forAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val message = query<MessageStorageItem>("primary = $0", primary).first().find()
                if (message != null) findLatest(message)?.let { delete(it) }
            }
            if (forAll) {
                // TODO: Implement server request to delete message
            }
        }
    }

    fun deleteMessages(forAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                selectedItems.forEach { primary ->
                    val message = query<MessageStorageItem>("primary = $0", primary).first().find()
                    if (message != null) findLatest(message)?.let { delete(it) }
                }
            }
            if (forAll) {
                // TODO: Implement server request to delete messages
            }
        }
    }

    fun getSelectedText(): String {
        var text = ""
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                selectedItems.forEach { primary ->
                    val message = query<MessageStorageItem>("primary = $0", primary).first().find()
                    if (message != null) text += "${message.body}\n"
                }
            }
        }
        return text
    }

    fun getSelectedMessageText(): String {
        var text = ""
        val selected = ArrayList(selectedItems)
        if (selected.isNotEmpty()) {
            val id = selected[0]
            viewModelScope.launch(Dispatchers.IO) {
                realm.write {
                    val item = query<MessageStorageItem>("primary = $0", id).first().find()
                    if (item != null) text = item.body
                }
            }
        }
        return text
    }

    fun getMessageId(): String {
        val selected = ArrayList(selectedItems)
        return if (selected.isNotEmpty()) selected[0] else ""
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val message = query<MessageStorageItem>("primary = $0", primary).first().find()
                if (message != null) {
                    findLatest(message)?.apply {
                        body = newBody
                        editDate = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun clearHistory(chatId: String, opponentJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val messages = query<MessageStorageItem>(
                    "opponent = $0 AND conversationType_ = $1",
                    opponentJid,
                    conversationType.rawValue
                ).find()
                delete(messages)
                val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                if (chat != null) {
                    findLatest(chat)?.apply {
                        lastMessage = null
                        lastPosition = ""
                        unread = 0
                    }
                }
            }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<LastChatsStorageItem>("primary = $0", id).first().find()
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
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<LastChatsStorageItem>("primary = $0", id).first().find()
                if (item != null) {
                    findLatest(item)?.apply {
                        draftMessage = draft
                        messageDate = if (!draft.isNullOrEmpty()) System.currentTimeMillis() else lastMessage?.date ?: 0
                    }
                }
            }
        }
    }

    fun setMute(id: String, mute: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<LastChatsStorageItem>("primary = $0", id).first().find()
                if (item != null) findLatest(item)?.muteExpired = mute
            }
        }
    }

    fun setUnread(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val message = query<MessageStorageItem>("primary = $0", id).first().find()
                if (message != null) findLatest(message)?.isRead = true
            }
        }
    }

    fun saveLastPosition(id: String, savedPosition: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<LastChatsStorageItem>("primary = $0", id).first().find()
                if (item != null) findLatest(item)?.lastPosition = savedPosition
            }
        }
    }

    fun getPositionMessage(lastPosition: String): Int {
        messageList.sortBy { it.sentTimestamp }
        return messageList.indexOfFirst { it.primary == lastPosition }
    }

    fun lastPositionPrimary(id: String): String {
        var lastPosition = ""
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query<LastChatsStorageItem>("primary = $0", id).first().find()
                if (item != null) lastPosition = item.lastPosition
            }
        }
        return lastPosition
    }

    fun getMessage(primary: String? = null): MessageDto? {
        val id = primary ?: selectedItems.firstOrNull() ?: return null
        return realm.query<MessageStorageItem>("primary = $0", id).first().find()?.let { item ->
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
                canEditMessage = item.outgoing && item.archivedId.isNotEmpty(),
                canDeleteMessage = item.outgoing && listOf<MessageSendingState>(
                    MessageSendingState.Deliver,
                    MessageSendingState.Read
                ).contains(item.state),
                urlAvatar = null,
                isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
                kind = null,
                isSelected = selectedItems.contains(item.primary),
                references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                isUnread = !item.isRead,
                isChecked = selectedItems.contains(item.primary),
                archivedId = item.archivedId
            )
        }
    }

    fun getMessagePosition(primary: String): Int = messageList.indexOfFirst { it.primary == primary }

    fun getForwardMessagesText(): String {
        var text = ""
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                selectedItems.forEach { id ->
                    val item = query<MessageStorageItem>("primary = $0", id).first().find()
                    if (item != null) text += "${if (item.outgoing) item.owner else item.opponent}\n${item.body}\n"
                }
            }
        }
        return text
    }

    override fun onCleared() {
        job?.cancel()
        realm.close()
        super.onCleared()
    }
}