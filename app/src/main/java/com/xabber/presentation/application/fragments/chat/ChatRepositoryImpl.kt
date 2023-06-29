package com.xabber.presentation.application.fragments.chat

import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.utils.toMessageReferenceDto
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatRepositoryImpl(): ChatRepository {
//    private val realm = Realm.open(defaultRealmConfig())
//
   override fun loadChat() {
       TODO("Not yet implemented")
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