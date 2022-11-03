package com.xabber.presentation.application.fragments.chatlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.defaultRealmConfig
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.messages.MessageStorageItem
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.presentation.application.activity.ApplicationViewModel
import io.realm.Realm
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
import kotlinx.coroutines.*

class ChatListViewModel : ApplicationViewModel() {
    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList
    val realm = Realm.open(defaultRealmConfig())
    val chatListDtos = ArrayList<ChatListDto>()
    var a = 111
    var job: Job? = null


    init {
        _chatList.value = chatListDtos
    }

    fun getChatList(chatListType: ChatListType) {
        job?.cancel()

        val query =
            when (chatListType) {
                ChatListType.RECENT -> "isArchived = false"
                ChatListType.UNREAD -> "'unread.length' > 0"
                ChatListType.ARCHIVE -> "isArchived = true"
            }

        val request = realm.query(LastChatsStorageItem::class, query)
        job = CoroutineScope(Dispatchers.IO).launch {
            val lastChatsFlow = request.asFlow()

            lastChatsFlow.collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.insertions
                        changes.insertionRanges
                        changes.changes
                        changes.changeRanges
                        changes.deletions
                        changes.deletionRanges
                        changes.list

                        val realmList = realm.query(LastChatsStorageItem::class).find()
                        chatListDtos.clear()
                        chatListDtos.addAll(realmList.map { T ->
                            ChatListDto(
                                T.primary,
                                T.owner,
                                T.jid,
                                "",
                                T.lastMessage?.body,
                                T.messageDate,
                                MessageSendingState.Read,
                                T.isArchived,
                                T.isSynced,
                                T.draftMessage,
                                false, // hasAttachment
                                false, // isSystemMessage
                                false, //isMentioned
                                T.muteExpired,
                                T.pinnedPosition, // почему дабл?
                                ResourceStatus.Online,
                                RosterItemEntity.Contact,
                                T.unread,
                                0, T.color, T.avatar, null
                            )
                        })

                        withContext(Dispatchers.Main) { _chatList.value = chatListDtos }
                    }
                    else -> {}
                }
            }
        }

        val r = realm.query(LastChatsStorageItem::class, query).find()
          chatListDtos.clear()
                        chatListDtos.addAll(r.map { T ->
                            ChatListDto(
                                T.primary,
                                T.owner,
                                T.jid,
                                "",
                                T.lastMessage?.body,
                                T.messageDate,
                                MessageSendingState.Read,
                                T.isArchived,
                                T.isSynced,
                                T.draftMessage,
                                false, // hasAttachment
                                false, // isSystemMessage
                                false, //isMentioned
                                T.muteExpired,
                                T.pinnedPosition, // почему дабл?
                                ResourceStatus.Online,
                                RosterItemEntity.Contact,
                                T.unread,
                                0, T.color, T.avatar, null
                            )
                        })

                        _chatList.value = chatListDtos
    }

    fun movieChatToArchive(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.isArchived = true
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

    fun pinChat(id: String): Boolean {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.isPinned = true
                item?.pinnedPosition = System.currentTimeMillis()
            }
        }
        return true
    }

    fun unPinChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.isPinned = false
                item?.pinnedPosition = 0
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


    fun addChat() {
        val names = listOf<String>(
            "Татьяна Игнатьева",
            "Вероника Козлова",
            "Светлана Зорина",
            "Олег Васильев",
            "Кирилл Серов",
            "Тимофей Шолохов",
            "Петр Славин"
        )
        val bodyes = listOf<String>(
            "Привет!",
            "Как дела",
            "Отлино, встетимся завтра",
            "Скоро приеду",
            "Передай всем привет от меня",
            "Добрый день"
        )
        val colors = listOf<Int>(
            R.color.blue_500,
            R.color.yellow_500,
            R.color.orange_500,
            R.color.red_500,
            R.color.green_500,
            R.color.amber_500
        )
        val avatars = listOf<Int>(
            R.drawable.angel,
            R.drawable.baby,
            R.drawable.rayan,
            R.drawable.free,
            R.drawable.girl,
            R.drawable.car,
            R.drawable.flower
        )

        a++
        realm.writeBlocking {
            copyToRealm(LastChatsStorageItem().apply {
                primary = "u ${a}"
                muteExpired = -1
                owner = names.random()
                jid = "ignateva@redsolution.ru"
                messageDate = System.currentTimeMillis()
                isArchived = false
                avatar = avatars.random()
                color = colors.random()
                lastMessage = copyToRealm(MessageStorageItem().apply {
                    primary = "tt $a"
                    body = bodyes.random()
                    state_ = 0
                })
            })


        }
    }

    fun addInitial() {
        val names = listOf<String>(
            "Татьяна Игнатьева",
            "Вероника Козлова",
            "Светлана Зорина",
            "Олег Васильев",
            "Кирилл Серов",
            "Тимофей Шолохов",
            "Петр Славин"
        )
        val bodyes = listOf<String>(
            "Привет!",
            "Как дела",
            "Отлино, встетимся завтра",
            "Скоро приеду",
            "Передай всем привет от меня",
            "Добрый день"
        )
        val colors = listOf<Int>(
            R.color.blue_500,
            R.color.yellow_500,
            R.color.orange_500,
            R.color.red_500,
            R.color.green_500,
            R.color.amber_500
        )
        val avatars = listOf<Int>(
            R.drawable.angel,
            R.drawable.baby,
            R.drawable.rayan,
            R.drawable.free,
            R.drawable.girl,
            R.drawable.car,
            R.drawable.flower
        )


        viewModelScope.launch {

            realm.writeBlocking {
                for (i in 0..1000) {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = "b $i"
                        muteExpired = -1
                        owner = names.random()
                        jid = "ignateva@redsolution.ru"
                        messageDate = System.currentTimeMillis()
                        isArchived = false
                        avatar = avatars.random()
                        color = colors.random()
                        lastMessage = copyToRealm(MessageStorageItem().apply {
                            primary = "z $i"
                            body = bodyes.random()
                            state_ = 0
                        })
                    })

                    //  }
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

