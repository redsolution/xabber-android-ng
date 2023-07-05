package com.xabber.presentation.application.fragments.chat

import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun loadChat(primary: String): Flow<ChatListDto?>

    fun observeMessages(): Flow<List<MessageDto>>
}