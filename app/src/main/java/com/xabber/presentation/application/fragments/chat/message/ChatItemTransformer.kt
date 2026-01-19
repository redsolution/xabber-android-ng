package com.xabber.presentation.application.fragments.chat.message

import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.message.ChatItem
import com.xabber.utils.StringUtils.getDateStringForMessage
import com.xabber.utils.isSameDayWith

object ChatItemTransformer {

    fun transformMessages(
        messages: List<MessageStorageItem>,
        unreadCount: Int = 0
    ): List<ChatItem> {
        val result = mutableListOf<ChatItem>()
        var previousDate: Long? = null
        var unreadMarkerAdded = false

        messages.forEachIndexed { index, message ->
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
}