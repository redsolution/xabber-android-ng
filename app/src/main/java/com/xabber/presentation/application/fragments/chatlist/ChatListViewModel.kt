package com.xabber.presentation.application.fragments.chatlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xabber.R
import com.xabber.model.dto.ChatListDto
import com.xabber.model.dto.ContactDto
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.defaultRealmConfig
import com.xabber.presentation.application.activity.ApplicationViewModel
import io.realm.Realm

class ChatListViewModel() : ApplicationViewModel() {
    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList

    fun getChatList() {
        val realm = Realm.open(defaultRealmConfig())
        val list = ArrayList<ChatListDto>()
        list.add(
            ChatListDto(
                "1",
                "Иван Сергеев",
                "Иван Сергеев",
                "Иван Сергеев",
                "Я подумаю, но не обещаю пока ничего",
                System.currentTimeMillis(),
                MessageSendingState.Sended,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.green_500,
                R.drawable.rayan,
                ContactDto(
                    "1",
                    "Иван Сергеев",
                    "Иван Сергеев",
                    "Иван",
                    "Сергеев",
                    "ivan@xabber.com",
                    R.color.green_500,
                    R.drawable.rayan,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Ирина Иванова",
                "Ирина Иванова",
                "Ирина Иванова",
                "Купи хлеба",
                System.currentTimeMillis(),
                MessageSendingState.Deliver,
                true,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.purple_500,
                R.drawable.flower,
                ContactDto(
                    "2",
                    "Ирина Иванова",
                    "Ирина Иванова",
                    "Ирина",
                    "Иванова",
                    "ivanova@xmpp.ru",
                    R.color.purple_500,
                    R.drawable.flower,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Анна Семенова",
                "Анна Семенова",
                "Анна Семенова",
                "когда? завтра?",
                System.currentTimeMillis() + 1,
                MessageSendingState.NotSended,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                "1",
                0,
                R.color.yellow_700,
                R.drawable.kitty,
                ContactDto(
                    "Анна Семенова",
                    "Анна Семенова",
                    "Анна Семенова",
                    "Анна",
                    "Семенова",
                    "annasemenova@xabber.com",
                    R.color.yellow_500,
                    R.drawable.kitty,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Олег Панин",
                "Олег Панин",
                "Олег Панин",
                "Если что звони на этот номер 88009654322",
                System.currentTimeMillis(),
                MessageSendingState.Error,
                false,
                true,
                "улица Тимирязева",
                false,
                false,
                false,
                0.0,
                7.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.red_500,
                R.drawable.man,
                ContactDto(
                    "Олег Панин",
                    "Олег Панин",
                    "Олег Панин",
                    "Олег",
                    "Панин",
                    "oleg92@xmpp.ru",
                    R.color.red_500,
                    R.drawable.man,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Геннадий Белов",
                "Геннадий Белов",
                "Геннадий Белов",
                "Ну ты даешь",
                System.currentTimeMillis(),
                MessageSendingState.Sending,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                androidx.transition.R.color.material_deep_teal_500,
                R.drawable.car,
                ContactDto(
                    "1",
                    "Геннадий Белов",
                    "Геннадий Белов",
                    "Геннадий",
                    "Белов",
                    "belovn@xabber.com",
                    com.canhub.cropper.R.color.material_deep_teal_500,
                    R.drawable.car,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Кристина Стаханова",
                "Кристина Стаханова",
                "Кристина Стаханова",
                "Не забудь взять паспорт, он в шкафу",
                System.currentTimeMillis(),
                MessageSendingState.NotSended,
                true,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.yellow_700,
                R.drawable.woman,
                ContactDto(
                    "2",
                    "Кристина Стаханова",
                    "Кристина Стаханова",
                    "Кристина",
                    "Стаханова",
                    "kriss@xmpp.ru",
                    R.color.yellow_700,
                    R.drawable.woman,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Кирилл Игнатьев",
                "Кирилл Игнатьев",
                "Кирилл Игнатьев",
                "Все, договорились)",
                System.currentTimeMillis() + 1,
                MessageSendingState.Read,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                "1",
                0,
                R.color.green_500,
                R.drawable.wolf,
                ContactDto(
                    "Кирилл Игнатьев",
                    "Кирилл Игнатьев",
                    "Кирилл Игнатьев",
                    "Кирилл",
                    "Игнатьев",
                    "ignatev@xabber.com",
                    R.color.green_500,
                    R.drawable.wolf,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Татьяна Морозова",
                "Татьяна Морозова",
                "Татьяна Морозова",
                "опоздаю на 10 мин.",
                System.currentTimeMillis(),
                MessageSendingState.Read,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                7.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.cyan_500,
                R.drawable.goodboy,
                ContactDto(
                    "Татьяна Морозова",
                    "Татьяна Морозова",
                    "Татьяна Морозова",
                    "Татьяна",
                    "Морозова",
                    "moroz92@xmpp.ru",
                    R.color.cyan_500,
                    R.drawable.goodboy,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Сергей Потапов",
                "Сергей Потапов",
                "Сергей Потапов",
                "Изменения в платежной системе",
                System.currentTimeMillis(),
                MessageSendingState.Deliver,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.blue_500,
                R.drawable.sea,
                ContactDto(
                    "1",
                    "Сергей Потапов",
                    "Сергей Потапов",
                    "Сергей",
                    "Потапов",
                    "sery@xabber.com",
                    R.color.blue_500,
                    R.drawable.sea,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Виктор Петров",
                "Виктор Петров",
                "Виктор Петров",
                "я же тебе ключ не отдал. давай завтра пересечемся в районе теплотеха",
                System.currentTimeMillis() + 1,
                MessageSendingState.Error,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                "1",
                0,
                R.color.deep_orange_500,
                R.drawable.baby,
                ContactDto(
                    "Виктор Петров",
                    "Виктор Петров",
                    "Виктор Петров",
                    "Виктор",
                    "Петров",
                    "petrov@xabber.com",
                    R.color.deep_orange_500,
                    R.drawable.baby,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Диляра Гизатуллина",
                "Диляра Гизатуллина",
                "Диляра Гизатуллина",
                "я тебе скину сейчас",
                System.currentTimeMillis(),
                MessageSendingState.Read,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                7.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.lime_500,
                R.drawable.girl,
                ContactDto(
                    "Диляра Гизатуллина",
                    "Диляра Гизатуллина",
                    "Диляра Гизатуллина",
                    "Диляра",
                    "Гизатуллина",
                    "dilya92@xmpp.ru",
                    R.color.lime_500,
                    R.drawable.girl,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Костя Кривоногов",
                "Костя Кривоногов",
                "Костя Кривоногов",
                "С днем рождения! Желаю тебе всего хорошего",
                System.currentTimeMillis(),
                MessageSendingState.Sending,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.indigo_500,
                R.drawable.litter,
                ContactDto(
                    "1",
                    "Костя Кривоногов",
                    "Костя Кривоногов",
                    "Костя",
                    "Кривоногов",
                    "kos@xabber.com",
                    R.color.indigo_500,
                    R.drawable.litter,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Полина Сорокина",
                "Полина Сорокина",
                "Полиночка",
                "собрание будет в 5 ч, я не успею на него",
                System.currentTimeMillis(),
                MessageSendingState.Deliver,
                true,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.purple_500,
                R.drawable.free,
                ContactDto(
                    "2",
                    "Полина Сорокина",
                    "Полина Сорокина",
                    "Полина",
                    "Сорокина",
                    "polly@xmpp.ru",
                    R.color.purple_500,
                    R.drawable.free,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Тимофей Решетников",
                "Тимофей Решетников",
                "Тимофей Решетников",
                "ахаха)))",
                System.currentTimeMillis() + 1,
                MessageSendingState.Deliver,
                false,
                true,
                null,
                false,
                false,
                false,
                0.0,
                0.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                "1",
                0,
                R.color.yellow_700,
                R.drawable.butterfly,
                ContactDto(
                    "Тимофей Решетников",
                    "Тимофей Решетников",
                    "Тимофей Решетников",
                    "Тимофей",
                    "Решетников",
                    "annasemenova@xabber.com",
                    R.color.yellow_500,
                    R.drawable.butterfly,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        list.add(
            ChatListDto(
                "1",
                "Инна Данилко",
                "Инна Данилко",
                "Инна Данилко",
                "передавай привет",
                System.currentTimeMillis(),
                MessageSendingState.NotSended,
                false,
                true,
                "скоро буду",
                false,
                false,
                false,
                0.0,
                7.0,
                ResourceStatus.Chat,
                RosterItemEntity.Contact,
                null,
                0,
                R.color.red_500,
                R.drawable.angel,
                ContactDto(
                    "Инна Данилко",
                    "Инна Данилко",
                    "Инна Данилко",
                    "Инна",
                    "Данилко",
                    "innochka@xmpp.ru",
                    R.color.red_500,
                    R.drawable.angel,
                    null, null, ResourceStatus.Online, RosterItemEntity.Contact
                )
            )
        )
        _chatList.value = list

//        val chatList = realm
//            .query<LastChatsStorageItem>()
//            .find()
//        _chatList.value = chatList.map { T ->
//            ChatListDto(
//                T.primary,
//                T.owner,
//                T.jid,
//                "",
//                T.lastMessage!!.body,
//                T.messageDate,
//                MessageSendingState.None,
//                T.isArchived,
//                T.isSynced,
//                T.draftMessage,
//                false, // hasAttachment
//                false, // isSystemMessage
//                false, //isMentioned
//                T.muteExpired,
//                T.pinnedPosition, // почему дабл?
//                ResourceStatus.Offline,
//                RosterItemEntity.Contact,
//                T.unread.toString()
//
//
//            )
//        }
//        realm.close()
    }


    fun movieChatToArchive(id: String) {
        //    chatRepository.movieChatToArchive(id)
        //     chat.value = chatRepository.getChatList()
//        for (i in 0 until chat.value!!.size) {
//            if (chat.value!![i].id == id) {
//                val archivedChat = chat.value!![i].copy(isArchived = !chat.value!![i].isArchived)
        //    chat.value!!.re(i)
        //    chat.value!!.add(archivedChat)
    }


    fun deleteChat(id: String) {
        //  chatRepository.deleteChat(id)
        //     chat.value = chatRepository.getChatList()
    }

    fun pinChat(id: String) {
        //    chatRepository.pinChat(id)


//        for (i in 0 until chat.value!!.size) {
//            if (chat.value!![i].id == id) {
//                val pinnedChat = chat.value!![i].copy(isPinned = true)
        //  chat.value!!.removeAt(i)
        // chat.value!!.add(pinnedChat)
        //    }
        // }

    }

    fun unPinChat(id: String) {
//        for (i in 0 until chat.value!!.size) {
//            if (chat.value!![i].id == id) {
//                val pinnedChat = chat.value!![i].copy(isPinned = false)
//         //       chat.value!!.removeAt(i)
//           //     chat.value!!.add(pinnedChat)
//            }
//        }
    }

    fun setMute() {

    }
}
