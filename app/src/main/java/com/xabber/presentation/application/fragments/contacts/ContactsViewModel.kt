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
import com.xabber.data_base.models.sync.ConversationType
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
                        updateContactList(filterNonGroupChats(changes.list))
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
            updateContactList(filterNonGroupChats(realmList))
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
            updateContactList(filterNonGroupChats(realmList))
        }
    }

    fun showAllContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(RosterStorageItem::class, "isDeleted = false")
                .sort("customNickname" to Sort.ASCENDING, "nickname" to Sort.ASCENDING, "jid" to Sort.ASCENDING)
                .find()
            updateContactList(filterNonGroupChats(realmList))
        }
    }

    private fun filterNonGroupChats(realmList: List<RosterStorageItem>): List<RosterStorageItem> {
        // Filter out RosterStorageItem entries that correspond to group chats
        return realmList.filter { rosterItem ->
            val isGroupChat = realm.query(
                LastChatsStorageItem::class,
                "jid = $0 AND owner = $1 AND conversationType_ = $2",
                rosterItem.jid, rosterItem.owner, "https://xabber.com/protocol/groups"
            ).first().find() != null
            !isGroupChat
        }.also {
            Log.d("ContactsViewModel", "Filtered roster items: total=${realmList.size}, non-group=${it.size}")
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
                    entity = RosterItemEntity.CONTACT, // Default to CONTACT since RosterStorageItem lacks entity_
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
        // Check if the opponent JID corresponds to a group chat
        val isGroupChat = realm.query(
            LastChatsStorageItem::class,
            "jid = $0 AND owner = $1 AND conversationType_ = $2",
            opponent, owner, "https://xabber.com/protocol/groups"
        ).first().find() != null

        if (isGroupChat) {
            Log.w("ContactsViewModel", "Skipping getChatId for group chat: opponent=$opponent, owner=$owner")
            return null // Group chats should not be opened from contacts list
        }

        val item = realm.query(LastChatsStorageItem::class, "jid = '$opponent' AND owner = '$owner'").first().find()
        val chatId = item?.primary ?: LastChatsStorageItem.genPrimary(opponent, owner, ConversationType.Regular)
        Log.d("ContactsViewModel", "Generated chatId: opponent=$opponent, owner=$owner, chatId=$chatId")
        return chatId
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