package com.xabber.presentation.application.fragments.chat

import android.util.Log
import android.widget.Toast
import com.xabber.data.dto.ChatDto
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.application.util.getRandomColor
import java.util.*

class ChatRepository {
    private var list = mutableListOf(
        ChatDto(
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
            true, false
        ),
        ChatDto(
            4,
            "qwe3",
            "qwe3",
            "Тимофей Дорофеев",
            "qwe3",
            Date(1643068800),
            MessageState.NONE,
            false,
            true,
            ResourceStatus.DND,
            RosterItemEntity.INCOGNITO_CHAT,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            true,
            false, false
        ),
        ChatDto(
            5,
            "qwe4",
            "qwe4",
            "Таисия Михеева",
            "qwe4",
            Date(1642982400),
            MessageState.NOT_SENT,
            false,
            true,
            ResourceStatus.OFFLINE,
            RosterItemEntity.ISSUE,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            false,
            false, false
        ),
        ChatDto(
            6,
            "qwe5",
            "qwe5",
            "Виктория Яковлева",
            "qwe5",
            Date(1642896000),
            MessageState.SENDING,
            false,
            true,
            ResourceStatus.XA,
            RosterItemEntity.PRIVATE_CHAT,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            false,
            false, false
        ),
        ChatDto(
            7,
            "qwe6",
            "qwe6",
            "Виктория Яковлева",
            "qwe6",
            Date(1642809600),
            MessageState.SENT,
            true,
            true,
            ResourceStatus.ONLINE,
            RosterItemEntity.SERVER,
            2,
            "unread1",
            getRandomColor(),
            false,
            false,
            null,
            true,
            true, false
        ),
        ChatDto(
            8,
            "qwe7",
            "qwe7",
            "Анна Симонова",
            "qwe7",
            Date(1642723200),
            MessageState.UPLOADING,
            false,
            true,
            ResourceStatus.ONLINE,
            RosterItemEntity.CONTACT,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            false,
            false, false
        ),
        ChatDto(
            9,
            "qwe",
            "qwe",
            "Макар Одинцов",
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
            10,
            "qwe1",
            "qwe1",
            "Сафия Герасимова",
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
            11,
            "qwe2",
            "qwe2",
            "Ярослав Руднев",
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
            true, false
        ),
        ChatDto(
            12,
            "qwe3",
            "qwe3",
            "Илья Шилов",
            "qwe3",
            Date(1643068800),
            MessageState.NONE,
            false,
            true,
            ResourceStatus.DND,
            RosterItemEntity.INCOGNITO_CHAT,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            true,
            false, false
        ),
        ChatDto(
            13,
            "qwe4",
            "qwe4",
            "Константин Мухин",
            "qwe4",
            Date(1642982400),
            MessageState.NOT_SENT,
            false,
            true,
            ResourceStatus.OFFLINE,
            RosterItemEntity.ISSUE,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            false,
            false, false
        ),
        ChatDto(
            14,
            "qwe5",
            "qwe5",
            "Григорий Васильев",
            "qwe5",
            Date(1642896000),
            MessageState.SENDING,
            false,
            true,
            ResourceStatus.XA,
            RosterItemEntity.PRIVATE_CHAT,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            false,
            false, false
        ),
        ChatDto(
            15,
            "qwe6",
            "qwe6",
            "Эмилия Новикова",
            "qwe6",
            Date(1642809600),
            MessageState.SENT,
            true,
            true,
            ResourceStatus.ONLINE,
            RosterItemEntity.SERVER,
            2,
            "unread1",
            getRandomColor(),
            false,
            false,
            null,
            true,
            true, false
        ),
        ChatDto(
            16,
            "qwe7",
            "qwe7",
            "Злата Куликова",
            "qwe7",
            Date(1642723200),
            MessageState.UPLOADING,
            false,
            true,
            ResourceStatus.ONLINE,
            RosterItemEntity.CONTACT,
            0,
            null,
            getRandomColor(),
            false,
            false,
            null,
            false,
            false,
            false
        )
    )

    fun getChatList(): List<ChatDto> = list

    fun movieChatToArchive(id: Int) {
        for (i in 0 until list.size) {
            if (list[i].id == id) {
                val pinnedChat = list[i].copy(isPinned = true)
                list.removeAt(i)
                list.add(pinnedChat)
            }
        }
    }

    fun deleteChat(id: Int) {
        for (i in 0 until list.size) {
            if (list[i].id == id) list.removeAt(i)
        }
    }

    fun pinChat(id: Int) {
        for (i in 0 until list.size) {
            if (list[i].id == id) {
                val pinnedChat = list[i].copy(isPinned = true)
                list.removeAt(i)
                list.add(pinnedChat)
            }
        }

    }

    fun turnOfNotifications(id: Int) {
        for (i in 0 until list.size) {
            if (list[i].id == id) {
                val mutedChat = list[i].copy(isMuted = true)
               list.removeAt(i)
            //    list.add(mutedChat)
                list = mutableListOf()
            }
        }
    }


}
