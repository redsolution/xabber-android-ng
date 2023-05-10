package com.xabber.presentation.application.fragments.chatlist.archive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.dao.LastChatStorageItemDao
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val lastChatDao = LastChatStorageItemDao(realm)
    private val _chatList = MutableLiveData<List<ChatListDto>>()
    val chatList: LiveData<List<ChatListDto>> = _chatList
    val t = System.currentTimeMillis() + 99999999999999999

    private val anchorChatList = ChatListDto(
        "",
        "",
        "",
        "",
        lastMessageState = MessageSendingState.None,
        drawableId = R.drawable.angel,
        entity = RosterItemEntity.Contact,
        status = ResourceStatus.Offline,
        isHide = true
    )

    init {
        getChat()
    }

    fun getAccountsAmount(): Int {
        var amount = 0
        realm.writeBlocking {
            amount = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled == true").find().size
        }
        return amount
    }

    private fun getEnableAccountList(): RealmSet<String> {
        val enabledAccountsIdes = realmSetOf("")
        realm.writeBlocking {
            val enabledAccounts = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
            enabledAccounts.forEach { enabledAccountsIdes.add(it.primary) }
        }
        return enabledAccountsIdes
    }

    fun initListener() {
        viewModelScope.launch {
            val accounts = getEnableAccountList()
            val lastChatsFlow =
                realm.query(
                    LastChatsStorageItem::class,
                    "owner IN {${accounts.joinToString { "'$it'" }}} && isArchived == true"
                )
                    .asFlow()
            lastChatsFlow.collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val listDto = ArrayList<ChatListDto>()
                        listDto.addAll(changes.list.map { T ->
                            T.toChatListDto()
                        })
                        val accountItems =
                            realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
                        val accountDtoList = accountItems.map { T -> T.toAccountDto() }
                        val accountHashMap = HashMap<String, AccountDto>()
                        accountDtoList.forEach { accountHashMap[it.id] = it }
                        listDto.filter { T -> accountHashMap.contains(T.owner) }
                        listDto.forEach { chatListDto ->
                            val ac = accountHashMap[chatListDto.owner]
                            if (ac != null) chatListDto.colorKey = ac.colorKey
                        }
                        listDto.sort()
                        if (listDto.size > 0) {
                            listDto.add(
                                0,
                                anchorChatList
                            )
                        }
                        withContext(Dispatchers.Main) {
                            _chatList.value = listDto
                        }
                    }
                    else -> {}
                }
            }
        }
    }


    fun pinChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            lastChatDao.setPinnedPosition(id, System.currentTimeMillis())
        }
    }

    fun unPinChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            lastChatDao.setPinnedPosition(id, -1)
        }
    }

    fun getChat() {
        val accounts = getEnableAccountList()
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    LastChatsStorageItem::class,
                    "owner IN {${accounts.joinToString { "'$it'" }}} && isArchived == true"
                )
                    .find()
            val listDto = ArrayList<ChatListDto>()
            listDto.addAll(realmList.map { T ->
                T.toChatListDto()
            })
            val accountItems = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
            val accountDtoList = accountItems.map { T -> T.toAccountDto() }
            val accountHashMap = HashMap<String, AccountDto>()
            accountDtoList.forEach { accountHashMap[it.id] = it }
            listDto.filter { T -> accountHashMap.contains(T.owner) }
            listDto.forEach { chatListDto ->
                val ac = accountHashMap[chatListDto.owner]
                if (ac != null) chatListDto.colorKey = ac.colorKey
            }
            listDto.sort()
            if (listDto.size > 0) {
                listDto.add(
                    0,
                    anchorChatList
                )
            }
            withContext(Dispatchers.Main) { _chatList.value = listDto }
        }
    }

    fun movieChatToArchive(id: String, isArchived: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.isArchived = isArchived
            }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val deletedChat =
                    realm.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (deletedChat != null) findLatest(deletedChat)?.let { delete(it) }
            }
        }
    }

    fun setMute(id: String, muteExpired: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                item?.muteExpired = muteExpired
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

    fun getColor(): String? {
        var color: String? = null
        realm.writeBlocking {
            val account = this.query(com.xabber.data_base.models.account.AccountStorageItem::class).first().find()
            color = account?.colorKey
        }
        return color
    }

}
