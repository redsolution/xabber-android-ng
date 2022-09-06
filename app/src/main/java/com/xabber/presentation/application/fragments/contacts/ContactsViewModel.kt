package com.xabber.presentation.application.fragments.contacts

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.R
import com.xabber.model.dto.ContactDto
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity

class ContactsViewModel : ViewModel() {
    var contacts = MutableLiveData<ArrayList<ContactDto>>()

    init {
        contacts.value = java.util.ArrayList<ContactDto>()
        contacts.value!!.addAll(
            mutableListOf(
                ContactDto(
                    "1",
                    "Иван Иванов",
                    "Иван Иванов",
                    "Иван",
                    "Иванов",
                    "ivanov@xmpp.ru",
                    R.color.red_500,
                    R.drawable.butterfly,
                    "группа 1",
                    null,
                    ResourceStatus.Online, RosterItemEntity.Groupchat
                ),
                ContactDto(
                    "1",
                    "Иван Сергеев",
                    "Иван Сергеев",
                    "Иван",
                    "Сергеев",
                    "ivan@xabber.com",
                    R.color.green_500,
                    R.drawable.rayan,
                    null, null,  ResourceStatus.Away, RosterItemEntity.Contact
                ),
                ContactDto(
                    "2",
                    "Ирина Иванова",
                    "Ирина Иванова",
                    "Ирина",
                    "Иванова",
                    "ivanova@xmpp.ru",
                    R.color.purple_500,
                    R.drawable.flower,
                    null, null, ResourceStatus.Chat, RosterItemEntity.Contact
                ),
                ContactDto(
                    "Анна Семенова",
                    "Анна Семенова",
                    "Анна Семенова",
                    "Анна",
                    "Семенова",
                    "annasemenova@xabber.com",
                    R.color.yellow_500,
                    R.drawable.kitty,
                    null, null, ResourceStatus.Dnd, RosterItemEntity.Bot
                ),
                ContactDto(
                    "Олег Панин",
                    "Олег Панин",
                    "Олег Панин",
                    "Олег",
                    "Панин",
                    "oleg92@xmpp.ru",
                    R.color.red_500,
                    R.drawable.man,
                    null, null, ResourceStatus.Xa, RosterItemEntity.IncognitoChat
                ),
                ContactDto(
                    "1",
                    "Геннадий Белов",
                    "Геннадий Белов",
                    "Геннадий",
                    "Белов",
                    "belovn@xabber.com",
                    com.canhub.cropper.R.color.material_deep_teal_500,
                    R.drawable.car,
                    null, null, ResourceStatus.Offline, RosterItemEntity.Server
                ),
                ContactDto(
                    "2",
                    "Кристина Стаханова",
                    "Кристина Стаханова",
                    "Кристина",
                    "Стаханова",
                    "kriss@xmpp.ru",
                    R.color.yellow_700,
                    R.drawable.woman,
                    null, null, ResourceStatus.Offline, RosterItemEntity.EncryptedChat
                ),
                ContactDto(
                    "Кирилл Игнатьев",
                    "Кирилл Игнатьев",
                    "Кирилл Игнатьев",
                    "Кирилл",
                    "Игнатьев",
                    "ignatev@xabber.com",
                    R.color.green_500,
                    R.drawable.wolf,
                    null, null, ResourceStatus.Online, RosterItemEntity.Groupchat
                ),
                ContactDto(
                    "Татьяна Морозова",
                    "Татьяна Морозова",
                    "Татьяна Морозова",
                    "Татьяна",
                    "Морозова",
                    "moroz92@xmpp.ru",
                    R.color.cyan_500,
                    R.drawable.goodboy,
                    null, null, ResourceStatus.Online, RosterItemEntity.Issue
                ),
                ContactDto(
                    "1",
                    "Сергей Потапов",
                    "Сергей Потапов",
                    "Сергей",
                    "Потапов",
                    "sery@xabber.com",
                    R.color.blue_500,
                    R.drawable.sea,
                    null, null, ResourceStatus.Online, RosterItemEntity.PrivateChat
                ),
                ContactDto(
                    "Виктор Петров",
                    "Виктор Петров",
                    "Виктор Петров",
                    "Виктор",
                    "Петров",
                    "petrov@xabber.com",
                    R.color.deep_orange_500,
                    R.drawable.baby,
                    null, null, ResourceStatus.Chat, RosterItemEntity.Contact
                ),
                ContactDto(
                    "Диляра Гизатуллина",
                    "Диляра Гизатуллина",
                    "Диляра Гизатуллина",
                    "Диляра",
                    "Гизатуллина",
                    "dilya92@xmpp.ru",
                    R.color.lime_500,
                    R.drawable.girl,
                    null, null, ResourceStatus.Online, RosterItemEntity.Bot
                ),
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
                ),
                ContactDto(
                    "2",
                    "Полина Сорокина",
                    "Полина Сорокина",
                    "Полина",
                    "Сорокина",
                    "polly@xmpp.ru",
                    R.color.purple_500,
                    R.drawable.free,
                    null, null, ResourceStatus.Dnd, RosterItemEntity.EncryptedChat
                )
            )
        )

    }
}