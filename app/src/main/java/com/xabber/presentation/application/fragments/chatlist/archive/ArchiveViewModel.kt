package com.xabber.presentation.application.fragments.chatlist.archive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.defaultRealmConfig
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import io.realm.Realm
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val listDto = ArrayList<ChatListDto>()

    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList

   fun initListener() {
        val request =
            realm.query(LastChatsStorageItem::class, "isArchived == true")
//               job =
        viewModelScope.launch {

            val lastChatsFlow = realm.query(LastChatsStorageItem::class, "isArchived == true").asFlow()
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

                        val realmList = realm.query(LastChatsStorageItem::class, "isArchived == true").find()
                        listDto.clear()
                        listDto.addAll(realmList.map { T ->
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
                                false, // hasAttachment
                                false, // isSystemMessage
                                false, //isMentioned
                                T.muteExpired,
                                T.pinnedPosition, // почему дабл?
                                ResourceStatus.Online,
                                RosterItemEntity.Contact,
                                if (T.unread <= 0) "" else T.unread.toString(),
                                0, T.color, T.avatar, null
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
                T.jid,
                T.rosterItem!!.nickname,
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
                if (T.unread <= 0) "" else T.unread.toString(),
                0, T.color, T.avatar, null
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

    fun clearHistoryChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.lastMessage = null
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
