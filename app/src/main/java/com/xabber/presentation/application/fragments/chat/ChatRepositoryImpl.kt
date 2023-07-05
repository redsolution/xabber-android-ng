package com.xabber.presentation.application.fragments.chat

import com.xabber.data_base.dao.LastChatStorageItemDao
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ChatRepositoryImpl() : ChatRepository {
    private val realm = Realm.open(defaultRealmConfig())

    val lastChatDao = LastChatStorageItemDao(realm)

    override fun loadChat(primary: String): Flow<ChatListDto?> {
        val lastChatsStorageItem = lastChatDao.getItemByPrimary(primary)
        val chatListDto = lastChatsStorageItem?.toChatListDto()
        return flowOf(chatListDto)
    }

    override fun observeMessages(): Flow<List<MessageDto>> {
        TODO("Not yet implemented")
//        val request =
//            realm.query(MessageStorageItem::class, "owner = '$owner' && opponent = '$opponentJid'")
//        val lastChatsFlow = request.asFlow()
//        viewModelScope.launch(Dispatchers.IO) {
//            lastChatsFlow.collect { changes: ResultsChange<MessageStorageItem> ->
//                when (changes) {
//                    is UpdatedResults -> {
//                        changes.list
//                        val list = ArrayList<MessageDto>()
//                        list.addAll(changes.list.map { T ->
//                            MessageDto(
//                                primary = T.primary,
//                                isOutgoing = T.outgoing,
//                                owner = T.owner,
//                                opponentJid = T.opponent,
//                                messageBody = T.body,
//                                MessageSendingState.Read,
//                                sentTimestamp = T.sentDate,
//                                editTimestamp = T.editDate,
//                                MessageDisplayType.Text,
//                                canEditMessage = false,
//                                canDeleteMessage = false,
//                                null,
//                                false,
//                                null,
//                                false,
//                                references= T.references.map { T -> T.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
//                                isChecked = selectedItems.contains(T.primary),
//                                isUnread = !T.isRead
//                            )
//                        })
//                        count = 0
//                        for (i in 0 until list.size) {
//                            if (list[i].isUnread) count++
//                        }
//                        messageList = list
//                        messageList.sort()
//                        withContext(Dispatchers.Main) {
//                            _messages.value = messageList
//                            _unreadCount.value = count
//                        }
//                    }
//                    else -> {}
//                }
//            }
//        }
    }
}