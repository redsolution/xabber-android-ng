package com.xabber.presentation.application.fragments.chatlist.archive

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.dao.LastChatStorageItemDao
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.ChatListDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.messages.MessageStorageItem
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val lastChatDao = LastChatStorageItemDao(realm)
    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList
    val t = System.currentTimeMillis() + 99999999999999999

    init {
     getChat()
    }

    fun initListener() {
        viewModelScope.launch {
            val account = getEnableAccountList()
            val lastChatsFlow =
                realm.query(LastChatsStorageItem::class, "owner = '$account' && isArchived == true")
                    .asFlow()
            lastChatsFlow.collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val listDto = ArrayList<ChatListDto>()
                        listDto.addAll(changes.list.map { T ->
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
                                isHide = false,
                                lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
                            )
                        })
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
                                    R.drawable.flower, true
                                )
                            )
                        }
                        withContext(Dispatchers.Main) {
                            _chatList.value = listDto
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
            a = accounts?.jid
        }
        return a
    }


    fun pinChat(id: String) {
        lastChatDao.setPinnedPosition(id, System.currentTimeMillis())
    }

    fun unPinChat(id: String) {
        lastChatDao.setPinnedPosition(id, -1)
    }

    fun getChat() {
        val account = getEnableAccountList()
        viewModelScope.launch(Dispatchers.IO) {
        val realmList =
            realm.query(LastChatsStorageItem::class, "owner = '$account' && isArchived = true")
                .find()
      val listDto = ArrayList<ChatListDto>()
        listDto.addAll(realmList.map { T ->
            ChatListDto(
                T.primary,
                T.owner,
                T.opponentJid,
                T.rosterItem!!.nickname,
                "",
                if (T.lastMessage == null) "" else T.lastMessage!!.body,
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
                if (T.unread <= 0) "" else T.unread.toString(),
                lastPosition = T.lastPosition,
                drawableId = T.avatar,
                isHide = false,
                lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
            )
        })
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
                        R.drawable.flower, true
                    )
                )
            }
            withContext(Dispatchers.Main) {  _chatList.value = listDto    }
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

    fun deleteChat(id: String) {
        Log.d("iii", "viewMoel")
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val deletedChat =
                    realm.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                Log.d("iii", "realm $deletedChat")
                if (deletedChat != null) findLatest(deletedChat)?.let { delete(it) }
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

    fun setMute(id: String, muteExpired: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.muteExpired = muteExpired
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

    fun getColor(): String? {
        var color: String? = null
        realm.writeBlocking {
            val account = this.query(AccountStorageItem::class).first().find()
            color = account?.colorKey
        }
        return color
    }

}
