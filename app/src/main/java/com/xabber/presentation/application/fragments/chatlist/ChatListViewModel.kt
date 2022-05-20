package com.xabber.presentation.application.fragments.chatlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ChatListDto
import com.xabber.data.dto.MessageState
import com.xabber.data.dto.ResourceStatus
import com.xabber.data.dto.RosterItemEntity
import com.xabber.xmpp.account.AccountStorageItem
import com.xabber.xmpp.chat_states.ComposingType
import com.xabber.xmpp.last_chats.LastChatsStorageItem
import com.xabber.xmpp.messages.MessageStorageItem
import com.xabber.xmpp.presences.ResourceStorageItem
import com.xabber.xmpp.roster.RosterStorageItem
import com.xabber.xmpp.sync.ConversationType
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.query

class ChatListViewModel : ViewModel() {

    private val chatRepository = ChatRepository()
    var chat = MutableLiveData<ArrayList<ChatListDto>>()


    fun getChatList(): List<ChatListDto> {
//        val config =
//            RealmConfiguration.Builder(setOf(LastChatsStorageItem::class, RosterStorageItem::class,
//                MessageStorageItem::class))
//                .build()
//        val realm = Realm.open(config)
//        val chatList = realm
//            .query<LastChatsStorageItem>()
//            .find()
//        val newList = chatList.map { T ->
//            ChatListDto(
//                T.primary,
//                T.owner,
//                T.jid,
//                T.owner,
//                T.lastMessage.toString(),
//                T.lastReadMessageDate,
//                MessageState.READ,
//                false,
//                T.isSynced,
//                ResourceStatus.ONLINE,
//                RosterItemEntity.CONTACT,
//                T.unread.toString(),
//                0,
//                false,
//                false,
//                T.owner,
//                false,
//                T.isPinned,
//                T.isArchived,
//                T.isPrereaded
//            )
//        }
//        realm.close()
//        return newList
        return ArrayList<ChatListDto>()
    }


    fun movieChatToArchive(id: String) {
        //    chatRepository.movieChatToArchive(id)
        //     chat.value = chatRepository.getChatList()
        for (i in 0 until chat.value!!.size) {
            if (chat.value!![i].id == id) {
                val archivedChat = chat.value!![i].copy(isArchived = !chat.value!![i].isArchived)
                chat.value!!.removeAt(i)
                chat.value!!.add(archivedChat)
            }
        }
    }

    fun deleteChat(id: String) {
        //  chatRepository.deleteChat(id)
        //     chat.value = chatRepository.getChatList()
    }

    fun pinChat(id: String) {
        //    chatRepository.pinChat(id)


        for (i in 0 until chat.value!!.size) {
            if (chat.value!![i].id == id) {
                val pinnedChat = chat.value!![i].copy(isPinned = true)
                chat.value!!.removeAt(i)
                chat.value!!.add(pinnedChat)
            }
        }

    }

    fun unPinChat(id: String) {
        for (i in 0 until chat.value!!.size) {
            if (chat.value!![i].id == id) {
                val pinnedChat = chat.value!![i].copy(isPinned = false)
                chat.value!!.removeAt(i)
                chat.value!!.add(pinnedChat)
            }
        }
    }

    fun turnOfNotifications(id: String) {
        //    chatRepository.turnOfNotifications(id)

    }
}
