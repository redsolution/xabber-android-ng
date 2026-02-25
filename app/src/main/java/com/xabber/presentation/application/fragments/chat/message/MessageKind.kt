package com.xabber.presentation.application.fragments.chat.message

import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.utils.StringUtils.getDateStringForMessage
import java.util.Calendar


enum class MessageKind {
    TEXT,
    IMAGE,
    FILE,
    STICKER,
    VOICE,
    DATE,
    SYSTEM,
    CALL,
    INITIAL,
    SKELETON
}

sealed class ChatItem {
    data class MessageItem(val message: MessageStorageItem) : ChatItem()
    data class DateHeaderItem(val date: Long, val formattedDate: String) : ChatItem()
//    data class UnreadMarkerItem(val count: Int) : ChatItem()

    val id: String
        get() = when (this) {
            is MessageItem -> "message_${message.primary}"
            is DateHeaderItem -> "date_${date}"
//            is UnreadMarkerItem -> "unread_${System.currentTimeMillis()}"
        }
}

// Вспомогательная функция для трансформации
fun List<MessageStorageItem>.toChatItems(unreadCount: Int = 0): List<ChatItem> {
    val result = ArrayList<ChatItem>(this.size + this.size / 20 + 1) // pre-size: messages + ~1 date header per 20 msgs
    var previousDayKey = Long.MIN_VALUE
    var unreadMarkerAdded = false

    // Reuse a single Calendar instance for all date calculations
    val cal = Calendar.getInstance()

    for (message in this) {
        // Добавляем маркер непрочитанных сообщений перед первым непрочитанным
        if (!unreadMarkerAdded && unreadCount > 0 && !message.isRead && !message.outgoing) {
//            result.add(ChatItem.UnreadMarkerItem(unreadCount))
            unreadMarkerAdded = true
        }

        // Проверяем, нужен ли заголовок с датой (reuse single Calendar)
        val currentDate = message.sentDate
        cal.timeInMillis = currentDate
        val currentDayKey = cal.get(Calendar.YEAR).toLong() * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        if (currentDayKey != previousDayKey) {
            result.add(ChatItem.DateHeaderItem(
                date = currentDate,
                formattedDate = getDateStringForMessage(currentDate)
            ))
            previousDayKey = currentDayKey
        }

        // Добавляем само сообщение
        result.add(ChatItem.MessageItem(message))
    }

    return result
}