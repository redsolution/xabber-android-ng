package com.xabber.presentation.application.fragments.contacts

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ContactDto
import com.xabber.data.dto.ResourceStatus
import com.xabber.data.dto.RosterItemEntity

class ContactsViewModel : ViewModel() {
  var contacts = MutableLiveData<ArrayList<ContactDto>>()

    init {
        contacts.value = java.util.ArrayList<ContactDto>()
      contacts.value!!.addAll(mutableListOf(
          ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Иван Иванов", "Подзаголовок",
              ResourceStatus.XA, RosterItemEntity.BOT), ContactDto(
              "Олег Олегов", "Олег Олегов", "группа 1", "Подзаголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Арина Артемонова", "Подзаголовок",
              ResourceStatus.OFFLINE, RosterItemEntity.CONTACT),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Ирина Меньшикова", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.GROUP),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Карина Румянцева", "Подзаголовок",
              ResourceStatus.XA, RosterItemEntity.SERVER), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Анатолий Медведев", "Подзаголовок",
              ResourceStatus.DND, RosterItemEntity.PRIVATE_CHAT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Виктор Пелевин", "Подзаголовок",
              ResourceStatus.OFFLINE, RosterItemEntity.INCOGNITO_GROUP), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Мария Антонова", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Тимофей Тигров", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Татьяна Пашнина", "Подзаголовок",
              ResourceStatus.AWAY, RosterItemEntity.BOT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Светлана Чернова", "Подзаголовок",
              ResourceStatus.CHAT, RosterItemEntity.GROUP), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Алексей Купатов", "Подзаголовок",
              ResourceStatus.AWAY, RosterItemEntity.INCOGNITO_GROUP), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Анна Левадная", "Подзаголовок",
              ResourceStatus.DND, RosterItemEntity.GROUP), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Сергей Бутрий", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Кирилл Розов", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Алина Рыбина", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT)))
    }
}