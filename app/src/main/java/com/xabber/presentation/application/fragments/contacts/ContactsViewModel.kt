package com.xabber.presentation.application.fragments.contacts

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.ChatListDto
import com.xabber.models.dto.ContactDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.models.xmpp.roster.RosterStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel : ViewModel() {
    var contacts = ArrayList<ContactDto>()

    val realm = Realm.open(defaultRealmConfig())
    private val _contactList = MutableLiveData<ArrayList<ContactDto>>()
    val contactList: LiveData<ArrayList<ContactDto>> = _contactList


    fun initDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(RosterStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<RosterStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val dataSource = ArrayList<ContactDto>()
                        dataSource.addAll(changes.list.map { T ->
                            ContactDto(
                                primary = T.primary,
                                owner = T.owner,
                                nickName = T.nickname,
                                jid = T.jid,
                                customNickName = T.customNickname,
                                color = T.colorR,
                                status = ResourceStatus.Chat,
                                entity = RosterItemEntity.Contact,
                                isDeleted = T.isDeleted,
                                group = "",
                                avatar = T.avatarR
                            )
                        })

                        contacts = dataSource
                        contacts.sort()
                        launch(Dispatchers.Main) {
                            _contactList.postValue(contacts)
                        }
                    }
                    else -> {}
                }
            }
        }
    }


    fun getChatList() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    RosterStorageItem::class
                ).find()
            val dataSource = ArrayList<ContactDto>()
            dataSource.addAll(realmList.map { T ->
                ContactDto(
                    primary = T.primary,
                    owner = T.owner,
                    nickName = T.nickname,
                    jid = T.jid,
                    customNickName = T.customNickname,
                    color = T.colorR,
                    status = ResourceStatus.Chat,
                    entity = RosterItemEntity.Contact,
                    isDeleted = T.isDeleted,
                    group = "",
                    avatar = T.avatarR
                )
            })
            contacts = dataSource
            contacts.sort()
            withContext(Dispatchers.Main) {
                _contactList.value = contacts
            }
        }


    }

    fun getChatId(owner: String, opponent: String): String? {
        var id: String? = null
        val item = realm.query(LastChatsStorageItem::class, "opponentJid = '$opponent'").first().find()
        return item?.primary ?: null
        return id
    }

    fun getOwner(): String? {
        val item = realm.query(AccountStorageItem::class).first().find()
        val nick =  item?.nickname
        Log.d("ooo", "$nick")
        return nick
    }
}