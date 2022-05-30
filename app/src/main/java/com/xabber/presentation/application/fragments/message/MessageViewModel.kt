package com.xabber.presentation.application.fragments.message

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.MessageDto
import com.xabber.data.xmpp.messages.MessageDisplayType
import com.xabber.data.xmpp.messages.MessageSendingState

class MessageViewModel : ViewModel() {
    private val _messages = MutableLiveData<List<MessageDto>>()
    val messages: LiveData<List<MessageDto>> = _messages

    init {
        _messages.value = listOf(
            MessageDto(
                "1",
                false,
                "Алескей Иванов",
                "Геннадий Белов",
                "Hi! What are you doing?",
                MessageSendingState.Sending,
                System.currentTimeMillis(),
                null,
                MessageDisplayType.Text,
                false,
                false
            ), MessageDto(
                "1",
                false,
                "Алескей Иванов",
                "Геннадий Белов",
                "Hi! What are you doing?",
                MessageSendingState.Sending,
                System.currentTimeMillis(),
                null,
                MessageDisplayType.Text,
                false,
                false
            ),
               MessageDto(
                "2",
                true,
                "Ирина Андреева",
                "Геннадий Белов",
                "I want go home",
                MessageSendingState.Sended,
                System.currentTimeMillis(),
                null,
                MessageDisplayType.Text,
                false,
                false
            ), MessageDto(
                "1",
                false,
                "Алескей Иванов",
                "Геннадий Белов",
                "Hi! What are you doing?",
                MessageSendingState.Error,
                System.currentTimeMillis(),
                null,
                MessageDisplayType.Text,
                false,
                false
            )
        )



    }
}