package com.xabber.presentation.application.fragments.chatlist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.defaultRealmConfig
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.messages.MessageStorageItem
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.model.xmpp.roster.RosterStorageItem
import com.xabber.presentation.XabberApplication
import io.realm.Realm
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
import kotlinx.coroutines.*

class ChatListViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    var job: Job? = null

    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList

    private var listDto = ArrayList<ChatListDto>()

    private val _unreadMessages = MutableLiveData<Int>()
    val unreadMessage: LiveData<Int> = _unreadMessages
    var a = 3000

    val t = System.currentTimeMillis() + 99999999999999999

    private val _showUnreadOnly = MutableLiveData<Boolean>()
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    init {
        _showUnreadOnly.value = false
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
    }

    fun initDataListener() {
        job?.cancel()
        val request =
            realm.query(LastChatsStorageItem::class, "isArchived = false")
        job = viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {

                    is UpdatedResults -> {
                        Log.d("uuu", "CHATLIST CHANGE")
                        changes.list
                        val dataSource = ArrayList<ChatListDto>()

                        if (showUnreadOnly.value!!) changes.list.filter { T -> T.unread > 0 }
                        dataSource.addAll(changes.list.map { T ->
                            ChatListDto(
                                T.primary,
                                T.owner,
                                T.jid,
                                T.rosterItem!!.nickname,
                                "",
                                T.lastMessage?.body,
                                T.messageDate,
                                MessageSendingState.Read,
                                T.isArchived,
                                T.isSynced,
                                T.draftMessage,
                                false,
                                false,
                                false,
                                T.muteExpired,
                                T.pinnedPosition,
                                ResourceStatus.Online,
                                RosterItemEntity.Contact,
                                if (T.unread <= 0) "" else T.unread.toString(),
                                0, T.color, T.avatar, null
                            )
                        })
                        listDto = dataSource
                        count = 0
                        for (i in 0 until listDto.size) {
                            if (listDto[i].unreadString.isNotEmpty()) count += listDto[i].unreadString.toInt()
                        }
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
                                    MessageSendingState.Sended,
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
                                    0,
                                    R.color.grey_500,
                                    R.drawable.flower,
                                    null,
                                    true
                                )
                            )
                        }
                        launch(Dispatchers.Main) {
                            _unreadMessages.value = count
                            _chatList.postValue(listDto)

                        }
                    }
                    else -> {}
                }
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

    fun getChat() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    LastChatsStorageItem::class,
                    "isArchived = false",
                ).find()
            val dataSource = ArrayList<ChatListDto>()
            val a =
                if (showUnreadOnly.value!!) realmList.filter { T -> T.unread > 0 } else realmList
            dataSource.addAll(a.map { T ->
                ChatListDto(
                    T.primary,
                    T.owner,
                    T.jid,
                    T.rosterItem!!.nickname,
                    "",
                    T.lastMessage?.body,
                    T.messageDate,
                    MessageSendingState.Read,
                    T.isArchived,
                    T.isSynced,
                    T.draftMessage,
                    false,
                    false,
                    false,
                    T.muteExpired,
                    T.pinnedPosition,
                    ResourceStatus.Online,
                    RosterItemEntity.Contact,
                    if (T.unread <= 0) "" else T.unread.toString(),
                    0, T.color, T.avatar, null
                )
            })
            listDto = dataSource
            var count = 0

            for (i in 0 until listDto.size) {
                if (listDto[i].unreadString.isNotEmpty()) count += listDto[i].unreadString.toInt()
            }
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
                        MessageSendingState.Sended,
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
                        0,
                        R.color.grey_500,
                        R.drawable.flower,
                        null,
                        true
                    )
                )
                listDto.sort()
            }
            withContext(Dispatchers.Main) {
                _unreadMessages.value = count
                _chatList.value = listDto
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
                })
                this.copyToRealm(LastChatsStorageItem().apply {
                    primary = chatListDto.id
                    muteExpired = chatListDto.muteExpired
                    owner = chatListDto.owner
                    jid = chatListDto.opponentJid
                    rosterItem = rosterStorageItem
                    messageDate = chatListDto.lastMessageDate
                    isArchived = chatListDto.isArchived
                    unread = chatListDto.unreadString.toInt()
                    avatar = chatListDto.drawableId
                    color = chatListDto.colorId
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
        Log.d("uuu", " в чат листах $opponent")
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val messages =
                    this.query(MessageStorageItem::class, "opponent = '$opponent'").find()
                delete(messages)

               // val chat = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
              //  chat?.lastMessage = null
            }
        }
    }

    fun markAllChatsAsUnread() {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val items = this.query(LastChatsStorageItem::class, "unread > 0").find()
                val iterator = items.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    item.unread = 0
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        //   realm.close()
    }

    fun addChat() {
        val colors = listOf<Int>(
            R.color.blue_500,
            R.color.yellow_500,
            R.color.orange_500,
            R.color.red_500,
            R.color.green_500,
            R.color.amber_500
        )
        val avatars = listOf(
            R.drawable.flower
        )

        viewModelScope.launch(Dispatchers.IO) {
            var a = 1
            realm.writeBlocking {
                a++
                val b = copyToRealm(RosterStorageItem().apply {
                    primary = "$a"
                    owner = "ivanov@xmpp.ru"
                    jid = "belov@redsolution.ru"
                    nickname = "Геннадий Белов"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "belov@redsolution.ru"
                    rosterItem = b
                    messageDate = System.currentTimeMillis()
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })


                val c = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 1"
                    owner = "ivanov@xmpp.ru"
                    jid = "volkov@redsolution.ru"
                    nickname = "Станислав Волков"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 1"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "volkov@redsolution.ru"
                    rosterItem = c
                    messageDate = System.currentTimeMillis() - 56565
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })


                val g = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 2"
                    owner = "ivanov@xmpp.ru"
                    jid = "tokarev@redsolution.ru"
                    nickname = "Олег Токарев"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 2"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "tokarev@redsolution.ru"
                    rosterItem = g
                    messageDate = System.currentTimeMillis() - 999
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })

                val f = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 3"
                    owner = "ivanov@xmpp.ru"
                    jid = "verina@redsolution.ru"
                    nickname = "Татьяна Верина"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 3"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "verina@redsolution.ru"
                    rosterItem = f
                    messageDate = System.currentTimeMillis()
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })

                val s = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 4"
                    owner = "ivanov@xmpp.ru"
                    jid = "petrova@redsolution.ru"
                    nickname = "Ольга Петрова"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 4"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "petrova@redsolution.ru"
                    rosterItem = s
                    messageDate = System.currentTimeMillis() - 666666
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })


                val k = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 5"
                    owner = "ivanov@xmpp.ru"
                    jid = "yakovlev@redsolution.ru"
                    nickname = "Виктор Яковлев"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 5"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "yakovlev@redsolution.ru"
                    rosterItem = k
                    messageDate = System.currentTimeMillis()
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })

                val m = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 6"
                    owner = "ivanov@xmpp.ru"
                    jid = "sidorov@redsolution.ru"
                    nickname = "Петр Сидоров"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 6"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "sidorov@redsolution.ru"
                    rosterItem = m
                    messageDate = System.currentTimeMillis()
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })


                val n = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 7"
                    owner = "ivanov@xmpp.ru"
                    jid = "pelevina@redsolution.ru"
                    nickname = "Ирина Пелевина"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 7"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "pelevina@redsolution.ru"
                    rosterItem = n
                    messageDate = System.currentTimeMillis()
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })


                val w = copyToRealm(RosterStorageItem().apply {
                    primary = "$a 8"
                    owner = "ivanov@xmpp.ru"
                    jid = "sobakin@redsolution.ru"
                    nickname = "Тимофей Собакин"
                })
                copyToRealm(LastChatsStorageItem().apply {
                    primary = "$a 8"
                    muteExpired = -1
                    owner = "ivanov@xmpp.ru"
                    jid = "sobakin@redsolution.ru"
                    rosterItem = w
                    messageDate = System.currentTimeMillis()
                    isArchived = false
                    unread = 0
                    avatar = avatars.random()
                    color = colors.random()
                })
            }
        }
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
            R.drawable.flower
        )
        var b = 333
        viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until 300) {
                b++
                delay(1000)
                realm.writeBlocking {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = "$b 10"
                        muteExpired = -1
                        owner = "ivanov@xmpp.ru"
                        jid = "sobakin@redsolution.ru"
                        rosterItem = copyToRealm(RosterStorageItem().apply {
                            primary = "$b 10"
                            owner = "ivanov@xmpp.ru"
                            jid = "sobakin@redsolution.ru"
                            nickname = "Иван Собакин"
                        })
                        messageDate = 0
                        isArchived = false
                        unread = 0
                        avatar = avatars.random()
                        color = colors.random()
                    })
                }
            }
        }
    }
}
