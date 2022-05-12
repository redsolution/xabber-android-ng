package com.xabber.presentation.application.fragments.chat

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ChatDto
import com.xabber.data.dto.MessageState
import com.xabber.data.dto.ResourceStatus
import com.xabber.data.dto.RosterItemEntity
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
            "London is a capital of great England. We want to go here. Refhugvhj hjjkl kkl;k",
            "2022 04 26 17:05:57 вт",
            MessageState.READ,
            false,
            true,
            ResourceStatus.ONLINE,
            RosterItemEntity.CONTACT,
            "",
            getRandomColor(),
            false,
            true,
            null,
            false,
            false, false, false
        ),

        ChatDto(
            2,
            "qwe1",
            "qwe1",
            "Лев Белоусов",
            "Hi! Where are you?",
            "2022 04 27 17:05:57 ср",
            MessageState.DELIVERED,
            false,
            true,
            ResourceStatus.XA,
            RosterItemEntity.CONTACT,
            "",
            getRandomColor(),
            true,
            false,
            null,
            false,
            true, false, true
        ),
        ChatDto(
            3,
            "qwe2",
            "qwe2",
            "Кирилл Петров",
            "I don't know",
            "2022 02 24 17:05:57 чт",
            MessageState.ERROR,
            true,
            true,
            ResourceStatus.OFFLINE,
            RosterItemEntity.CONTACT,
            "99",
            getRandomColor(),
            false,
            true,
            "Username",
            false,
            true, false, false),

            ChatDto(
            4,
            "qwe2",
            "qwe2",
            "Ирина Верина",
            "qwe2",
            "2021 11 13 12:05:01 сб",
            MessageState.NONE,
            true,
            true,
            ResourceStatus.AWAY,
            RosterItemEntity.CONTACT,
            "",
            getRandomColor(),
            false,
            false,
            "Username",
            false,
            true, false, true),

           ChatDto(
            5,
            "qwe2",
            "qwe2",
            "Алексей Иванов",
            "Yes of course",
            "2022 02 24 17:05:57 чт",
            MessageState.ERROR,
            true,
            true,
            ResourceStatus.DND,
            RosterItemEntity.CONTACT,
            "",
            getRandomColor(),
            false,
            true,
            "Username",
            false,
            true, false, true),

            ChatDto(
            6,
            "qwe2",
            "qwe2",
            "Ирина Сидорова",
            "qwe2",
            "2021 11 13 12:05:01 сб",
            MessageState.ERROR,
            true,
            true,
            ResourceStatus.CHAT,
            RosterItemEntity.CONTACT,
            "",
            getRandomColor(),
            false,
            false,
            "Username",
            false,
            true, false, false),

              ChatDto(
            7,
            "qwe2",
            "qwe2",
            "Кристина Собакина",
            "qwe2",
            "2021 11 13 12:05:01 сб",
            MessageState.NOT_SENT,
            true,
            true,
            ResourceStatus.ONLINE,
            RosterItemEntity.BOT,
            "1",
            getRandomColor(),
            false,
            false,
            "Username",
            false, false, false, false),


               ChatDto(
            8,
            "qwe2",
            "qwe2",
            "Павел Федоров",
            "qwe2",
            "2021 11 13 12:05:01 сб",
            MessageState.UPLOADING,
            false,
            true,
            ResourceStatus.XA,
            RosterItemEntity.BOT,
            "",
            getRandomColor(),
            false,
            true,
            "Username",
            true,
            true, false, false),

            ChatDto(
                9,
                "qwe2",
                "qwe2",
                "Юлия Артемьева",
                "Что случилось?",
                "2021 11 13 12:05:01 сб",
                MessageState.SENT,
                false,
                false,
                ResourceStatus.OFFLINE,
                RosterItemEntity.BOT,
                "",
                getRandomColor(),
                false,
                true,
                "Username",
                true,
                true, false, false),
             ChatDto(
                10,
                "qwe2",
                "qwe2",
                "Андрей Гаврилов",
                "Купи хлеб",
                "2021 11 13 12:05:01 сб",
                MessageState.SENT,
                false,
                false,
                ResourceStatus.AWAY,
                RosterItemEntity.BOT,

                "",
                getRandomColor(),
                false,
                true,
                "Username",
                true,
                false, false, false),
             ChatDto(
                11,
                "qwe2",
                "qwe2",
                "Арина Рыбина",
                "Документы забрала",
                "2021 11 13 12:05:01 сб",
                MessageState.SENT,
                false,
                false,
                ResourceStatus.DND,
                RosterItemEntity.BOT,

                "",
                getRandomColor(),
                false,
                true,
                "Username",
                true,
                false, false, false),
             ChatDto(
                12,
                "qwe2",
                "qwe2",
                "Сергей Сергеев",
                "Книги",
                "2021 11 13 12:05:01 сб",
                MessageState.SENT,
                false,
                false,
                ResourceStatus.CHAT,
                RosterItemEntity.BOT,
                "",
                getRandomColor(),
                false,
                true,
                "Username",
                true,
                false, false, true),
              ChatDto(
                13,
                "qwe2",
                "qwe2",
                "Юрий Алексеев",
                "",
                "2021 11 13 12:05:01 сб",
                MessageState.SENT,
                false,
                false,
                ResourceStatus.ONLINE,
                RosterItemEntity.GROUP,
                "",
                getRandomColor(),
                false,
                true,
                "Username",
                true,
                false, false, true),
              ChatDto(
                14,
                "qwe2",
                "qwe2",
                "Иван Данилов",
                "мне завтра на работу",
                "2021 11 13 12:05:01 сб",
                MessageState.SENT,
                false,
                false,
                ResourceStatus.AWAY,
                RosterItemEntity.GROUP,

                "8",
                getRandomColor(),
                false,
                true,
                "Username",
                true,
                false, false, true),
               ChatDto(
                15,
                "qwe2",
                "qwe2",
                "Степан Степанов",
                "мне завтра на работу",
                "2021 11 13 12:05:01 сб",
                MessageState.SENT,
                false,
                false,
                ResourceStatus.ONLINE,
                RosterItemEntity.SERVER,
                "5",
                getRandomColor(),
                false,
                true,
                "Username",
                true,
                false, false, false),
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
