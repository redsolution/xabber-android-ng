package com.xabber.presentation.application.fragments.chat

import android.util.Log
import com.xabber.data.dto.ChatDto
import com.xabber.presentation.application.util.getRandomColor
import java.util.*

class ChatRepository() {

    fun getChatList() : List<ChatDto> {
         val list: List<ChatDto> = listOf(
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    true
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    true
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    true
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    false
                ),
                ChatDto(
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
                    true
                ),
                ChatDto(
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
                    false
                ),
            )
            val mutableList: MutableList<ChatDto> = mutableListOf()

            while (mutableList.size < 1500) {
                mutableList.addAll(
                    list.map {
                        it.jid = UUID.randomUUID().toString()
                        it
                    }
                )
                Log.d("qwe", mutableList.size.toString())
            }
        return mutableList
    }
}