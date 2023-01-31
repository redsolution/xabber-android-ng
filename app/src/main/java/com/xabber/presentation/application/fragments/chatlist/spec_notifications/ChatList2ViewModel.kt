//package com.xabber.presentation.application.fragments.chatlist.spec_notifications
//
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.xabber.R
//import com.xabber.data_base.defaultRealmConfig
//import com.xabber.models.dto.ChatListDto
//import com.xabber.models.xmpp.account.AccountStorageItem
//import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
//import com.xabber.models.xmpp.messages.MessageSendingState
//import com.xabber.models.xmpp.messages.MessageStorageItem
//import com.xabber.models.xmpp.presences.ResourceStatus
//import com.xabber.models.xmpp.presences.RosterItemEntity
//import com.xabber.models.xmpp.roster.RosterStorageItem
//import com.xabber.presentation.application.fragments.chatlist.ChatListBaseFragment.ChatListType
//import io.realm.kotlin.Realm
//import io.realm.kotlin.notifications.ResultsChange
//import io.realm.kotlin.notifications.UpdatedResults
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//class ChatList2ViewModel : ViewModel() {
//    val realm = Realm.open(defaultRealmConfig())
//
//    private val _chatList = MutableLiveData<ArrayList<ChatListDto>>()
//    val chatList: LiveData<ArrayList<ChatListDto>> = _chatList
//
//    fun initializeChatsListener(chatListType: ChatListType) {
//        val query = when (chatListType) {
//            ChatListType.RECENT -> "isArchived = false"
//            ChatListType.UNREAD -> "isArchived = false && unread > 0"
//            ChatListType.ARCHIVED -> "isArchived = true"
//        }
//
//        viewModelScope.launch {
//            val account = getEnableAccountList()
//            val lastChatsFlow =
//                realm.query(LastChatsStorageItem::class, "owner = '$account' && $query")
//                    .asFlow()
//            lastChatsFlow.collect { changes: ResultsChange<LastChatsStorageItem> ->
//                when (changes) {
//                    is UpdatedResults -> {
//                        val listDto = ArrayList<ChatListDto>()
//                        listDto.addAll(changes.list.map { T ->
//                            ChatListDto(
//                                id = T.primary,
//                                owner = T.owner,
//                                opponentJid = T.opponentJid,
//                                opponentNickname = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
//                                customNickname = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
//                                lastMessageBody = if (T.lastMessage == null) "" else T.lastMessage!!.body,
//                                lastMessageDate = if (T.lastMessage == null) T.messageDate else T.lastMessage!!.date,
//                                lastMessageState = if (T.lastMessage?.state_ == 5 || T.lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
//                                isArchived = T.isArchived,
//                                isSynced = T.isSynced,
//                                draftMessage = T.draftMessage,
//                                hasAttachment = false,
//                                isSystemMessage = false,
//                                isMentioned = false,
//                                muteExpired = T.muteExpired,
//                                pinnedDate = T.pinnedPosition,
//                                status = ResourceStatus.Online,
//                                entity = RosterItemEntity.Contact,
//                                unread = if (T.unread <= 0) "" else T.unread.toString(),
//                                lastPosition = T.lastPosition,
//                                drawableId = T.avatar,
//                                colorId = T.color,
//                                isHide = false,
//                                lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
//                            )
//                        })
//                        if (listDto.size > 0 && chatListType != ChatListType.ARCHIVED) {
//                            listDto.add(
//                                0,
//                                ChatListDto(
//                                    "",
//                                    "",
//                                    "",
//                                    "",
//                                    "",
//                                    "",
//                                    0,
//                                    MessageSendingState.None,
//                                    false,
//                                    true,
//                                    "",
//                                    true,
//                                    true,
//                                    true,
//                                    -1,
//                                    System.currentTimeMillis() + 99999999999999999,
//                                    ResourceStatus.Chat,
//                                    RosterItemEntity.Bot,
//                                    "",
//                                    "",
//                                    R.color.grey_500,
//                                    R.drawable.flower, true
//                                )
//                            )
//                            listDto.sort()
//                        }
//                        withContext(Dispatchers.Main) {
//                            _chatList.value = listDto
//                        }
//                    }
//                    else -> {}
//                }
//            }
//        }
//    }
//
//    fun getChat(chatListType: ChatListType) {
//        val query = when (chatListType) {
//            ChatListType.RECENT -> "isArchived = false"
//            ChatListType.UNREAD -> "isArchived = false && unread > 0"
//            ChatListType.ARCHIVED -> "isArchived = true"
//        }
//        val account = getEnableAccountList()
//        viewModelScope.launch(Dispatchers.IO) {
//            val realmList =
//                realm.query(LastChatsStorageItem::class, "owner = '$account' && $query")
//                    .find()
//            val listDto = ArrayList<ChatListDto>()
//            listDto.addAll(realmList.map { T ->
//                ChatListDto(
//                    T.primary,
//                    T.owner,
//                    T.opponentJid,
//                    T.rosterItem!!.nickname,
//                    "",
//                    if (T.lastMessage == null) "" else T.lastMessage!!.body,
//                    T.messageDate,
//                    MessageSendingState.Read,
//                    T.isArchived,
//                    T.isSynced,
//                    T.draftMessage,
//                    false,
//                    false,
//                    false,
//                    T.muteExpired,
//                    T.pinnedPosition,
//                    ResourceStatus.Online,
//                    RosterItemEntity.Contact,
//                    if (T.unread <= 0) "" else T.unread.toString(),
//                    lastPosition = T.lastPosition,
//                    drawableId = T.avatar,
//                    colorId = T.color,
//                    isHide = false,
//                    lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
//                )
//            })
//            if (listDto.size > 0 && chatListType != ChatListType.ARCHIVED) {
//                listDto.add(
//                    0,
//                    ChatListDto(
//                        "",
//                        "",
//                        "",
//                        "",
//                        "",
//                        "",
//                        0,
//                        MessageSendingState.None,
//                        false,
//                        true,
//                        "",
//                        true,
//                        true,
//                        true,
//                        -1,
//                        System.currentTimeMillis() + 99999999999999999,
//                        ResourceStatus.Chat,
//                        RosterItemEntity.Bot,
//                        "",
//                        "",
//                        R.color.grey_500,
//                        R.drawable.flower, true
//                    )
//                )
//                listDto.sort()
//            }
//            withContext(Dispatchers.Main) {
//                _chatList.value = listDto
//            }
//        }
//    }
//
//    fun movieChatToArchive(id: String, isArchived: Boolean) {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val item: LastChatsStorageItem? =
//                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
//                item?.isArchived = isArchived
//            }
//        }
//    }
//
//    fun deleteChat(id: String) {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val deletedChat =
//                    realm.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
//                if (deletedChat != null) findLatest(deletedChat)?.let { delete(it) }
//            }
//        }
//    }
//
//    fun clearHistoryChat(id: String) {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val chat = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
//                val opponent = chat?.opponentJid
//                val messages =
//                    this.query(MessageStorageItem::class, "opponent = '$opponent'").find()
//                delete(messages)
//                chat?.lastMessage = null
//                chat?.unread = 0
//            }
//        }
//    }
//
//    fun setMute(id: String, muteExpired: Long) {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val item: LastChatsStorageItem? =
//                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
//                item?.muteExpired = muteExpired
//            }
//        }
//    }
//
//
//    fun getEnableAccountList(): String? {
//        var a: String? = null
//        realm.writeBlocking {
//            val accounts = this.query(AccountStorageItem::class, "enabled = true").first().find()
//            a = accounts?.jid
//        }
//        return a
//    }
//
//    fun pinChat(id: String) {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val item: LastChatsStorageItem? =
//                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
//                item?.pinnedPosition = System.currentTimeMillis()
//            }
//        }
//    }
//
//    fun unPinChat(id: String) {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val item: LastChatsStorageItem? =
//                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
//                item?.pinnedPosition = -1
//            }
//        }
//    }
//
//    fun getPrimaryAccount(): String? {
//        var account: String? = null
//        realm.writeBlocking {
//            val item = this.query(AccountStorageItem::class, "order = 0").first().find()
//            account = item?.jid
//        }
//        return account
//    }
//
//
//    fun addSomeChats() {
//        val colors = listOf<Int>(
//            R.color.blue_500,
//            R.color.yellow_500,
//            R.color.orange_500,
//            R.color.red_500,
//            R.color.green_500,
//            R.color.amber_500
//        )
//        val avatars = listOf(
//            R.drawable.flower,
//            R.drawable.angel,
//            R.drawable.dog,
//            R.drawable.car,
//            R.drawable.sea,
//            R.drawable.man
//        )
//        var b = 0
//
//        val names = listOf(
//            "Иван",
//            "Сергей",
//            "Анатолий",
//            "Петр",
//            "Геннадий",
//            "Роман",
//            "Кирилл",
//            "Павел",
//            "Руслан",
//            "Олег",
//            "Алексей",
//            "Андрей",
//            "Эдуард",
//            "Валерий",
//            "Борис",
//            "Михаил",
//            "Марат",
//            "Игнат",
//            "Лев",
//            "Афанасий"
//        )
//        val familys = listOf(
//            "Усачев",
//            "Кошкин",
//            "Степанов",
//            "Ветров",
//            "Тимофеев",
//            "Голубев",
//            "Белов",
//            "Ульянов",
//            "Солнцев",
//            "Романов",
//            "Корытов",
//            "Букин",
//            "Сталин",
//            "Горин",
//            "Павлов",
//            "Рубин",
//            "Комов",
//            "Тигров",
//            "Рыбин",
//            "Поддубный"
//        )
//
//        val nam = ArrayList<String>()
//        for (i in 0 until names.size) {
//            for (j in 0 until familys.size) {
//                val m = names[i] + " " + familys[j]
//                nam.add(m)
//            }
//        }
//        val chatsOwner = realm.query(AccountStorageItem::class).find().first()
//        viewModelScope.launch(Dispatchers.IO) {
//
//            realm.write {
//                for (i in 0 until 400) {
//                    val col = colors.random()
//                    val av = avatars.random()
//                    copyToRealm(LastChatsStorageItem().apply {
//                        primary = "$b 10"
//                        muteExpired = -1
//                        owner = chatsOwner.jid
//                        opponentJid = "${nam[i]}@redsolution.ru"
//                        rosterItem = copyToRealm(RosterStorageItem().apply {
//                            primary = "$b 10"
//                            owner = chatsOwner.jid
//                            jid = "${nam[i]}@redsolution.ru"
//                            nickname = nam[i]
//                            customNickname = nam[i]
//                            colorR = col
//                            avatarR = av
//                        })
//                        messageDate = System.currentTimeMillis()
//                        isArchived = false
//                        unread = 0
//                        avatar = av
//                        color = col
//                    })
//                    b++
//                }
//            }
//        }
//    }
//
//
//    fun markAllChatsAsUnread() {
//        viewModelScope.launch(Dispatchers.IO) {
//            realm.writeBlocking {
//                val items = this.query(LastChatsStorageItem::class).find()
//                items.forEach {
//                    it.unread = 0
//                }
//                val messages = this.query(MessageStorageItem::class).find()
//                messages.forEach {
//                    it.isRead = true
//                }
//            }
//        }
//    }
//
//    override fun onCleared() {
//        super.onCleared()
//        realm.close()
//    }
//
//}
