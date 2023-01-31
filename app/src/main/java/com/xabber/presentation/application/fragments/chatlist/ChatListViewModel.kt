package com.xabber.presentation.application.fragments.chatlist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.ChatListDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.messages.MessageStorageItem
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.models.xmpp.roster.RosterStorageItem
import com.xabber.models.xmpp.sync.ConversationType
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    var job: Job? = null

    private val _chats = MutableLiveData<ArrayList<ChatListDto>>()
    val chats: LiveData<ArrayList<ChatListDto>> = _chats
    var chatListDto = ArrayList<ChatListDto>()

    private val _archivedChats = MutableLiveData<ArrayList<ChatListDto>>()
    val archivedChats: LiveData<ArrayList<ChatListDto>> = _archivedChats

    private val _showUnreadOnly = MutableLiveData<Boolean>()
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    private val _unreadMessages = MutableLiveData<Int>()
    val unreadMessage: LiveData<Int> = _unreadMessages

    private val enabledAccounts = HashSet<String>()

    var a = 3000

    val t = System.currentTimeMillis() + 99999999999999999

    init {
        _showUnreadOnly.value = false
        getChatList()
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
        initDataListener()
        getChatList()
    }

    fun initDataListener() {
        job?.cancel()
        val account = getEnableAccountList()
        val query =
            if (showUnreadOnly.value!!) "owner = '$account' && isArchived = false && unread > 0" else "owner = '$account' && isArchived = false"
        job = viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(LastChatsStorageItem::class, query)
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val dataSource = ArrayList<ChatListDto>()
                        dataSource.addAll(changes.list.map { T ->
                            ChatListDto(
                                id = T.primary,
                                owner = T.owner,
                                opponentJid = T.opponentJid,
                                opponentNickname = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
                                customNickname = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
                                lastMessageBody = if (T.lastMessage == null) "" else T.lastMessage!!.body,
                                lastMessageDate = if (T.lastMessage == null) T.messageDate else T.lastMessage!!.date,
                                lastMessageState = if (T.lastMessage?.state_ == 5 || T.lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
                                isArchived = T.isArchived,
                                isSynced = T.isSynced,
                                draftMessage = T.draftMessage,
                                hasAttachment = false,
                                isSystemMessage = false,
                                isMentioned = false,
                                muteExpired = T.muteExpired,
                                pinnedDate = T.pinnedPosition,
                                status = ResourceStatus.Online,
                                entity = RosterItemEntity.Contact,
                                unread = if (T.unread <= 0) "" else T.unread.toString(),
                                lastPosition = T.lastPosition,
                                drawableId = T.avatar,
                                colorId = T.color,
                                isHide = false,
                                lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
                            )
                        })

                        chatListDto = dataSource
                        chatListDto.sort()
                        if (chatListDto.size > 0) {
                            chatListDto.add(
                                0,
                                ChatListDto(
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    0,
                                    MessageSendingState.None,
                                    false,
                                    true,
                                    "",
                                    true,
                                    true,
                                    true,
                                    -1,
                                    t,
                                    ResourceStatus.Chat,
                                    RosterItemEntity.Bot,
                                    "",
                                    "",
                                    R.color.grey_500,
                                    R.drawable.flower, true
                                )
                            )
                        }
                        launch(Dispatchers.Main) {
                            _chats.postValue(chatListDto)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun getEnableAccountList(): String? {
        var a: String? = null
        realm.writeBlocking {
            val accounts = this.query(AccountStorageItem::class, "enabled = true").first().find()
            a = accounts?.jid ?: null
        }
        return a
    }

    fun initAccountDataListener() {
        Log.d("itt","${realm.query(AccountStorageItem::class).find()}")
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {

                    is UpdatedResults -> {
                        getChatList()
                    }
                    else -> {}
                }
            }
        }
    }

    fun getChatList() {
        val account = getEnableAccountList()
        val query =
            if (showUnreadOnly.value!!) "owner = '$account' && isArchived = false && unread > 0" else "owner = '$account' && isArchived = false"
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    LastChatsStorageItem::class,
                    query
                ).find()
            val dataSource = ArrayList<ChatListDto>()
            dataSource.addAll(realmList.map { T ->
                ChatListDto(
                    id = T.primary,
                    owner = T.owner,
                    opponentJid = T.opponentJid,
                    opponentNickname = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
                    customNickname = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
                    lastMessageBody = if (T.lastMessage == null) "" else T.lastMessage!!.body,
                    lastMessageDate = if (T.lastMessage == null) T.messageDate else T.lastMessage!!.date,
                    lastMessageState = if (T.lastMessage?.state_ == 5 || T.lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
                    isArchived = T.isArchived,
                    isSynced = T.isSynced,
                    draftMessage = T.draftMessage,
                    hasAttachment = false,
                    isSystemMessage = false,
                    isMentioned = false,
                    muteExpired = T.muteExpired,
                    pinnedDate = T.pinnedPosition,
                    status = ResourceStatus.Online,
                    entity = RosterItemEntity.Contact,
                    unread = if (T.unread <= 0) "" else T.unread.toString(),
                    lastPosition = T.lastPosition,
                    drawableId = T.avatar,
                    colorId = T.color,
                    isHide = false,
                    lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
                )
            })
            chatListDto = dataSource
            if (chatListDto.size > 0) {
                chatListDto.add(
                    0,
                    ChatListDto(
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        0,
                        MessageSendingState.None,
                        false,
                        true,
                        "",
                        true,
                        true,
                        true,
                        -1,
                        t,
                        ResourceStatus.Chat,
                        RosterItemEntity.Bot,
                        "",
                        "",
                        R.color.grey_500,
                        R.drawable.flower, true
                    )
                )
                chatListDto.sort()
            }
            withContext(Dispatchers.Main) {
                _chats.value = chatListDto
            }
        }
    }

    fun pinChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.pinnedPosition = System.currentTimeMillis()
            }
        }
    }

    fun unPinChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.pinnedPosition = -1
            }
        }
    }

    fun movieChatToArchive(id: String, isArchived: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.isArchived = isArchived
            }
        }
    }

    fun insertChat(chatListDto: ChatListDto) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val rosterStorageItem = copyToRealm(RosterStorageItem().apply {
                    primary = chatListDto.id
                    owner = chatListDto.owner
                    jid = chatListDto.opponentJid
                    nickname = chatListDto.opponentNickname
                    customNickname = chatListDto.customNickname

                })
                this.copyToRealm(LastChatsStorageItem().apply {
                    primary = chatListDto.id
                    owner = chatListDto.owner
                    opponentJid = chatListDto.opponentJid
                    rosterItem = rosterStorageItem
                    muteExpired = chatListDto.muteExpired
                    messageDate = chatListDto.lastMessageDate
                    isArchived = chatListDto.isArchived
                    isSynced = chatListDto.isSynced
                    unread = chatListDto.unread.toInt()
                    avatar = chatListDto.drawableId
                    color = chatListDto.colorId
                    draftMessage = chatListDto.draftMessage
                    pinnedPosition = chatListDto.pinnedDate
                    lastPosition = chatListDto.lastPosition
                })
            }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val deletedChat =
                    realm.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (deletedChat != null) findLatest(deletedChat)?.let { delete(it) }
            }
        }
    }

    fun setMute(id: String, muteExpired: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.muteExpired = muteExpired
            }
        }
    }

    fun clearHistoryChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val chat = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                val opponent = chat?.opponentJid
                val messages =
                    this.query(MessageStorageItem::class, "opponent = '$opponent'").find()
                delete(messages)
                chat?.lastMessage = null
                chat?.unread = 0
            }
        }
    }

    fun markAllChatsAsUnread() {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val items = this.query(LastChatsStorageItem::class).find()
                items.forEach {
                    it.unread = 0
                }
                val messages = this.query(MessageStorageItem::class).find()
                messages.forEach {
                    it.isRead = true
                }
            }
        }
    }

    fun checkIsEntry(): Boolean {
        var isEntry = false
        realm.writeBlocking {
            val account = this.query(AccountStorageItem::class).first().find()
            isEntry = account != null
        }
        return isEntry
    }

    fun initUnreadMessagesCountListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(
                    LastChatsStorageItem::class,
                    "isArchived = false && muteExpired <= 0 && unread > 0"
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

    fun initAccountListListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(AccountStorageItem::class, "enabled = true")
            request.asFlow().collect { changes: ResultsChange<AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list.forEach { enabledAccounts.add(it.jid) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun forwardMessage(id: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) {
                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = "texttt + ${System.currentTimeMillis()}"
                        owner = item.owner
                        opponent = item.opponentJid
                        body = text
                        date = System.currentTimeMillis()
                        sentDate = System.currentTimeMillis()
                        editDate = 0
                        outgoing = true
                        references = realmListOf()
                        conversationType_ = ConversationType.Channel.toString()
                    })
                    item.lastMessage = message
                    item.messageDate = message.date
                    item.unread = 0

                }
            }
        }
    }

    fun getUnreadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(
                    LastChatsStorageItem::class,
                    "isArchived = false && muteExpired <= 0 && unread > 0"
                ).find()
            var count = 0
            request.forEach {
                if (it.unread > 0) count += it.unread
            }
            withContext(Dispatchers.Main) {
                _unreadMessages.value = count
            }
        }
    }

    fun chatIsEmpty(): Boolean {
        var result = true
        realm.writeBlocking {
            val lastChats = this.query(LastChatsStorageItem::class).find()
            if (lastChats.size > 0) result = false
        }
        return result
    }

    fun insertContactAndChat(contactJid: String, customName: String) {
        val contactOwner = getPrimaryAccount()
        if (contactOwner != null) {
            viewModelScope.launch(Dispatchers.IO) {
                realm.write {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = contactJid
                        muteExpired = -1
                        owner = contactOwner
                        opponentJid = contactJid
                        rosterItem = copyToRealm(RosterStorageItem().apply {
                            primary = contactJid
                            owner = contactOwner
                            jid = contactJid
                            customNickname = customName
                        })
                    })
                }
            }
        }
    }

    fun getPrimaryAccount(): String? {
        var account: String? = null
        realm.writeBlocking {
            val item = this.query(AccountStorageItem::class, "order = 0").first().find()
            account = item?.jid
        }
        return account
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        realm.close()
    }

    fun addSomeChats() {
        val colors = listOf<Int>(
            R.color.blue_500,
            R.color.yellow_500,
            R.color.orange_500,
            R.color.red_500,
            R.color.green_500,
            R.color.amber_500
        )
        val avatars = listOf(
            R.drawable.flower,
            R.drawable.angel,
            R.drawable.dog,
            R.drawable.car,
            R.drawable.sea,
            R.drawable.man
        )
        var b = 0

        val names = listOf(
            "Иван",
            "Сергей",
            "Анатолий",
            "Петр",
            "Геннадий",
            "Роман",
            "Кирилл",
            "Павел",
            "Руслан",
            "Олег",
            "Алексей",
            "Андрей",
            "Эдуард",
            "Валерий",
            "Борис",
            "Михаил",
            "Марат",
            "Игнат",
            "Лев",
            "Афанасий"
        )
        val familys = listOf(
            "Усачев",
            "Кошкин",
            "Степанов",
            "Ветров",
            "Тимофеев",
            "Голубев",
            "Белов",
            "Ульянов",
            "Солнцев",
            "Романов",
            "Корытов",
            "Букин",
            "Сталин",
            "Горин",
            "Павлов",
            "Рубин",
            "Комов",
            "Тигров",
            "Рыбин",
            "Поддубный"
        )

        val nam = ArrayList<String>()
        for (i in 0 until names.size) {
            for (j in 0 until familys.size) {
                val m = names[i] + " " + familys[j]
                nam.add(m)
            }
        }
        val chatsOwner = realm.query(AccountStorageItem::class).find().first()
        viewModelScope.launch(Dispatchers.IO) {

            realm.write {
                for (i in 0 until 400) {
                    val col = colors.random()
                    val av = avatars.random()
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = "$b 10"
                        muteExpired = -1
                        owner = chatsOwner.jid
                        opponentJid = "${nam[i]}@redsolution.ru"
                        rosterItem = copyToRealm(RosterStorageItem().apply {
                            primary = "$b 10"
                            owner = chatsOwner.jid
                            jid = "${nam[i]}@redsolution.ru"
                            nickname = nam[i]
                            customNickname = nam[i]
                            colorR = col
                            avatarR = av
                        })
                        messageDate = System.currentTimeMillis()
                        isArchived = false
                        unread = 0
                        avatar = av
                        color = col
                    })
                    b++
                }
            }
        }
    }


    fun getArchivedChats() {
        val account = getEnableAccountList()
        val query = "owner = '$account' && isArchived = true"
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    LastChatsStorageItem::class,
                    query
                ).find()
            val dataSource = ArrayList<ChatListDto>()
            dataSource.addAll(realmList.map { T ->
                ChatListDto(
                    id = T.primary,
                    owner = T.owner,
                    opponentJid = T.opponentJid,
                    opponentNickname = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
                    customNickname = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
                    lastMessageBody = if (T.lastMessage == null) "" else T.lastMessage!!.body,
                    lastMessageDate = if (T.lastMessage == null) T.messageDate else T.lastMessage!!.date,
                    lastMessageState = if (T.lastMessage?.state_ == 5 || T.lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
                    isArchived = T.isArchived,
                    isSynced = T.isSynced,
                    draftMessage = T.draftMessage,
                    hasAttachment = false,
                    isSystemMessage = false,
                    isMentioned = false,
                    muteExpired = T.muteExpired,
                    pinnedDate = T.pinnedPosition,
                    status = ResourceStatus.Online,
                    entity = RosterItemEntity.Contact,
                    unread = if (T.unread <= 0) "" else T.unread.toString(),
                    lastPosition = T.lastPosition,
                    drawableId = T.avatar,
                    colorId = T.color,
                    isHide = false,
                    lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
                )
            })
               dataSource.sort()
            withContext(Dispatchers.Main) {
                _archivedChats.value = dataSource
            }
        }
    }

    fun initArchivedChatsListener() {
        val account = getEnableAccountList()
        val query = "owner = '$account' && isArchived = true"
        job = viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(LastChatsStorageItem::class, query)
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val dataSource = ArrayList<ChatListDto>()
                        dataSource.addAll(changes.list.map { T ->
                            ChatListDto(
                                id = T.primary,
                                owner = T.owner,
                                opponentJid = T.opponentJid,
                                opponentNickname = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
                                customNickname = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
                                lastMessageBody = if (T.lastMessage == null) "" else T.lastMessage!!.body,
                                lastMessageDate = if (T.lastMessage == null) T.messageDate else T.lastMessage!!.date,
                                lastMessageState = if (T.lastMessage?.state_ == 5 || T.lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
                                isArchived = T.isArchived,
                                isSynced = T.isSynced,
                                draftMessage = T.draftMessage,
                                hasAttachment = false,
                                isSystemMessage = false,
                                isMentioned = false,
                                muteExpired = T.muteExpired,
                                pinnedDate = T.pinnedPosition,
                                status = ResourceStatus.Online,
                                entity = RosterItemEntity.Contact,
                                unread = if (T.unread <= 0) "" else T.unread.toString(),
                                lastPosition = T.lastPosition,
                                drawableId = T.avatar,
                                colorId = T.color,
                                isHide = false,
                                lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
                            )
                        })
                        dataSource.sort()
                        launch(Dispatchers.Main) {
                            _archivedChats.postValue(dataSource)
                        }
                    }
                    else -> {}
                }
            }
        }
    }



}
