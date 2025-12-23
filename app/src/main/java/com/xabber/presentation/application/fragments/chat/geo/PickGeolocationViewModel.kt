package com.xabber.presentation.application.fragments.chat.geo

import androidx.lifecycle.ViewModel
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.dto.ChatListDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query

class PickGeolocationViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())

    fun getChat(chatId: String): ChatListDto? {
        var chatListDto: ChatListDto? = null
        realm.writeBlocking {
            val chat =
                this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
            if (chat != null) chatListDto = chat.toChatListDto()
        }
        return chatListDto
    }


    fun insertMessage(chatId: String, message: MessageStorageItem) {
        realm.writeBlocking {
            val managedMessage = copyToRealm(message)

            val lastChat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                ?: copyToRealm(LastChatsStorageItem().apply {
                    primary = chatId
                    // Minimal setup; additional fields can be set by caller if needed
                })

            lastChat.apply {
                this.lastMessage = managedMessage
                this.messageDate = managedMessage.sentDate
                this.lastMessageId = managedMessage.messageId

                if (!managedMessage.outgoing && !managedMessage.isRead && muteExpired <= 0) {
                    unread += 1
                } else if (managedMessage.outgoing) {
                    unread = 0
                }

                if (!managedMessage.outgoing && muteExpired <= 0) {
                    isArchived = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }
}