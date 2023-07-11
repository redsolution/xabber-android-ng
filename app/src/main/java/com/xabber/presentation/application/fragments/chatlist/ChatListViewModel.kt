package com.xabber.presentation.application.fragments.chatlist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.dao.AccountStorageItemDao
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
import com.xabber.presentation.XabberApplication
import com.xabber.utils.toAccountDto
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val lastChatDao = LastChatStorageItemDao(realm)
    private val accountStorageItemDao = AccountStorageItemDao(realm)
    private var job: Job? = null
    private val _chats = MutableLiveData<ArrayList<ChatListDto>>()
    val chats: LiveData<ArrayList<ChatListDto>> = _chats
    private var chatListDto = ArrayList<ChatListDto>()
    var selectedChatId = ""

    private val _showUnreadOnly = MutableLiveData<Boolean>()
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly
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
        _showUnreadOnly.value = false
        getChatList()
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

    fun initDataListener() {
        job?.cancel()
        val accounts = getEnableAccountList()
        val query =
            if (showUnreadOnly.value!!) "owner IN {${accounts.joinToString { "'$it'" }}} && isArchived = false && unread > 0" else "owner IN {${accounts.joinToString { "'$it'" }}} && isArchived = false"
        job = viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(LastChatsStorageItem::class, query)
            request.asFlow().collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val dataSource = ArrayList<ChatListDto>()
                        dataSource.addAll(changes.list.map { T ->
                            T.toChatListDto()
                        })
                        val accountItems =
                            realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
                        val accountDtoList = accountItems.map { T -> T.toAccountDto() }
                        val accountHashMap = HashMap<String, AccountDto>()
                        accountDtoList.forEach { accountHashMap[it.id] = it }
                        dataSource.forEach { chatListDto ->
                            val ac = accountHashMap[chatListDto.owner]
                            if (ac != null) {
                                chatListDto.colorKey = ac.colorKey
                            }
                        }
                        dataSource.sort()
                        if (dataSource.size > 0) {
                            dataSource.add(
                                0,
                                anchorChatList
                            )
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
        val enabledAccountsIdes = realmSetOf("")
        realm.writeBlocking {
            val enabledAccounts = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
            enabledAccounts.forEach { enabledAccountsIdes.add(it.primary) }
        }
        return enabledAccountsIdes
    }


    fun initAccountDataListener() {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(com.xabber.data_base.models.account.AccountStorageItem::class)
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
        val query =
            if (showUnreadOnly.value!!) "owner IN {${accounts.joinToString { "'$it'" }}} && isArchived = false && unread > 0" else "owner IN {${accounts.joinToString { "'$it'" }}} && isArchived = false"
        viewModelScope.launch(Dispatchers.IO) {
            val realmList =
                realm.query(
                    LastChatsStorageItem::class,
                    query
                ).find()
            val dataSource = ArrayList<ChatListDto>()
            dataSource.addAll(realmList.map { T ->
                T.toChatListDto()
            })

            val accountItems = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
            val accountDtoList = accountItems.map { T -> T.toAccountDto() }
            val accountHashMap = HashMap<String, AccountDto>()
            accountDtoList.forEach { accountHashMap[it.id] = it }
            dataSource.forEach { chatListDto ->
                val ac = accountHashMap[chatListDto.owner]
                if (ac != null) {
                    chatListDto.colorKey = ac.colorKey
                }
            }
            dataSource.sort()
            if (dataSource.size > 0) {
                dataSource.add(
                    0,
                    anchorChatList
                )
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
                val items = this.query(LastChatsStorageItem::class).find()
                items.forEach {
                    it.unread = 0
                }
                val messages = this.query(MessageStorageItem::class).find()
                messages.forEach {
                    it.isRead = true
                }
            }
        }
    }

    fun forwardMessage(id: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item: LastChatsStorageItem? =
                    this.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) {
                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = "texttt + ${System.currentTimeMillis()}"
                        owner = item.owner
                        opponent = item.jid
                        body = text
                        date = System.currentTimeMillis()
                        sentDate = System.currentTimeMillis()
                        editDate = 0
                        outgoing = true
                        conversationType_ = ConversationType.Group.toString()
                    })
                    item.lastMessage = message
                    item.messageDate = message.date
                    item.unread = 0
                }
            }
        }
    }

    fun chatIsEmpty(): Boolean {
        var result = true
        realm.writeBlocking {
            val lastChats = this.query(LastChatsStorageItem::class).find()
            if (lastChats.size > 0) result = false
        }
        return result
    }

    fun insertContactAndChat(contactJid: String, customName: String) {
        val contactOwner = getMainAccountPrimary()
        if (contactOwner != null) {
            viewModelScope.launch(Dispatchers.IO) {
                realm.write {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = contactJid
                        muteExpired = -1
                        owner = contactOwner
                        jid = contactJid
                        rosterItem = copyToRealm(RosterStorageItem().apply {
                            primary = contactJid
                            owner = contactOwner
                            jid = contactJid
                            customNickname = customName
                        })
                    })
                }
            }
        }
    }

    private fun getMainAccountPrimary(): String? = accountStorageItemDao.getMainAccountPrimary()

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        realm.close()
    }

    fun addSomeChats() {
        val colors = listOf(
            XabberApplication.applicationContext().resources.getString(R.string.red), XabberApplication.applicationContext().resources.getString(R.string.orange),
            XabberApplication.applicationContext().resources.getString(R.string.amber),
            XabberApplication.applicationContext().resources.getString(R.string.lime),
            XabberApplication.applicationContext().resources.getString(R.string.light_green),
            XabberApplication.applicationContext().resources.getString(R.string.green),
            XabberApplication.applicationContext().resources.getString(R.string.teal),
            XabberApplication.applicationContext().resources.getString(R.string.cyan),
            XabberApplication.applicationContext().resources.getString(R.string.light_blue),
            XabberApplication.applicationContext().resources.getString(R.string.blue),
            XabberApplication.applicationContext().resources.getString(R.string.indigo),
            XabberApplication.applicationContext().resources.getString(R.string.deep_purple),
            XabberApplication.applicationContext().resources.getString(R.string.purple),
            XabberApplication.applicationContext().resources.getString(R.string.pink),
            XabberApplication.applicationContext().resources.getString(R.string.blue_grey),
            XabberApplication.applicationContext().resources.getString(R.string.brown),
        )
        val avatars = listOf(
            R.drawable.flower,
            R.drawable.angel,
            R.drawable.dog,
            R.drawable.car,
            R.drawable.sea,
            R.drawable.man
        )
        var b = 0

        val names = listOf(
            "Иван",
            "Сергей",
            "Анатолий",
            "Петр",
            "Геннадий",
            "Роман",
            "Кирилл",
            "Павел",
            "Руслан",
            "Олег",
            "Алексей",
            "Андрей",
            "Эдуард",
            "Валерий",
            "Борис",
            "Михаил",
            "Марат",
            "Игнат",
            "Лев",
            "Афанасий",
            "Антон",
            "Роман",
            "Василий",
            "Владимир",
            "Игнат",
            "Дмитрий",
            "Евгений",
            "Станислав",
            "Ян",
            "Жан"
        )
        val familys = listOf(
            "Усачев",
            "Кошкин",
            "Степанов",
            "Ветров",
            "Тимофеев",
            "Голубев",
            "Белов",
            "Ульянов",
            "Солнцев",
            "Романов",
            "Корытов",
            "Букин",
            "Сталин",
            "Горин",
            "Павлов",
            "Рубин",
            "Комов",
            "Тигров",
            "Рыбин",
            "Поддубный",
            "Журавлев",
            "Эйлерт",
            "Иванов",
            "Петров",
            "Сидоров",
            "Красовский",
            "Лужин",
            "Бродский",
            "Акинфеев",
            "Твардовский"
        )

        val accounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class).find()
        val owners = ArrayList<String>()
        accounts.forEach {
            owners.add(it.primary)
        }

        val nam = ArrayList<String>()
        for (i in 0 until names.size) {
            for (j in 0 until familys.size) {
                val m = names[i] + " " + familys[j]
                nam.add(m)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {

            realm.write {
                for (i in 0 until 900) {
                    val col = colors.random()
                    val av = avatars.random()
                    val ow = owners.random()
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = "$b 10"
                        muteExpired = -1
                        owner = ow
                        jid = "${nam[i]}@redsolution.ru"
                        rosterItem = copyToRealm(RosterStorageItem().apply {
                            primary = "$b 10"
                            owner = ow
                            jid = "${nam[i]}@redsolution.ru"
                            nickname = nam[i]
                            customNickname = nam[i]
                            colorKey = col
                            avatarR = av
                        })
                        messageDate = System.currentTimeMillis()
                        isArchived = false
                        unread = 0
                        avatar = av
                        conversationType_ = ConversationType.Group.rawValue
                    })
                    b++
                }
            }
        }
    }

    fun isSavedHas(jid: String): Boolean {
        var acc: LastChatsStorageItem? = null
        realm.writeBlocking {
            acc = this.query(LastChatsStorageItem::class, "owner = '$jid' && opponentJid = '$jid'")
                .first().find()
        }
        return acc != null
    }

    fun getPrimaryAccountColorKey(): String {
        var color = XabberApplication.applicationContext().resources.getString(R.string.blue)
        realm.writeBlocking {
            val account = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true")
                .sort("order", Sort.ASCENDING).first().find()
            color = account?.colorKey ?: XabberApplication.applicationContext().resources.getString(R.string.offline)
        }
        return color
    }


    fun setColor(id: String, color: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "primary = '$id'").first().find()
                if (item != null) findLatest(item)?.colorKey = color
            }
        }
    }

}

