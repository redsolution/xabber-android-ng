package com.xabber.presentation.application.fragments.chatlist.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.ChatListDto
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListForwardViewModel: ViewModel() {
  private val realm = Realm.open(defaultRealmConfig())

//    fun getChatList(): ArrayList<ChatListDto> {
//        viewModelScope.launch(Dispatchers.IO) {
//            val realmList =
//                realm.query(
//                    LastChatsStorageItem::class,
//                    query
//                ).find()
//            val dataSource = ArrayList<ChatListDto>()
//            dataSource.addAll(realmList.map { T ->
//                ChatListDto(
//                    id = T.primary,
//                    owner = T.owner,
//                    opponentJid = T.opponentJid,
//                    opponentNickname = if (T.rosterItem != null) T.rosterItem!!.nickname else "",
//                    customNickname = if (T.rosterItem != null) T.rosterItem!!.customNickname else "",
//                    lastMessageBody = if (T.lastMessage == null) "" else T.lastMessage!!.body,
//                    lastMessageDate = if (T.lastMessage == null || T.draftMessage != null) T.messageDate else T.lastMessage!!.date,
//                    lastMessageState = if (T.lastMessage?.state_ == 5 || T.lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
//                    isArchived = T.isArchived,
//                    isSynced = T.isSynced,
//                    draftMessage = T.draftMessage,
//                    hasAttachment = false,
//                    isSystemMessage = false,
//                    isMentioned = false,
//                    muteExpired = T.muteExpired,
//                    pinnedDate = T.pinnedPosition,
//                    status = ResourceStatus.Online,
//                    entity = RosterItemEntity.Contact,
//                    unread = if (T.unread <= 0) "" else T.unread.toString(),
//                    lastPosition = T.lastPosition,
//                    drawableId = T.avatar,
//                    colorId = T.color,
//                    isHide = false,
//                    lastMessageIsOutgoing = if (T.lastMessage != null) T.lastMessage!!.outgoing else false
//                )
//            })
//            chatListDto = dataSource
//            if (chatListDto.size > 0) {
//                chatListDto.add(
//                    0,
//                    ChatListDto(
//                        "",
//                        "",
//                        "",
//                        "",
//                        "",
//                        "",
//                        0,
//                        MessageSendingState.None,
//                        false,
//                        true,
//                        "",
//                        true,
//                        true,
//                        true,
//                        -1,
//                        t,
//                        ResourceStatus.Chat,
//                        RosterItemEntity.Bot,
//                        "",
//                        "",
//                        R.color.grey_500,
//                        R.drawable.flower, true
//                    )
//                )
//                chatListDto.sort()
//            }
//            withContext(Dispatchers.Main) {
//                _chats.value = chatListDto
//            }
//        }
//
//}
}