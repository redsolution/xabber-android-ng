package com.xabber.presentation.application.fragments.chatlist

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
import io.realm.Realm
import io.realm.RealmResults
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
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

    private val _unreadMessages = MutableLiveData<Int>()
    val unreadMessage: LiveData<Int> = _unreadMessages

    private val _showUnreadOnly = MutableLiveData<Boolean>()
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    init {
        _showUnreadOnly.value = false
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
    }

    fun initDataListener() {
        val request =
            realm.query(LastChatsStorageItem::class, "isArchived = false")
        job = viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
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
                                    0,
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
                        0,
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

    fun clearHistoryChat(opponent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val items: RealmResults<MessageStorageItem> =
                    this.query(MessageStorageItem::class, "opponent = '$opponent'").find()
                delete(items)
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
        realm.close()
    }

}
