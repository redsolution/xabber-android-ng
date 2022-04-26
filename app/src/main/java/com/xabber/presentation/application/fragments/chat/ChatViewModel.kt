package com.xabber.presentation.application.fragments.chat

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
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
            Date(1643155200),
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
            Date(1643328000),
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
            "Артемий Маслов",
            "qwe2",
            Date(1643241600),
            MessageState.ERROR,
            true,
            true,
            ResourceStatus.CHAT,
            RosterItemEntity.GROUP_CHAT,
            1,
            "unread1",
            getRandomColor(),
            false,
            false,
            "Username",
            false,
            true, false)))

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
        chatRepository.deleteChat(id)
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
        chatRepository.turnOfNotifications(id)

    }
}
