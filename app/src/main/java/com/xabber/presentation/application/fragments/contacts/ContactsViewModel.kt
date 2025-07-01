package com.xabber.presentation.application.fragments.contacts

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.ContactDto
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.roster.RosterStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel : ViewModel() {
    private val realm = Realm.open(defaultRealmConfig())
    private val _contactList = MutableLiveData<ArrayList<ContactDto>>()
    val contactList: LiveData<ArrayList<ContactDto>> = _contactList
    private var contacts = ArrayList<ContactDto>()

    fun initDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request = realm.query(RosterStorageItem::class, "isDeleted = false")
                .sort("customNickname" to Sort.ASCENDING, "nickname" to Sort.ASCENDING, "jid" to Sort.ASCENDING)
            request.asFlow().collect { changes: ResultsChange<RosterStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        updateContactList(changes.list)
                    }
                    else -> {}
                }
            }
        }
    }

    fun getChatList() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(RosterStorageItem::class, "isDeleted = false")
                .sort("customNickname" to Sort.ASCENDING, "nickname" to Sort.ASCENDING, "jid" to Sort.ASCENDING)
                .find()
            updateContactList(realmList)
        }
    }

    fun filterContactsByGroup(groupName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(
                RosterStorageItem::class,
                "groups ==[c] $0 AND isDeleted = false",
                groupName
            ).sort("customNickname" to Sort.ASCENDING, "nickname" to Sort.ASCENDING, "jid" to Sort.ASCENDING)
                .find()
            updateContactList(realmList)
        }
    }

    fun showAllContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(RosterStorageItem::class, "isDeleted = false")
                .sort("customNickname" to Sort.ASCENDING, "nickname" to Sort.ASCENDING, "jid" to Sort.ASCENDING)
                .find()
            updateContactList(realmList)
        }
    }

    private fun updateContactList(realmList: List<RosterStorageItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataSource = ArrayList<ContactDto>()
            dataSource.addAll(realmList.map { T ->
                ContactDto(
                    primary = T.primary,
                    owner = T.owner,
                    nickName = T.nickname,
                    jid = T.jid,
                    customNickName = T.customNickname,
                    color = T.colorKey,
                    status = realm.query(
                        com.xabber.data_base.models.presences.ResourceStorageItem::class,
                        "jid = $0 AND owner = $1",
                        T.jid, T.owner
                    ).sort("timestamp" to Sort.DESCENDING, "priority" to Sort.DESCENDING)
                        .first().find()?.status?.let { ResourceStatus.valueOf(it.name) } ?: ResourceStatus.OFFLINE,
                    entity = RosterItemEntity.CONTACT,
                    isDeleted = T.isDeleted,
                    group = T.groups.firstOrNull() ?: "",
                    avatar = T.avatarR
                )
            })
            contacts = dataSource
            contacts.sort() // Apply ContactDto.compareTo
            withContext(Dispatchers.Main) {
                _contactList.value = contacts
            }
        }
    }

    fun getChatId(owner: String, opponent: String): String? {
        val item = realm.query(LastChatsStorageItem::class, "jid = '$opponent'").first().find()
        return item?.primary
    }

    fun getOwner(): String? {
        val item = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class).first().find()
        val nick = item?.username
        Log.d("ooo", "$nick")
        return nick
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }
}