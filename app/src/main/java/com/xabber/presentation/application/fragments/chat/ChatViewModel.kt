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
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private var job: Job? = null

    private val _opponentName = MutableLiveData<String>()
    val opponentName: LiveData<String> = _opponentName

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired

    private var messageList = ArrayList<MessageDto>()
    var a = 11
    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int> = _selectedCount
    private val selectedItems = HashSet<String>()
    private var test = 0
    private var count = 0

    init {
        initChatDataListener(chatId)
        initMessagesListener(owner, opponent)
        markAllMessageUnread(chatId)
    }

    fun initMessagesListener(owner: String, opponentJid: String) {
        val request = realm.query(MessageStorageItem::class, "owner = '$owner' AND opponent = '$opponentJid'")
        val lastChatsFlow = request.asFlow().debounce(100).distinctUntilChanged() // Debounce and prevent duplicate emissions
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
        realm.write {
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
                    isChecked = selectedItems.contains(item.primary)
                )
            })
        }
        count = list.count { it.isUnread }
        if (list != messageList) {
            messageList = list
            messageList.sortBy { it.sentTimestamp }
            Log.d("ChatViewModel", "Updating messages LiveData: ${list.size} messages, $count unread, messageListSize=${messageList.size}")
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageList
                _unreadCount.value = count
            }
        } else {
            Log.d("ChatViewModel", "No change in messages, skipping LiveData update, messageListSize=${messageList.size}")
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
            val realmList = realm.query(MessageStorageItem::class, "owner = '$owner' AND opponent = '$opponent'").find()
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
            val rreferences = realmListOf<MessageReferenceStorageItem>()
            realm.writeBlocking {
                for (i in 0 until messageDto.references.size) {
                    val ref = this.copyToRealm(MessageReferenceStorageItem().apply {
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
                val message = this.copyToRealm(MessageStorageItem().apply {
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
                val item = this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                item?.lastMessage = message
                item?.messageDate = message.date
                if (!messageDto.isOutgoing && item?.muteExpired ?: 0 <= 0) {
                    item?.isArchived = false
                    item?.unread = (item?.unread ?: 0) + 1
                }
                Log.d("ChatViewModel", "Inserted message: primary=${message.primary}, body=${message.body.take(50)}, isRead=${message.isRead}, opponent=${message.opponent}")
            }
        }
    }

    fun insertMessagesFromReceiver(messages: List<MessageDto>) {
        Log.d("ChatViewModel", "insertMessagesFromReceiver called with ${messages.size} messages for chatId=$chatId, current messageListSize=${messageList.size}")
        viewModelScope.launch(Dispatchers.IO) {
            val list = messageList.toMutableList()
            list.addAll(messages)
            count = list.count { it.isUnread }
            messageList = list as ArrayList<MessageDto>
            messageList.sortBy { it.sentTimestamp }
            Log.d("ChatViewModel", "After insertMessagesFromReceiver, messageListSize=${messageList.size}")
            viewModelScope.launch(Dispatchers.Main) {
                _messages.value = messageList
                _unreadCount.value = count
            }
        }
    }



    @RequiresApi(Build.VERSION_CODES.O)
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
                    </query>
                </iq>
            """.trimIndent()
            Log.d("ChatViewModel", "Sending MAM query for JID: $opponentJid, queryId: $queryId")
            AccountManager.find(AccountStorageItem().jid)?.stream?.socket?.write(mamQuery)?.also { success ->
                if (success) {
                    Log.d("ChatViewModel", "MAM query sent successfully")
                } else {
                    Log.e("ChatViewModel", "Failed to send MAM query")
                }
            }
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
            Log.d("ChatViewModel", "Selected message: primary=$primary, checked=$checked, selectedItems=$selectedItems, messageListSize=${messageList.size}")
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
            Log.d("ChatViewModel", "Cleared selection, messageListSize=${messageList.size}")
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
        test++
        Log.d("iii", "test = $test")
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

    fun getSelectedText(): String {
        var text = ""
        val selected = ArrayList(selectedItems)
        realm.writeBlocking {
            selected.forEach { primary ->
                val message = query(MessageStorageItem::class, "primary = '$primary'").first().find()
                if (message != null) text += "${message.body}\n"
                Log.d("iii", "getSelectedText: $text")
            }
        }
        Log.d("iii", "Final selected text: $text")
        return text
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
                val chat = query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                chat?.lastMessage = null
                chat?.lastPosition = ""
                chat?.unread = 0
            }
        }
    }

    fun deleteChat(id: String) {
        job?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) findLatest(item)?.let { delete(item) }
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
            realm.write {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                Log.d("item", "$item")
                item?.muteExpired = mute
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // realm.close()
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

    fun getMessagePosition(primary: String): Int {
        return messageList.indexOfFirst { it.primary == primary }
    }

    fun getMessageId(): String {
        val selected = ArrayList(selectedItems)
        return if (selected.isNotEmpty()) selected[0] else ""
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
                item.conversationType_ == "https://xabber.com/protocol/groups",
                references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                isUnread = !item.isRead,
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
                        chat.unread = 0
                        Log.d("ChatViewModel", "Marked all messages as read for chatId=$chatId, owner=$owner, opponent=$opponent, updated ${unreadMessages.size} messages")
                    } else {
                        Log.d("ChatViewModel", "No unread messages to mark for chatId=$chatId")
                    }
                } else {
                    Log.w("ChatViewModel", "No chat found for chatId=$chatId")
                }
            }
        }
    }

    fun getForwardMessagesText(): String {
        Log.d("yyy", "selectedItems = $selectedItems")
        val selected = ArrayList(selectedItems)
        var text = ""
        realm.writeBlocking {
            selected.forEach { id ->
                val item = query(MessageStorageItem::class, "primary = '$id'").first().find()
                if (item != null) text += "${if (item.outgoing) item.owner else item.opponent}\n${item.body}\n"
            }
        }
        return text
    }
}