package com.xabber.presentation.application.fragments.chat

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ChatDto
import com.xabber.presentation.application.util.getRandomColor
import java.util.*

class ChatViewModel : ViewModel() {
    private val chatRepository = ChatRepository()
     var chat = MutableLiveData<ArrayList<ChatDto>>()

    init {
        Log.d("Init", "Init   bbbbbbbbbbb")
        chat.value = ArrayList<ChatDto>()
        chat.value!!.addAll(mutableListOf(ChatDto(
            1,
            "qwe",
            "qwe",
            "Наталья Барабанщикова",
            "Attachment",
            "2022 04 26 17:05:57 вт",
            MessageState.READ,
            false,
            true,
            ResourceStatus.ONLINE,
            RosterItemEntity.CONTACT,
            0,
            null,
            getRandomColor(),
            false,
            true,
            null,
            false,
            false, false
        ),

        ChatDto(
            2,
            "qwe1",
            "qwe1",
            "Лев Белоусов",
            "drafted",
            "2022 04 27 17:05:57 ср",
            MessageState.DELIVERED,
            false,
            true,
            ResourceStatus.AWAY,
            RosterItemEntity.BOT,
            0,
            null,
            getRandomColor(),
            true,
            false,
            null,
            false,
            false, false
        ),
        ChatDto(
            3,
            "qwe2",
            "qwe2",
            "Кирилл Петров",
            "qwe2",
            "2022 02 24 17:05:57 чт",
            MessageState.ERROR,
            true,
            true,
            ResourceStatus.CHAT,
            RosterItemEntity.GROUP,
            1,
            "unread1",
            getRandomColor(),
            false,
            false,
            "Username",
            false,
            true, false),
            ChatDto(
            3,
            "qwe2",
            "qwe2",
            "Ирина Сидорова",
            "qwe2",
            "2021 11 13 12:05:01 сб",
            MessageState.ERROR,
            true,
            true,
            ResourceStatus.CHAT,
            RosterItemEntity.GROUP,
            1,
            "unread1",
            getRandomColor(),
            false,
            false,
            "Username",
            false,
            true, false)
        ))

    }

    fun movieChatToArchive(id: Int) {
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

    fun deleteChat(id: Int) {
      //  chatRepository.deleteChat(id)
   //     chat.value = chatRepository.getChatList()
    }

    fun pinChat(id: Int) {
    //    chatRepository.pinChat(id)


         for (i in 0 until chat.value!!.size) {
            if (chat.value!![i].id == id) {
                val pinnedChat = chat.value!![i].copy(isPinned = true)
                chat.value!!.removeAt(i)
               chat.value!!.add(pinnedChat)
            }
        }

    }

    fun unPinChat(id: Int) {
          for (i in 0 until chat.value!!.size) {
            if (chat.value!![i].id == id) {
                val pinnedChat = chat.value!![i].copy(isPinned = false)
                chat.value!!.removeAt(i)
               chat.value!!.add(pinnedChat)
            }
        }
    }

    fun turnOfNotifications(id: Int) {
    //    chatRepository.turnOfNotifications(id)

    }
}
