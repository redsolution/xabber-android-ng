package com.xabber.presentation.application.fragments.chat

import android.util.Log
import androidx.lifecycle.*
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AccountDto
import com.xabber.models.dto.ChatListDto
import com.xabber.models.dto.MessageDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageDisplayType
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.messages.MessageStorageItem
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.models.xmpp.sync.ConversationType
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val _messages = MutableLiveData<ArrayList<MessageDto>>()
    val messages: LiveData<ArrayList<MessageDto>> = _messages


    private val _unreadMessages = MutableLiveData<Int>()
    val unreadMessage: LiveData<Int> = _unreadMessages
    var job: Job? = null

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

    companion object {

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                return ChatViewModel(
                ) as T
            }
        }
    }


    init {

    }

    fun initMessagesListener(opponentJid: String) {
        Log.d("chat", "инициализирован слушатель сообщений")
        val request = realm.query(MessageStorageItem::class, "opponent = '$opponentJid'")
        val lastChatsFlow = request.asFlow()
        viewModelScope.launch(Dispatchers.IO) {
            lastChatsFlow.collect { changes: ResultsChange<MessageStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        Log.d("chat", "MESSAGE VM CHANGE")
                        val list = ArrayList<MessageDto>()
                        var count = 0
                        list.addAll(changes.list.map { T ->
                            MessageDto(
                                T.primary,
                                T.outgoing,
                                T.owner,
                                T.opponent,
                                T.body,
                                MessageSendingState.Read,
                                sentTimestamp = T.sentDate,
                                editTimestamp = T.editDate,
                                MessageDisplayType.Text,
                                true,
                                false,
                                null, // hasAttachment
                                false, // isSystemMessage
                                null, //isMentioned
                                false,
                                null,
                                isChecked = selectedItems.contains(T.primary),
                                isUnread = !T.isRead// почему дабл
                            )
                        })

                        count = 0
                        for (i in 0 until list.size) {
                            if (list[i].isUnread) count++
                        }


                        messageList = list
                        messageList.sort()
                        withContext(Dispatchers.Main) {
                            _messages.value = messageList
                            Log.d("mmm", "$count")
                            _unreadCount.value = count
                            Log.d("chat", "messages внутри слушателя сообщений ${_messages.value}")
                        }
                    }
                    else -> {}
                }
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
                        changes.list
                        val chat = changes.list[0]
                        withContext(Dispatchers.Main) {
                            _muteExpired.value = chat.muteExpired
                            _opponentName.value =
                                if (chat.rosterItem?.customNickname != "") chat.rosterItem?.customNickname else chat.rosterItem?.nickname
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

    fun getContactPrimary(id: String): String? {
        var contactPrimary: String? = null
        viewModelScope.launch {
            realm.writeBlocking {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                contactPrimary = item?.rosterItem?.primary
            }
        }
        return contactPrimary
    }

    fun getChat(chatId: String): ChatListDto? {
        var chatListDto: ChatListDto? = null
        viewModelScope.launch {
            realm.writeBlocking {
                val chat =
                    realm.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                if (chat != null) chatListDto = ChatListDto(
                    id = chat.primary,
                    owner = chat.owner,
                    opponentJid = chat.opponentJid,
                    muteExpired = chat.muteExpired,
                    isArchived = chat.isArchived,
                    isSynced = chat.isSynced,
                    draftMessage = chat.draftMessage,
                    lastMessageBody = if (chat.lastMessage != null) chat.lastMessage!!.body else "",
                    lastMessageDate = if (chat.lastMessage != null) chat.lastMessage!!.date else 0L,
                    lastMessageState = if (chat.lastMessage != null) MessageSendingState.Read else MessageSendingState.None,
                    colorId = chat.color,
                    opponentNickname = chat.rosterItem!!.nickname,
                    drawableId = chat.avatar,
                    status = ResourceStatus.Chat,
                    entity = RosterItemEntity.Contact,
                    lastMessageIsOutgoing = if (chat.lastMessage != null) chat.lastMessage!!.outgoing else false,
                    customNickname = if (chat.rosterItem != null) chat.rosterItem!!.customNickname else "",
                lastPosition = chat.lastPosition
                )
            }
        }
        Log.d("iii", " chatlistDto in ViewModel $chatListDto")
        return chatListDto
    }

    fun getMessageList(opponentJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(MessageStorageItem::class, "opponent = '$opponentJid'").find()
            val list = ArrayList<MessageDto>()
            list.addAll(realmList.map { T ->
                MessageDto(
                    T.primary,
                    T.outgoing,
                    T.owner,
                    T.opponent,
                    T.body,
                    MessageSendingState.Read,
                    T.sentDate,
                    editTimestamp = T.editDate,
                    MessageDisplayType.Text,
                    true,
                    false,
                    null, // hasAttachment
                    false, // isSystemMessage
                    null, //isMentioned
                    false,
                    null,// почему дабл
                    isChecked = selectedItems.contains(T.primary),
                    isUnread = !T.isRead
                )

            })
            var count = 0
            for (i in 0 until list.size) {
                if (list[i].isUnread) count++
            }

            Log.d("iii", "selecteditems = $selectedItems")
            messageList = list
            messageList.sort()
            withContext(Dispatchers.Main) {
                _messages.value = messageList
                _unreadCount.value = count
            }
        }
    }

    fun getAccount(): AccountDto? {
        var account: AccountDto? = null
        realm.writeBlocking {
        val acc =    this.query(AccountStorageItem::class).first().find()
            account =  if (acc != null)AccountDto(id = acc!!.primary, jid= acc!!.jid, colorKey = acc.colorKey, nickname = acc.nickname) else null
        }
        return account
    }

    fun insertMessage(chatId: String, messageDto: MessageDto, isReaded: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val message = copyToRealm(MessageStorageItem().apply {
                    primary = messageDto.primary
                    owner = messageDto.owner
                    opponent = messageDto.opponentJid
                    body = messageDto.messageBody
                    date = messageDto.sentTimestamp
                    sentDate = messageDto.sentTimestamp
                    editDate = messageDto.editTimestamp
                    outgoing = messageDto.isOutgoing
                    isRead = messageDto.isOutgoing || isReaded
                    references = realmListOf()
                    conversationType_ = ConversationType.Channel.toString()
                })
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                item?.lastMessage = message
                item?.messageDate = message.date
                var oldValue = item?.unread ?: 0
                oldValue++
                item?.unread = if (messageDto.isOutgoing || isReaded) 0 else oldValue
                item?.lastMessage?.outgoing = messageDto.isOutgoing
                if (item != null) {
                    if (!messageDto.isOutgoing && item.muteExpired <= 0) item.isArchived = false
                }
                Log.d("yyy", "item unread = ${item?.unread}")
            }
        }
    }

    fun selectMessage(primary: String, checked: Boolean) {
        if (checked) {
            selectedItems.add(primary)
        } else {
            selectedItems.remove(primary)
        }
        _selectedCount.value = selectedItems.size
    }

    fun clearAllSelected() {
        selectedItems.clear()
        Log.d("iii", "clear")
    }

    fun isOutgoing(): Boolean {
        var out = false
        if (selectedItems.size == 1) {
            val l = arrayListOf<String>()
            l.addAll(selectedItems)
            val primary = l[0]
            realm.writeBlocking {
                val item = realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                    .find()
                if (item != null && item.outgoing) out = true
            }
        }
        return out
    }

    fun deleteMessage(primary: String, forAll: Boolean) {
        test++
        Log.d("iii", "test = $test")
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val deletedMessage =
                    realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                        .find()
                if (deletedMessage != null) findLatest(deletedMessage)?.let { delete(it) }
            }
        }
        if (forAll) {
            // запрос на сервер удалить сообщение
        }
    }

    fun deleteMessages(forAll: Boolean) {
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                for (i in 0 until selected.size) {
                    val primary = selected[i]
                    val deletedMessage =
                        realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                            .find()
                    if (deletedMessage != null) findLatest(deletedMessage)?.let { delete(it) }
                }
            }
        }
        if (forAll) {
            // запрос на сервер удалить сообщение
        }
    }

    fun getSelectedText(): String {
        var text = ""
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)

        realm.writeBlocking {
            for (i in 0 until selected.size) {
                val primary = selected[i]
                val message =
                    realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                        .find()
                if (message != null) text += "${message}${message.body} \n"
                Log.d("iii", " inter $text")
            }
        }
        Log.d("iii", "$text")
        return text
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val editableMessage: MessageStorageItem? =
                    this.query(MessageStorageItem::class, "primary = '$primary'").first().find()
                editableMessage?.body = newBody
                editableMessage?.editDate = System.currentTimeMillis()
            }
        }
    }

    fun clearHistory(idChat: String, owner: String, opponentJid: String) {

        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val messages =
                    this.query(MessageStorageItem::class, "opponent = '$opponentJid'").find()
                delete(messages)

                val chat =
                    this.query(LastChatsStorageItem::class, "primary = '$idChat'").first().find()
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
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) findLatest(item)?.let { delete(item) }
            }
        }
    }

    fun insertChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val c = copyToRealm(LastChatsStorageItem().apply {
                    primary = id
                })
            }
        }
    }

    fun saveDraft(id: String, draft: String?) {
            realm.writeBlocking {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) {
                    findLatest(item).also {
                        val oldDraft = it?.draftMessage
                        if (oldDraft != draft) {
                            it?.draftMessage = draft
                            if (draft != null)
                                it?.messageDate = System.currentTimeMillis()
                        }
                    }
                }
            }
    }

    fun setMute(id: String, mute: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
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
            val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) {
                findLatest(item).also {
                   it?.lastPosition = savedPosition
                }
            }
        }
    }


    fun getPositionMessage(lastPosition: String): Int {
        Log.d("mmm", "messageList = $messageList")

        messageList.sort()
        var pos = 0
        for (i in 0 until messageList.size) {
            if (messageList[i].primary == lastPosition) pos = i

        }
        return pos
    }

    fun lastPositionPrimary(id: String): String {
        var lastPosition = ""
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) lastPosition = item.lastPosition
        }
        return lastPosition


    }

    fun getSelectedMessageText(): String {
        var text = ""
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        val id = selected[0]
        realm.writeBlocking {
            val item = this.query(MessageStorageItem::class, "primary = '$id'").first().find()
            if (item != null) text = item.body
        }
        return text
    }

    fun getMessageId(): String {
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        return selected[0]
    }

    fun setUnread(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val mes = this.query(MessageStorageItem::class, "primary = '$id").first().find()
                mes?.isRead = true
            }
        }
    }

    fun getMessage(primary: String? = null): MessageDto? {
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        val id = primary ?: selected[0]
        var message: MessageDto? = null
        realm.writeBlocking {
            val item = this.query(MessageStorageItem::class, "primary = '$id'").first().find()
            if (item != null) message = MessageDto(
                item.primary,
                item.outgoing,
                item.owner,
                item.opponent,
                item.body,
                MessageSendingState.Sent,
                item.sentDate,
                editTimestamp = item.editDate,
                MessageDisplayType.Text,
                true,
                false,
                null, // hasAttachment
                false, // isSystemMessage
                null, //isMentioned
                false,
                null,
                isChecked = selectedItems.contains(item.primary)
            )
        }
        return message
    }

    fun markAllMessageUnread(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val unreadMessages = this.query(MessageStorageItem::class, "isRead = false").find()
                unreadMessages.forEach { it.isRead = true }
                val chat =
                    this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                chat?.unread = 0
            }
        }

    }

    fun getForwardMessagesText(): String {
        Log.d("yyy", "selectedItems = $selectedItems")
        val selected = arrayListOf<String>()
        selected.addAll(selectedItems)
        var text = ""
        realm.writeBlocking {
            for (i in 0 until selected.size) {
                val id = selected[i]
                val item = this.query(MessageStorageItem::class, "primary = '$id'").first().find()
                if (item != null) text += if (item.outgoing) item.owner else item.opponent + "\n" + item.body
            }
        }
        return text
    }

    fun getColor(id: String): Int {
        var result = 0
        realm.writeBlocking {
            val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            if (item != null) result = item.color
        }
        return result
    }




    fun getUnreadMessages(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chat =
                realm.query(
                    LastChatsStorageItem::class,
                    "primary = '$id'"
                ).first().find()
            var count = chat?.unread

            withContext(Dispatchers.Main) {
                _unreadMessages.value = count!!
            }
        }
    }


    fun initUnreadMessagesCountListener(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(
                    LastChatsStorageItem::class,
                    "primary = '$id'"
                )
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        var count = 0
                        changes.list.forEach { count += it.unread }
                        withContext(Dispatchers.Main) { _unreadMessages.value = count }
                    }
                    else -> {}
                }
            }
        }
    }
}



