package com.xabber.presentation.application.fragments.contacts

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ChatDto
import com.xabber.data.dto.ContactDto
import com.xabber.data.dto.ContactKind
import com.xabber.presentation.application.fragments.chat.ResourceStatus
import com.xabber.presentation.application.fragments.chat.RosterItemEntity

class ContactsViewModel : ViewModel() {
  var contacts = MutableLiveData<ArrayList<ContactDto>>()

    init {
        contacts.value = java.util.ArrayList<ContactDto>()
      contacts.value!!.addAll(mutableListOf(
          ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"), ContactDto(ContactKind.CONTACT,
              "Олег Олегов", "Олег Олегов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"), ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),ContactDto(ContactKind.CONTACT,
              "Иван Иванов", "Иван Иванов", "группа 1", "Заголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT, false, "groupPrimary"),))
    }
}