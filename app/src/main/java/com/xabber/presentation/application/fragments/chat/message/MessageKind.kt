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
    abstract val id: String

    data class MessageItem(
        val message: MessageStorageItem,
        val isNeedName: Boolean = false
    ) : ChatItem() {
        override val id: String = "message_${message.primary}"
    }
    data class DateHeaderItem(val date: Long, val formattedDate: String) : ChatItem() {
        override val id: String = "date_$date"
    }
//    data class UnreadMarkerItem(val count: Int) : ChatItem() {
//        override val id: String = "unread_${System.currentTimeMillis()}"
//    }
}

// Вспомогательная функция для трансформации
fun List<MessageStorageItem>.toChatItems(unreadCount: Int = 0, isGroup: Boolean = false): List<ChatItem> {
    val result = ArrayList<ChatItem>(this.size + this.size / 20 + 1) // pre-size: messages + ~1 date header per 20 msgs
    var previousDayKey = Long.MIN_VALUE
    var unreadMarkerAdded = false
    var previousAuthorTag = ""

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

        // Precompute isNeedName for group chats (avoids Realm access during scroll)
        val isSystem = message.displayAs_ == "system"
        val needName = if (isGroup && !isSystem) {
            val authorTag = message.groupchatAuthorId ?: if (message.outgoing) "__self__" else ""
            val need = authorTag != previousAuthorTag
            previousAuthorTag = authorTag
            need
        } else false

        result.add(ChatItem.MessageItem(message, needName))
    }

    return result
}

/**
 * Incrementally appends new messages to an existing ChatItems list.
 * Only processes messages starting from [newStartIndex], avoiding
 * the O(n) full recomputation of toChatItems() for the common case
 * of new messages arriving at the end of the conversation.
 */
fun appendToChatItems(
    existingItems: List<ChatItem>,
    allMessages: List<MessageStorageItem>,
    newStartIndex: Int,
    isGroup: Boolean = false
): List<ChatItem> {
    if (newStartIndex >= allMessages.size) return existingItems

    val newCount = allMessages.size - newStartIndex
    val result = ArrayList<ChatItem>(existingItems.size + newCount + 2)
    result.addAll(existingItems)

    // Determine the last day key and last author tag from the existing items
    var lastDayKey = Long.MIN_VALUE
    var previousAuthorTag = ""
    if (existingItems.isNotEmpty()) {
        val lastDate = when (val last = existingItems.last()) {
            is ChatItem.MessageItem -> {
                if (isGroup) {
                    val msg = last.message
                    previousAuthorTag = msg.groupchatAuthorId ?: if (msg.outgoing) "__self__" else ""
                }
                last.message.sentDate
            }
            is ChatItem.DateHeaderItem -> last.date
        }
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastDate
        lastDayKey = cal.get(Calendar.YEAR).toLong() * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    val cal = Calendar.getInstance()
    for (i in newStartIndex until allMessages.size) {
        val message = allMessages[i]
        cal.timeInMillis = message.sentDate
        val currentDayKey = cal.get(Calendar.YEAR).toLong() * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        if (currentDayKey != lastDayKey) {
            result.add(ChatItem.DateHeaderItem(
                date = message.sentDate,
                formattedDate = getDateStringForMessage(message.sentDate)
            ))
            lastDayKey = currentDayKey
        }

        val isSystem = message.displayAs_ == "system"
        val needName = if (isGroup && !isSystem) {
            val authorTag = message.groupchatAuthorId ?: if (message.outgoing) "__self__" else ""
            val need = authorTag != previousAuthorTag
            previousAuthorTag = authorTag
            need
        } else false

        result.add(ChatItem.MessageItem(message, needName))
    }

    return result
}