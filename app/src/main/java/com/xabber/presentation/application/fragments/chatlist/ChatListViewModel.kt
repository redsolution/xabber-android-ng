package com.xabber.presentation.application.fragments.chatlist

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.account.AccountManager
import com.xabber.data_base.dao.LastChatStorageItemDao
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.ChatListDto
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
class ChatListViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val lastChatDao = LastChatStorageItemDao(realm)
    private val accountStorageItemDao = AccountManager
    private var job: Job? = null
    private val _chats = MutableLiveData<ArrayList<ChatListDto>>()
    val chats: LiveData<ArrayList<ChatListDto>> = _chats
    private var chatListDto = ArrayList<ChatListDto>()
    private val _selectedChatId = MutableLiveData<String?>()
    val selectedChatId: LiveData<String?> = _selectedChatId
    private val _showUnreadOnly = MutableLiveData<Boolean>()
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly

    init {
        _showUnreadOnly.value = false
        getChatList()
        CoroutineScope(Dispatchers.IO).launch {
            checkLastChats()
        }
    }

    private suspend fun checkLastChats() {
        withContext(Dispatchers.IO) {
            realm.write {
                val chats = query<LastChatsStorageItem>(
                    "conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
                ).find()

            }
        }
    }

    fun isSavedHas(jid: String): Boolean {
        var exists = false
        realm.writeBlocking {
            exists = this.query(
                LastChatsStorageItem::class,
                "jid = $0 AND conversationType_ = $1",
                jid,
                ConversationType.Favorites.rawValue
            ).first().find() != null
        }
        return exists
    }

    fun getAccountsAmount(): Int {
        var amount = 0
        realm.writeBlocking {
            amount = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find().size
        }
        return amount
    }

    fun setShowUnreadOnly(show: Boolean) {
        _showUnreadOnly.value = show
        initDataListener()
        getChatList()
    }

    fun selectChat(chatId: String) {
        _selectedChatId.value = chatId
    }

    fun initDataListener() {
        job?.cancel()
        val accounts = getEnableAccountList()
        val query = if (showUnreadOnly.value == true) {
            "owner IN {${accounts.joinToString { "'$it'" }}} AND isArchived = false AND unread > 0 AND conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
        } else {
            "owner IN {${accounts.joinToString { "'$it'" }}} AND isArchived = false AND conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
        }
        job = viewModelScope.launch(Dispatchers.IO) {
            val request = realm.query(LastChatsStorageItem::class, query)
                .sort("pinnedPosition" to Sort.DESCENDING, "messageDate" to Sort.DESCENDING)
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        val dataSource = ArrayList<ChatListDto>()
                        dataSource.addAll(changes.list.map { it.toChatListDto() })
                        val accountItems = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
                        val accountDtoList = accountItems.map { it.toAccountDto() }
                        val accountHashMap = HashMap<String, AccountDto>()
                        accountDtoList.forEach { accountHashMap[it.id] = it }
                        dataSource.forEach { chatListDto ->
                            val ac = accountHashMap[chatListDto.owner]
                            if (ac != null) {
                                chatListDto.colorKey = ac.colorKey
                            }
                        }
                        chatListDto = dataSource
                        launch(Dispatchers.Main) {
                            _chats.postValue(chatListDto)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun getEnableAccountList(): RealmSet<String> {
        val enabledAccountsIds = realmSetOf<String>()
        realm.writeBlocking {
            val enabledAccounts = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
            enabledAccounts.forEach { account -> enabledAccountsIds.add(account.primary) }
        }
        return enabledAccountsIds
    }

    fun initAccountDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class)
            request.asFlow().collect { changes: ResultsChange<com.xabber.data_base.models.account.AccountStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        getChatList()
                    }
                    else -> {}
                }
            }
        }
    }

    fun getChatList() {
        val accounts = getEnableAccountList()
        val query = if (showUnreadOnly.value == true) {
            "owner IN {${accounts.joinToString { "'$it'" }}} AND isArchived = false AND unread > 0 AND conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
        } else {
            "owner IN {${accounts.joinToString { "'$it'" }}} AND isArchived = false AND conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
        }
        viewModelScope.launch(Dispatchers.IO) {
            val realmList = realm.query(LastChatsStorageItem::class, query)
                .sort("pinnedPosition" to Sort.DESCENDING, "messageDate" to Sort.DESCENDING).find()
            val dataSource = ArrayList<ChatListDto>()
            dataSource.addAll(realmList.map { it.toChatListDto() })
            val accountItems = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
            val accountDtoList = accountItems.map { it.toAccountDto() }
            val accountHashMap = HashMap<String, AccountDto>()
            accountDtoList.forEach { accountHashMap[it.id] = it }
            dataSource.forEach { chatListDto ->
                val ac = accountHashMap[chatListDto.owner]
                if (ac != null) {
                    chatListDto.colorKey = ac.colorKey
                }
            }
            chatListDto = dataSource
            withContext(Dispatchers.Main) {
                _chats.value = chatListDto
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

    fun setArchived(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            lastChatDao.setArchived(id)
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            lastChatDao.deleteItem(id)
        }
    }

    fun setMute(id: String, muteExpired: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            lastChatDao.setMuteExpired(id, muteExpired)
        }
    }

    fun markAllChatsAsUnread() {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val items = this.query(
                    LastChatsStorageItem::class,
                    "conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
                ).find()
                items.forEach { findLatest(it)?.unread = 0 }
                val messages = this.query(
                    MessageStorageItem::class,
                    "conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
                ).find()
                messages.forEach { findLatest(it)?.isRead = true }
            }
            checkLastChats()
        }
    }

    fun forwardMessage(id: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) {
                    val newMessageTimestamp = System.currentTimeMillis()
                    // Check for the latest message in the chat
                    val latestMessage = query<MessageStorageItem>(
                        "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
                        item.owner, item.jid, item.conversationType_
                    ).sort("sentDate", Sort.DESCENDING).first().find()

                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = MessageStorageItem.genPrimary("message_${newMessageTimestamp}", item.owner)
                        owner = item.owner
                        opponent = item.jid
                        body = text
                        date = newMessageTimestamp
                        sentDate = newMessageTimestamp
                        editDate = 0
                        outgoing = true
                        conversationType_ = ConversationType.Regular.rawValue
                        isRead = true // Forwarded messages are typically marked as read
                    })

                    findLatest(item)?.apply {
                        // Update only if the new message has a higher timestamp
                        if (latestMessage == null || newMessageTimestamp > latestMessage.sentDate) {
                            lastMessage = message
                            lastMessageId = message.messageId
                            messageDate = newMessageTimestamp
                            unread = 0
                        }
                    }
                }
            }
            checkLastChats()
        }
    }

    fun chatIsEmpty(): Boolean {
        var result = true
        realm.writeBlocking {
            val lastChats = this.query(
                LastChatsStorageItem::class,
                "conversationType_ IN {'${ConversationType.Regular.rawValue}', '${ConversationType.Group.rawValue}', '${ConversationType.Channel.rawValue}', '${ConversationType.Favorites.rawValue}'}"
            ).find()
            if (lastChats.isNotEmpty()) result = false
        }
        return result
    }

    private fun getMainAccountPrimary(): String? {
        val primary = accountStorageItemDao.getMainAccountPrimary()
        return primary
    }



    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        realm.close()
    }
}