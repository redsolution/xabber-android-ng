package com.xabber.presentation.application.fragments.chat

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.FileDto
import com.xabber.data.dto.MessageDto
import com.xabber.data.xmpp.messages.MessageDisplayType
import com.xabber.data.xmpp.messages.MessageSendingState

class ChatViewModel : ViewModel() {
    private val _messages = MutableLiveData<ArrayList<MessageDto>>()
    val messages: LiveData<ArrayList<MessageDto>> = _messages
    private val messageList = ArrayList<MessageDto>()

    private val _miniatures = MutableLiveData<ArrayList<FileDto>>()
    val miniatures: LiveData<ArrayList<FileDto>> = _miniatures

    fun initList() {
        for (i in 0..5000) {
            val timeStamp = 5623450975L
            val secondTimeStamp = 56234509876L
            messageList.add(
                    MessageDto(
                        "$i",
                        true,
                        "Геннадий Белов",
                        "Кирилл Степанов",
                        "I am go home. See you!",
                        MessageSendingState.Read,
                        timeStamp+i,
                        null,
                        MessageDisplayType.Text,
                        false,
                        false,
                        null, false
                    )
                    )
            messageList.add(
                MessageDto(
                    "${i+ 5000}",
                    false,
                    "Кирилл Степанов",
                    "Геннадий Белов",
                    "What are you doing?",
                    MessageSendingState.Read,
                    secondTimeStamp,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, false
                )
            )
          }
            messageList.add(
                MessageDto(
                    "10003",
                    true,
                    "Геннадий Белов",
                    "Кирилл Степанов",
                    "В центре Челябинска утром в пятницу, 3 июня, сошел трамвай с рельсов. Об этом 74.RU сообщили очевидцы. Авария произошла при попытке вагона выехать с улицы Кирова на проспект Победы — там такие происшествия далеко не редкость.",
                    MessageSendingState.Deliver,
                    1654237345665,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, false
                )
            )
               messageList.add(
                MessageDto(
                    "10004",
                    true,
                    "Геннадий Белов",
                    "Кирилл Степанов",
                    "Представляешь?",
                    MessageSendingState.Deliver,
                    1654234646664,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, false
                )
            )
             messageList.add(
                MessageDto(
                    "10005",
                    true,
                    "Геннадий Белов",
                    "Кирилл Степанов",
                    "Представляешь?",
                    MessageSendingState.Deliver,
                 1654294347660,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, false
                )
            )
            messageList.add(
                MessageDto(
                    "10006",
                    true,
                    "Геннадий Белов",
                    "Кирилл Степанов",
                    "Да?",
                    MessageSendingState.Sending,
                  1654239348650,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, false
                )
            )
             messageList.add(MessageDto(
                    "10007",
                    false,
                    "Кирилл Степанов",
                    "Геннадий Белов",
                    "Ничего себе. Вот это новости. Я люблю ездить на трамваях. Но хочу купить машину",
                    MessageSendingState.Read,
                    1654234399640,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, true
                )
            )
            messageList.add(MessageDto(
                "10008",
                false,
                "Кирилл Степанов",
                "Геннадий Белов",
                "Сегодня хорошая погода",
                MessageSendingState.NotSended,
                1654234345632,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null, true
            )
            )
             messageList.add(
                MessageDto(
                    "10009",
                    true,
                    "Геннадий Белов",
                    "Кирилл Степанов",
                    "Да, неплохая",
                    MessageSendingState.NotSended,
                    1654234345601,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, true
                )
            )
             messageList.add(
                MessageDto(
                    "10010",
                    true,
                    "Геннадий Белов",
                    "Кирилл Степанов",
                    "Да, неплохая",
                    MessageSendingState.Sended,
                    1654234345580,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null, false
                )
            )
            messageList.add(
                MessageDto(
                    "22222",
                    true,
                    "Ann",
                    "Геннадий Белов",
                    "Алексей присоединился к чату",
                    MessageSendingState.Read,
                    1654234345585,
                    null,
                    MessageDisplayType.System,
                    false,
                    false,
                    null,false
                )
            )


        messageList
        _messages.value = messageList
    }

    fun addFile(fileDto: FileDto) {
        _miniatures.value?.add(fileDto)
    }

    fun clearMiniatures() {
        _miniatures.value?.clear()
    }

    fun insertMessage(messageDto: MessageDto) {
        messageList.add(messageDto)
        _messages.value = messageList
    }

    fun deleteMessage(messageDto: MessageDto) {
        messageList.remove(messageDto)
        _messages.value = messageList
    }
}