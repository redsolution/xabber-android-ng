package com.xabber.presentation.application.fragments.chat.message

import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
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
        val isNeedName: Boolean = false,
        val isNeedTail: Boolean = true
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
            previousAuthorTag = "" // Reset so first message after date header always shows nickname
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

    // Precompute tails after all items are built
    computeTails(result)

    return result
}

/**
 * Computes isNeedTail for all MessageItems in the list.
 * Bottom mode: tail on the last message in a same-direction group.
 * Top mode: tail on the first message in a same-direction group.
 */
private fun computeTails(items: MutableList<ChatItem>) {
    val messageIndices = mutableListOf<Int>()
    for (i in items.indices) {
        if (items[i] is ChatItem.MessageItem) messageIndices.add(i)
    }
    if (messageIndices.isEmpty()) return

    val bottom = ChatSettingsManager.bottom
    for (mi in messageIndices.indices) {
        val idx = messageIndices[mi]
        val current = items[idx] as ChatItem.MessageItem
        val msg = current.message

        // Media-only messages (refs but no body) never get tails
        if (msg.references.size > 0 && msg.body.isEmpty()) {
            if (current.isNeedTail) {
                items[idx] = current.copy(isNeedTail = false)
            }
            continue
        }

        val needTail = if (bottom) {
            // Tail if next message in list is different direction or doesn't exist
            val nextMi = mi + 1
            if (nextMi >= messageIndices.size) true
            else {
                val nextMsg = (items[messageIndices[nextMi]] as ChatItem.MessageItem).message
                msg.outgoing != nextMsg.outgoing
            }
        } else {
            // Tail if previous message in list is different direction or doesn't exist
            val prevMi = mi - 1
            if (prevMi < 0) true
            else {
                val prevMsg = (items[messageIndices[prevMi]] as ChatItem.MessageItem).message
                msg.outgoing != prevMsg.outgoing
            }
        }

        if (current.isNeedTail != needTail) {
            items[idx] = current.copy(isNeedTail = needTail)
        }
    }
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

    // Recompute tails for the entire list (including existing items that may lose their tail)
    computeTails(result)

    return result
}