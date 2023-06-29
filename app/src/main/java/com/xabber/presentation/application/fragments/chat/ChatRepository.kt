package com.xabber.presentation.application.fragments.chat

import com.xabber.dto.MessageDto
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun loadChat()

    fun observeMessages(): Flow<List<MessageDto>>
}