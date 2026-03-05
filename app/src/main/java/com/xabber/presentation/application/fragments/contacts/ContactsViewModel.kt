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
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
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
                        buildContactList(changes.list)
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
            buildContactList(realmList)
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
            buildContactList(realmList)
        }
    }

    fun showAllContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(RosterStorageItem::class, "isDeleted = false")
                .sort("customNickname" to Sort.ASCENDING, "nickname" to Sort.ASCENDING, "jid" to Sort.ASCENDING)
                .find()
            buildContactList(realmList)
        }
    }

    /**
     * Build contact list with batch queries instead of per-item queries.
     * Loads all group chat JIDs and all presence resources in two queries,
     * then maps roster items to DTOs using in-memory lookups.
     */
    private suspend fun buildContactList(realmList: List<RosterStorageItem>) {
        // 1. Batch: load all group chat JIDs in one query
        val groupChatJids = realm.query(
            LastChatsStorageItem::class,
            "conversationType_ = $0",
            "https://xabber.com/protocol/groups"
        ).find().map { "${it.owner}|${it.jid}" }.toHashSet()

        // 2. Batch: load all presence resources in one query, group by owner|jid
        val allResources = realm.query(ResourceStorageItem::class).find()
        val bestPresence = mutableMapOf<String, ResourceStatus>()
        allResources.groupBy { "${it.owner}|${it.jid}" }
            .forEach { (key, resources) ->
                val best = resources.maxWithOrNull(
                    compareBy<ResourceStorageItem> { it.status.rank() }
                        .thenByDescending { it.priority }
                )
                if (best != null) {
                    bestPresence[key] = best.status
                }
            }

        // 3. Filter out group chats and map to DTOs — no per-item Realm queries
        val dataSource = ArrayList<ContactDto>()
        for (item in realmList) {
            val key = "${item.owner}|${item.jid}"
            val isGroup = key in groupChatJids
            if (isGroup) continue  // Skip group chats from contact list

            dataSource.add(
                ContactDto(
                    primary = item.primary,
                    owner = item.owner,
                    nickName = item.nickname,
                    jid = item.jid,
                    customNickName = item.customNickname,
                    color = item.colorKey,
                    status = bestPresence[key] ?: ResourceStatus.OFFLINE,
                    entity = RosterItemEntity.CONTACT,
                    isDeleted = item.isDeleted,
                    group = item.groups.firstOrNull() ?: "",
                    avatar = item.avatarR,
                    isGroupChat = false
                )
            )
        }

        contacts = dataSource
        contacts.sort()
        Log.d("ContactsViewModel", "Built contact list: total=${realmList.size}, non-group=${contacts.size}")
        withContext(Dispatchers.Main) {
            _contactList.value = contacts
        }
    }

    private fun ResourceStatus.rank(): Int = when (this) {
        ResourceStatus.CHAT    -> 5
        ResourceStatus.ONLINE  -> 4
        ResourceStatus.AWAY    -> 3
        ResourceStatus.DND     -> 2
        ResourceStatus.XA      -> 1
        ResourceStatus.OFFLINE -> 0
    }

    fun getChatId(owner: String, opponent: String): String? {
        val chat = realm.query(
            LastChatsStorageItem::class,
            "jid = $0 AND owner = $1",
            opponent, owner
        ).first().find()

        // Skip group chats
        if (chat?.conversationType_ == "https://xabber.com/protocol/groups") {
            Log.w("ContactsViewModel", "Skipping getChatId for group chat: opponent=$opponent, owner=$owner")
            return null
        }

        val chatId = chat?.primary ?: LastChatsStorageItem.genPrimary(opponent, owner, ConversationType.Regular)
        Log.d("ContactsViewModel", "Generated chatId: opponent=$opponent, owner=$owner, chatId=$chatId")
        return chatId
    }

    fun getOwner(): String? {
        val item = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class).first().find()
        return item?.username
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }
}
