package com.xabber.presentation.application.fragments.chatlist.archive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.messages.MessageStorageItem
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val listDto = ArrayList<ChatListDto>()

    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList

    fun initListener() {

        viewModelScope.launch {
            val lastChatsFlow =
                realm.query(LastChatsStorageItem::class, "isArchived == true").asFlow()
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

                        val realmList =
                            realm.query(LastChatsStorageItem::class, "isArchived == true").find()
                        listDto.clear()
                        listDto.addAll(realmList.map { T ->
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
                        withContext(Dispatchers.Main) {
                            _chatList.value = listDto
                        }
                    }
                    else -> {}
                }
            }
        }
    }


    fun getChat() {

        val realmList =
            realm.query(LastChatsStorageItem::class, "isArchived == true").find()
        listDto.clear()
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
                colorId = T.color, isHide =false, outgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
            )
        })
        _chatList.value = listDto
    }

    fun movieChatToArchive(id: String, isArchived: Boolean) {
//        val iterator = listDto.iterator()
//        while(iterator.hasNext()){
//            val item = iterator.next()
//            if(item.id == id){
//                iterator.remove()
//            }
//        }
//        _chatList.value = listDto
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.isArchived = isArchived
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

}
