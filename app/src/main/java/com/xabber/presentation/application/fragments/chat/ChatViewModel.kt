package com.xabber.presentation.application.fragments.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.defaultRealmConfig
import com.xabber.model.dto.MessageDto
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageDisplayType
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.messages.MessageStorageItem
import com.xabber.model.xmpp.sync.ConversationType
import com.xabber.presentation.XabberApplication
import io.realm.Realm
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
import io.realm.realmListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val _messages = MutableLiveData<ArrayList<MessageDto>>()
    val messages: LiveData<ArrayList<MessageDto>> = _messages
    private var messageList = ArrayList<MessageDto>()
    var a = 11

    fun initListener(opponent: String) {
        Log.d("chat", "инициализирован слушатель сообщений")
        val request = realm.query(MessageStorageItem::class, "opponent = '$opponent'")
        val lastChatsFlow = request.asFlow()
        viewModelScope.launch(Dispatchers.IO) {
            lastChatsFlow.collect { changes: ResultsChange<MessageStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        Log.d("chat", "MESSAGE VM CHANGE")
                        val list = ArrayList<MessageDto>()
                        list.addAll(changes.list.map { T ->
                            MessageDto(
                                T.primary,
                                T.outgoing,
                                T.owner,
                                T.opponent,
                                T.opponent,
                                T.body,
                                MessageSendingState.Sended,
                                T.sentDate,
                                T.editDate,
                                MessageDisplayType.Text,
                                true,
                                false,
                                null, // hasAttachment
                                false, // isSystemMessage
                                null, //isMentioned
                                false,
                                null // почему дабл
                            )
                        })
                        messageList = list
                        messageList.sort()
                        withContext(Dispatchers.Main) {
                            _messages.value = messageList
                            Log.d("chat", "messages внутри слушателя сообщений ${_messages.value}")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getMessageList(opponent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(MessageStorageItem::class, "opponent = '$opponent'").find()
            val list = ArrayList<MessageDto>()
            list.addAll(realmList.map { T ->
                MessageDto(
                    T.primary,
                    T.outgoing,
                    T.owner,
                    T.opponent,
                    T.opponent,
                    T.body,
                    MessageSendingState.Sended,
                    T.sentDate,
                    T.editDate,
                    MessageDisplayType.Text,
                    true,
                    false,
                    null, // hasAttachment
                    false, // isSystemMessage
                    null, //isMentioned
                    false,
                    null // почему дабл
                )
            })
            messageList = list
            messageList.sort()
            withContext(Dispatchers.Main) { _messages.value = messageList }
        }
    }

    fun insertMessage(messageDto: MessageDto) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = messageDto.primary
            realm.write {
                val message = copyToRealm(MessageStorageItem().apply {
                    primary = messageDto.primary
                    owner = messageDto.owner
                    opponent = messageDto.opponent
                    body = messageDto.messageBody
                    date = messageDto.sentTimestamp
                    sentDate = messageDto.sentTimestamp
                    editDate = messageDto.editTimestamp
                    outgoing = messageDto.isOutgoing
                    isRead = true
                    references = realmListOf()
                    conversationType_ = ConversationType.Channel.toString()
                })
                val id: String = messageDto.primary
                val opponentJid = messageDto.opponentJid
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "jid = '$opponentJid'").first().find()
                item?.lastMessage = message
                Log.d("uuu", "item lastMessage = ${item?.lastMessage?.body}")
//
//                val lastMessage = copyToRealm(MessageStorageItem().apply {
//                    primary = messageDto.primary
//                    owner = messageDto.owner
//                    opponent = messageDto.opponent
//                    body = messageDto.messageBody
//                    date = messageDto.sentTimestamp
//                    sentDate = messageDto.sentTimestamp
//                    editDate = messageDto.editTimestamp
//                    outgoing = messageDto.isOutgoing  // true я
//                    isRead = true
//                    references = realmListOf()
//                    conversationType_ = ConversationType.Channel.toString()
//                })
//                //this.query(MessageStorageItem::class, "primary = '$id'").first().find()
//                Log.d("kkkk", "lastM ${lastMessage}")
//                item?.lastMessage = lastMessage
//            }
            }
        }
    }


    fun deleteMessage(primary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val deletedMessage =
                    realm.query(MessageStorageItem::class, "primary = '$primary'").first()
                        .find()
                if (deletedMessage != null) findLatest(deletedMessage)?.let { delete(it) }
            }
        }
    }

    fun editMessage(primary: String, newBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val editableMessage: MessageStorageItem? =
                    this.query(MessageStorageItem::class, "primary = '$primary'").first().find()
                editableMessage?.body = newBody
            }
        }
    }

    fun clearHistory(owner: String, opponent: String) {
        Log.d("uuu", "$opponent")
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val messages =
                    this.query(MessageStorageItem::class, "opponent = '$opponent'").find()
                delete(messages)
            }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) findLatest(item)?.let { delete(item) }
                val d = this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
            }
        }
    }

    fun insertChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val c = copyToRealm(LastChatsStorageItem().apply {
                    primary = id
                })
                Log.d("chat", "новый $c")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm?.close()
    }


}



