package com.xabber.presentation.application.fragments.chatlist

import androidx.annotation.ColorRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xabber.R
import com.xabber.data.dto.ChatListDto
import com.xabber.data.dto.ContactDto
import com.xabber.defaultRealmConfig
import com.xabber.presentation.application.activity.ApplicationViewModel
import com.xabber.data.xmpp.last_chats.LastChatsStorageItem
import com.xabber.data.xmpp.messages.MessageSendingState
import com.xabber.data.xmpp.presences.ResourceStatus
import com.xabber.data.xmpp.presences.RosterItemEntity
import com.xabber.presentation.application.activity.BdCommunicator
import io.realm.Realm
import io.realm.query

class ChatListViewModel() : ApplicationViewModel() {
    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList

    fun getChatList() {
        val realm = Realm.open(defaultRealmConfig())
        val list = ArrayList<ChatListDto>()
        list.add(ChatListDto("1", "Иван Сергеев", "Иван Сергеев", "Иван Сергеев", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.green_500, R.drawable.rayan, ContactDto("1", "Иван Сергеев", "Иван Сергеев", "Иван", "Сергеев","ivan@xabber.com", R.color.green_500, R.drawable.rayan, null)))
        list.add(ChatListDto("1", "Ирина Иванова", "Ирина Иванова", "Ирина Иванова", "Купи хлеба", System.currentTimeMillis(), MessageSendingState.Deliver, true, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.purple_500, R.drawable.flower, ContactDto("2", "Ирина Иванова", "Ирина Иванова","Ирина", "Иванова","ivanova@xmpp.ru", R.color.purple_500, R.drawable.flower, null) ))
         list.add(ChatListDto("1", "Анна Семенова", "Анна Семенова", "Анна Семенова", "когда? завтра?", System.currentTimeMillis()+1, MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, "1", 0, R.color.yellow_700, R.drawable.kitty, ContactDto("Анна Семенова", "Анна Семенова", "Анна Семенова", "Анна", "Семенова","annasemenova@xabber.com", R.color.yellow_500, R.drawable.kitty, null)))
 list.add(ChatListDto("1", "Олег Панин", "Олег Панин", "Олег Панин", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, "jkl", false, false, false, 0.0, 7.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.red_500, R.drawable.man, ContactDto("Олег Панин", "Олег Панин", "Олег Панин","Олег", "Панин","oleg92@xmpp.ru", R.color.red_500, R.drawable.man, null)))
       list.add(ChatListDto("1", "Иван Сергеев", "Иван Сергеев", "Иван Сергеев", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.green_500, R.drawable.rayan, ContactDto("1", "Иван Сергеев", "Иван Сергеев", "Иван", "Сергеев","ivan@xabber.com", R.color.green_500, R.drawable.rayan, null)))
        list.add(ChatListDto("1", "Ирина Иванова", "Ирина Иванова", "Ирина Иванова", "Купи хлеба", System.currentTimeMillis(), MessageSendingState.Deliver, true, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.purple_500, R.drawable.flower, ContactDto("2", "Ирина Иванова", "Ирина Иванова","Ирина", "Иванова","ivanova@xmpp.ru", R.color.purple_500, R.drawable.flower, null) ))
         list.add(ChatListDto("1", "Анна Семенова", "Анна Семенова", "Анна Семенова", "когда? завтра?", System.currentTimeMillis()+1, MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, "1", 0, R.color.yellow_700, R.drawable.kitty, ContactDto("Анна Семенова", "Анна Семенова", "Анна Семенова", "Анна", "Семенова","annasemenova@xabber.com", R.color.yellow_500, R.drawable.kitty, null)))
 list.add(ChatListDto("1", "Олег Панин", "Олег Панин", "Олег Панин", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, "jkl", false, false, false, 0.0, 7.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.red_500, R.drawable.man, ContactDto("Олег Панин", "Олег Панин", "Олег Панин","Олег", "Панин","oleg92@xmpp.ru", R.color.red_500, R.drawable.man, null)))
              list.add(ChatListDto("1", "Иван Сергеев", "Иван Сергеев", "Иван Сергеев", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.green_500, R.drawable.rayan, ContactDto("1", "Иван Сергеев", "Иван Сергеев", "Иван", "Сергеев","ivan@xabber.com", R.color.green_500, R.drawable.rayan, null)))
        list.add(ChatListDto("1", "Ирина Иванова", "Ирина Иванова", "Ирина Иванова", "Купи хлеба", System.currentTimeMillis(), MessageSendingState.Deliver, true, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.purple_500, R.drawable.flower, ContactDto("2", "Ирина Иванова", "Ирина Иванова","Ирина", "Иванова","ivanova@xmpp.ru", R.color.purple_500, R.drawable.flower, null) ))
         list.add(ChatListDto("1", "Анна Семенова", "Анна Семенова", "Анна Семенова", "когда? завтра?", System.currentTimeMillis()+1, MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, "1", 0, R.color.yellow_700, R.drawable.kitty, ContactDto("Анна Семенова", "Анна Семенова", "Анна Семенова", "Анна", "Семенова","annasemenova@xabber.com", R.color.yellow_500, R.drawable.kitty, null)))
 list.add(ChatListDto("1", "Олег Панин", "Олег Панин", "Олег Панин", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, "jkl", false, false, false, 0.0, 7.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.red_500, R.drawable.man, ContactDto("Олег Панин", "Олег Панин", "Олег Панин","Олег", "Панин","oleg92@xmpp.ru", R.color.red_500, R.drawable.man, null)))
              list.add(ChatListDto("1", "Иван Сергеев", "Иван Сергеев", "Иван Сергеев", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.green_500, R.drawable.rayan, ContactDto("1", "Иван Сергеев", "Иван Сергеев", "Иван", "Сергеев","ivan@xabber.com", R.color.green_500, R.drawable.rayan, null)))
        list.add(ChatListDto("1", "Ирина Иванова", "Ирина Иванова", "Ирина Иванова", "Купи хлеба", System.currentTimeMillis(), MessageSendingState.Deliver, true, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.purple_500, R.drawable.flower, ContactDto("2", "Ирина Иванова", "Ирина Иванова","Ирина", "Иванова","ivanova@xmpp.ru", R.color.purple_500, R.drawable.flower, null) ))
         list.add(ChatListDto("1", "Анна Семенова", "Анна Семенова", "Анна Семенова", "когда? завтра?", System.currentTimeMillis()+1, MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, "1", 0, R.color.yellow_700, R.drawable.kitty, ContactDto("Анна Семенова", "Анна Семенова", "Анна Семенова", "Анна", "Семенова","annasemenova@xabber.com", R.color.yellow_500, R.drawable.kitty, null)))
 list.add(ChatListDto("1", "Олег Панин", "Олег Панин", "Олег Панин", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, "jkl", false, false, false, 0.0, 7.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.red_500, R.drawable.man, ContactDto("Олег Панин", "Олег Панин", "Олег Панин","Олег", "Панин","oleg92@xmpp.ru", R.color.red_500, R.drawable.man, null)))
             list.add(ChatListDto("1", "Иван Сергеев", "Иван Сергеев", "Иван Сергеев", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.green_500, R.drawable.rayan, ContactDto("1", "Иван Сергеев", "Иван Сергеев", "Иван", "Сергеев","ivan@xabber.com", R.color.green_500, R.drawable.rayan, null)))
        list.add(ChatListDto("1", "Ирина Иванова", "Ирина Иванова", "Ирина Иванова", "Купи хлеба", System.currentTimeMillis(), MessageSendingState.Deliver, true, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.purple_500, R.drawable.flower, ContactDto("2", "Ирина Иванова", "Ирина Иванова","Ирина", "Иванова","ivanova@xmpp.ru", R.color.purple_500, R.drawable.flower, null) ))
         list.add(ChatListDto("1", "Анна Семенова", "Анна Семенова", "Анна Семенова", "когда? завтра?", System.currentTimeMillis()+1, MessageSendingState.Deliver, false, true, null, false, false, false, 0.0, 0.0, ResourceStatus.Chat, RosterItemEntity.Contact, "1", 0, R.color.yellow_700, R.drawable.kitty, ContactDto("Анна Семенова", "Анна Семенова", "Анна Семенова", "Анна", "Семенова","annasemenova@xabber.com", R.color.yellow_500, R.drawable.kitty, null)))
 list.add(ChatListDto("1", "Олег Панин", "Олег Панин", "Олег Панин", "когда? завтра?", System.currentTimeMillis(), MessageSendingState.Deliver, false, true, "jkl", false, false, false, 0.0, 7.0, ResourceStatus.Chat, RosterItemEntity.Contact, null, 0, R.color.red_500, R.drawable.man, ContactDto("Олег Панин", "Олег Панин", "Олег Панин","Олег", "Панин","oleg92@xmpp.ru", R.color.red_500, R.drawable.man, null)))
        _chatList .value = list

//        val chatList = realm
//            .query<LastChatsStorageItem>()
//            .find()
//        _chatList.value = chatList.map { T ->
//            ChatListDto(
//                T.primary,
//                T.owner,
//                T.jid,
//                "",
//                T.lastMessage!!.body,
//                T.messageDate,
//                MessageSendingState.None,
//                T.isArchived,
//                T.isSynced,
//                T.draftMessage,
//                false, // hasAttachment
//                false, // isSystemMessage
//                false, //isMentioned
//                T.muteExpired,
//                T.pinnedPosition, // почему дабл?
//                ResourceStatus.Offline,
//                RosterItemEntity.Contact,
//                T.unread.toString()
//
//
//            )
//        }
//        realm.close()
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

    fun setMute() {

    }
}
