package com.xabber.presentation.application.fragments.contacts

import androidx.annotation.ColorRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.model.dto.ChatListDto
import com.xabber.model.dto.ContactDto
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.model.xmpp.roster.RosterStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query

class ContactAccountViewModel: ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val _contactAccount = MutableLiveData<ContactDto>()
    val contactAccount: LiveData<ContactDto> = _contactAccount


    fun getJid(id: String): String{
        val contact = realm.query(RosterStorageItem::class, "primary = '$id").first().find()
        return contact?.jid ?: ""
    }

    fun getContact(id: String): ContactDto {
        val contact = realm.query(RosterStorageItem::class, "primary = '$id").first().find()
      val contactDto = ContactDto(
         primary= contact!!.primary,
          owner= contact.owner,
          jid= contact.jid,
          nickName = contact.nickname,
          customNickName= contact.customNickname,
          color= R.color.blue_500,
          avatar= R.drawable.img,
          isHide= contact.isHidden,
      entity= RosterItemEntity.Contact,
      status= ResourceStatus.Chat,
      group= null)
_contactAccount.value =contactDto
        return contactDto
    }


    fun initListenerContactAccount(id: String) {

    }

    fun getChatId(): String {
realm.query<LastChatsStorageItem>()
    }
}