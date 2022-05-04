package com.xabber.presentation.application.fragments.contacts

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.data.dto.ContactDto
import com.xabber.data.dto.ContactKind
import com.xabber.data.dto.ResourceStatus
import com.xabber.presentation.application.fragments.chat.RosterItemEntity

class ContactsViewModel : ViewModel() {
  var contacts = MutableLiveData<ArrayList<ContactDto>>()

    init {
        contacts.value = java.util.ArrayList<ContactDto>()
      contacts.value!!.addAll(mutableListOf(
          ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Иван Иванов", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Олег Олегов", "Олег Олегов", "группа 1", "Подзаголовок", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Арина Артемонова", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Ирина Меньшикова", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Карина Румянцева", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Анатолий Медведев", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Виктор Пелевин", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Мария Антонова", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Тимофей Тигров", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Татьяна Пашнина", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Светлана Чернова", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Алексей Купатов", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Анна Левадная", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT), ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Сергей Бутрий", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Кирилл Розов", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT),ContactDto(
              "Иван Иванов", "Иван Иванов", "группа 1", "Алина Рыбина", "Подзаголовок",
              ResourceStatus.ONLINE, RosterItemEntity.CONTACT)))
    }
}