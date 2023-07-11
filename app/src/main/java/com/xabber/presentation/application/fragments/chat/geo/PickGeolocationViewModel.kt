package com.xabber.presentation.application.fragments.chat.geo

import android.util.Log
import androidx.lifecycle.ViewModel
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmListOf

class PickGeolocationViewModel: ViewModel() {
    val realm = Realm.open(defaultRealmConfig())

    fun getChat(chatId: String): ChatListDto? {
        var chatListDto: ChatListDto? = null
        realm.writeBlocking {
            val chat =
                this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
            if (chat != null) chatListDto = chat.toChatListDto()
        }
        return chatListDto
    }

    override fun onCleared() {
        super.onCleared()
         realm.close()
    }


    fun insertMessage(chatId: String, messageDto: MessageDto) {
        val rreferences = realmListOf<MessageReferenceStorageItem>()
        realm.writeBlocking {
            for (i in 0 until messageDto.references.size) {
                val ref = this.copyToRealm(MessageReferenceStorageItem().apply {
                    primary = messageDto.references[i].id + "${System.currentTimeMillis()}"
                    uri = messageDto.references[i].uri
                    mimeType = messageDto.references[i].mimeType
                    isGeo = messageDto.references[i].isGeo
                    latitude = messageDto.references[i].latitude
                    longitude = messageDto.references[i].longitude
                    isAudioMessage = messageDto.references[i].isVoiceMessage
                    fileName = messageDto.references[i].fileName
                    fileSize = messageDto.references[i].size
                })
                rreferences.add(ref)
            }

            val message = this.copyToRealm(MessageStorageItem().apply {
                primary = messageDto.primary
                owner = messageDto.owner
                opponent = messageDto.opponentJid
                body = messageDto.messageBody
                date = messageDto.sentTimestamp
                sentDate = messageDto.sentTimestamp
                editDate = messageDto.editTimestamp
                outgoing = messageDto.isOutgoing
                isRead = !messageDto.isUnread
                references = rreferences
                conversationType_ = ConversationType.Channel.toString()
            })
            val item: LastChatsStorageItem? =
                this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
            item?.lastMessage = message
            item?.messageDate = message.date
//                var oldValue = item?.unread ?: 0
//                oldValue++
//                item?.unread = if (messageDto.isOutgoing || isReaded) 0 else oldValue
            item?.lastMessage?.outgoing = messageDto.isOutgoing
            if (item != null) {
                if (!messageDto.isOutgoing && item.muteExpired <= 0) item.isArchived = false
            }
        }
    }


}