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
    data class UnreadMarkerItem(val count: Int) : ChatItem()

    val id: String
        get() = when (this) {
            is MessageItem -> "message_${message.primary}"
            is DateHeaderItem -> "date_${date}"
            is UnreadMarkerItem -> "unread_${System.currentTimeMillis()}"
        }
}

// Вспомогательная функция для трансформации
fun List<MessageStorageItem>.toChatItems(unreadCount: Int = 0): List<ChatItem> {
    val result = mutableListOf<ChatItem>()
    var previousDate: Long? = null
    var unreadMarkerAdded = false

    this.forEachIndexed { index, message ->
        // Добавляем маркер непрочитанных сообщений перед первым непрочитанным
        if (!unreadMarkerAdded && unreadCount > 0 && !message.isRead && !message.outgoing) {
            result.add(ChatItem.UnreadMarkerItem(unreadCount))
            unreadMarkerAdded = true
        }

        // Проверяем, нужен ли заголовок с датой
        val currentDate = message.sentDate
        if (previousDate == null || !currentDate.isSameDayWith(previousDate!!)) {
            result.add(ChatItem.DateHeaderItem(
                date = currentDate,
                formattedDate = getDateStringForMessage(currentDate)
            ))
            previousDate = currentDate
        }

        // Добавляем само сообщение
        result.add(ChatItem.MessageItem(message))
    }

    return result
}

// Добавим расширение для проверки дня
private fun Long.isSameDayWith(other: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = this@isSameDayWith }
    val cal2 = Calendar.getInstance().apply { timeInMillis = other }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}