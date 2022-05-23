package com.xabber.presentation.application.fragments.chatlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ChatListDto
import com.xabber.defaultRealmConfig
import com.xabber.xmpp.chat_states.ComposingType
import com.xabber.xmpp.last_chats.LastChatsStorageItem
import com.xabber.xmpp.messages.MessageSendingState
import com.xabber.xmpp.presences.ResourceStatus
import com.xabber.xmpp.presences.RosterItemEntity
import io.realm.Realm
import io.realm.query

class ChatListViewModel : ViewModel() {
    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList

    fun getChatList() {
        val realm = Realm.open(defaultRealmConfig())

        val chatList = realm
            .query<LastChatsStorageItem>()
            .find()
        _chatList.value = chatList.map { T ->
            ChatListDto(
                T.primary,
                T.owner,
                T.jid,
                "",
                T.lastMessage!!.body,
                T.messageDate,
              MessageSendingState.None,
                T.isArchived,
                T.isSynced,
                T.draftMessage,
                false, // hasAttachment
                false, // isSystemMessage
                false, //isMentioned
                T.muteExpired,
                T.pinnedPosition, // почему дабл?
                ResourceStatus.Offline,
                RosterItemEntity.Contact,
                T.unread.toString()


            )
        }
        realm.close()
    }


    fun movieChatToArchive(id: String) {
        //    chatRepository.movieChatToArchive(id)
        //     chat.value = chatRepository.getChatList()
//        for (i in 0 until chat.value!!.size) {
//            if (chat.value!![i].id == id) {
//                val archivedChat = chat.value!![i].copy(isArchived = !chat.value!![i].isArchived)
        //    chat.value!!.re(i)
        //    chat.value!!.add(archivedChat)
    }


    fun deleteChat(id: String) {
        //  chatRepository.deleteChat(id)
        //     chat.value = chatRepository.getChatList()
    }

    fun pinChat(id: String) {
        //    chatRepository.pinChat(id)


//        for (i in 0 until chat.value!!.size) {
//            if (chat.value!![i].id == id) {
//                val pinnedChat = chat.value!![i].copy(isPinned = true)
        //  chat.value!!.removeAt(i)
        // chat.value!!.add(pinnedChat)
        //    }
        // }

    }

    fun unPinChat(id: String) {
//        for (i in 0 until chat.value!!.size) {
//            if (chat.value!![i].id == id) {
//                val pinnedChat = chat.value!![i].copy(isPinned = false)
//         //       chat.value!!.removeAt(i)
//           //     chat.value!!.add(pinnedChat)
//            }
//        }
    }

    fun turnOfNotifications(id: String) {
        //    chatRepository.turnOfNotifications(id)

    }
}
