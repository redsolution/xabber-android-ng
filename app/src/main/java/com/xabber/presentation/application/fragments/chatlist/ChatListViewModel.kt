package com.xabber.presentation.application.fragments.chatlist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.model.dto.AccountDto
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.account.AccountStorageItem
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.messages.MessageStorageItem
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.model.xmpp.roster.RosterStorageItem
import com.xabber.model.xmpp.sync.ConversationType
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
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

    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList
    private var listDto = ArrayList<ChatListDto>()
    private val _showUnreadOnly = MutableLiveData<Boolean>()
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    private val _unreadMessages = MutableLiveData<Int>()
    val unreadMessage: LiveData<Int> = _unreadMessages

    private val enabledAccounts = HashSet<String>()

    var a = 3000

    val t = System.currentTimeMillis() + 99999999999999999

    init {
        _showUnreadOnly.value = false
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
    }

    private fun getEnableAccountList(): ArrayList<AccountDto> {
        val accountList = ArrayList<AccountDto>()
        realm.writeBlocking {
            val accounts = this.query(AccountStorageItem::class, "enabled = true").find()
            accountList.addAll(accounts.map { T ->
                AccountDto(
                    id = T.primary,
                    jid = T.jid,
                    order = T.order,
                    nickname = T.nickname,
                    enabled = T.enabled,
                    statusMessage = T.statusMessage,
                    colorKey = T.colorKey
                )
            })
        }
        accountList.sort()
        return accountList
    }

    fun initDataListener() {
        job?.cancel()
        val accounts = getEnableAccountList()
        val jids = arrayListOf<String>()
        for (i in 0 until accounts.size) jids.add(accounts[i].jid)
        Log.d("fff", "$jids")
        val p = "ooo"
        val query =
            if (showUnreadOnly.value!!) "isArchived = false && unread > 0" else "isArchived = false"
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
                                displayName = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
                                customName = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
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
                                outgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
                            )

                        })

                        listDto = dataSource
                        listDto.sort()
                        if (listDto.size > 0) {
                            listDto.add(
                                0,
                                ChatListDto(
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    0,
                                    MessageSendingState.Sent,
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
                            _chatList.postValue(listDto)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getChatList() {
        val accounts = getEnableAccountList()
        val jids = arrayListOf<String>()
        for (i in 0 until accounts.size) jids.add(accounts[i].jid)
        Log.d("fff", "get $jids")
        val query =
            if (showUnreadOnly.value!!) "isArchived = false && unread > 0" else "isArchived = false"
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    LastChatsStorageItem::class,
                    query).find()
            val dataSource = ArrayList<ChatListDto>()
            dataSource.addAll(realmList.map { T ->
                ChatListDto(
                    id = T.primary,
                    owner = T.owner,
                    opponentJid = T.opponentJid,
                    displayName = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
                    customName = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
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
                    outgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
                )
            })
            listDto = dataSource
            if (listDto.size > 0) {
                listDto.add(
                    0,
                    ChatListDto(
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        0,
                        MessageSendingState.Sent,
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
                listDto.sort()
            }
            withContext(Dispatchers.Main) {
                _chatList.value = listDto
            }
        }
    }

    fun pinChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.pinnedPosition = System.currentTimeMillis()
                Log.d("fff", "owner = ${item?.owner}")
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
                    nickname = chatListDto.displayName
                    customNickname = chatListDto.customName

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

    fun clearHistoryChat(id: String, opponent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val messages =
                    this.query(MessageStorageItem::class, "opponent = '$opponent'").find()
                delete(messages)
                val chat = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
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

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        realm.close()
    }


    fun addOne() {
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
        Log.d("account", "jid = ${chatsOwner.jid}, nickname = ${chatsOwner.nickname}")
        viewModelScope.launch(Dispatchers.IO) {

            realm.write {
                for (i in 0 until 400) {
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
                        })
                        messageDate = System.currentTimeMillis()
                        isArchived = false
                        unread = 0
                        avatar = avatars.random()
                        color = colors.random()
                    })
                    b++
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
        val own = getOwnerJid()
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
                        changes.list.forEach {  count += it.unread }
                        withContext(Dispatchers.Main) { _unreadMessages.value = count
                        Log.d("fff", "count = $count, $enabledAccounts, changes = $chatList")}
                    }
                    else -> {}
                }
            }
        }
    }

    private fun getOwnerJid(): String {
        val owner = realm.query(AccountStorageItem::class).first().find()
        return owner?.jid ?: ""
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
                        primary = "texttt"
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
            request.forEach { if (it.unread > 0) count += it.unread
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



}
