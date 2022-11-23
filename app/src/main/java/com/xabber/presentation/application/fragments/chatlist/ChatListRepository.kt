package com.xabber.presentation.application.fragments.chatlist

import com.xabber.model.dto.ChatListDto

interface ChatListRepository {

    fun getChatList()

    fun insertChat(chatListDto: ChatListDto)

    fun pinChat(id: String)

    fun unpinChat(id: String)

    fun setMuteChat(id: String, muteExpired: Long)

    fun movieChatToArchive(id: String)

    fun clearHistory(id: String)

    fun deleteChat(id: String)

}