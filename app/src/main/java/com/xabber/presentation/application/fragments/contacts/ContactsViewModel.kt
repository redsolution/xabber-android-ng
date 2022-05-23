package com.xabber.presentation.application.fragments.contacts

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ContactDto
import com.xabber.xmpp.presences.ResourceStatus
import com.xabber.xmpp.presences.RosterItemEntity

class ContactsViewModel : ViewModel() {
  var contacts = MutableLiveData<ArrayList<ContactDto>>()

    init {
        contacts.value = java.util.ArrayList<ContactDto>()
      contacts.value!!.addAll(mutableListOf(
          ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Иван Иванов", "Подзаголовок",
              ResourceStatus.Xa, RosterItemEntity.Server), ContactDto(
              "Олег Олегов", "Олег Олегов", "группа 1", "Подзаголовок", "Подзаголовок",
              ResourceStatus.Online, RosterItemEntity.Contact), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Арина Артемонова", "Подзаголовок",
              ResourceStatus.Offline, RosterItemEntity.Contact),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Ирина Меньшикова", "Подзаголовок",
              ResourceStatus.Online, RosterItemEntity.Groupchat),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Карина Румянцева", "Подзаголовок",
              ResourceStatus.Xa, RosterItemEntity.Server)))
    }
}