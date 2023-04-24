package com.xabber.presentation.application.fragments.contacts

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.ChatListDto
import com.xabber.models.dto.ContactDto
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageStorageItem
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.models.xmpp.roster.RosterStorageItem
import com.xabber.utils.toChatListDto
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactAccountViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val _contactAccount = MutableLiveData<ContactDto>()
    val contactAccount: LiveData<ContactDto> = _contactAccount
    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired

    var tab: Int = 0

    private val _isDeleted = MutableLiveData<Boolean>()
    val isDeleted: LiveData<Boolean> = _isDeleted


    fun getJid(id: String): String {
        val contact = realm.query(RosterStorageItem::class, "primary = '$id'").first().find()
        return contact?.jid ?: ""
    }

    fun getContact(id: String): ContactDto {
        val contact = realm.query(RosterStorageItem::class, "primary = '$id'").first().find()
        val contactDto = ContactDto(
            primary = contact!!.primary,
            owner = contact.owner,
            jid = contact.jid,
            nickName = contact.nickname,
            customNickName = contact.customNickname,
            color = contact.colorKey,
            avatar = contact.avatarR,
            isHide = contact.isHidden,
            entity = RosterItemEntity.Contact,
            status = ResourceStatus.Chat,
            group = null
        )
        _contactAccount.value = contactDto

        return contactDto
    }

    fun initContactDataListener(id: String) {
        val request = realm.query(RosterStorageItem::class, "primary = '$id'").find()
        val contactFlow = request.asFlow()
        viewModelScope.launch(Dispatchers.IO) {
            contactFlow.collect { changes: ResultsChange<RosterStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val contact = changes.list[0]

                        withContext(Dispatchers.Main) {
                            _isDeleted.value = contact.isDeleted
                        }
                    }
                    else -> {}
                }
            }
        }
    }


    fun initListenerContactAccount(id: String) {

    }

    fun getChatId(jid: String): String {
        val item = realm.query(LastChatsStorageItem::class, "jid = '$jid'").first().find()
        return item?.primary ?: ""
    }

    fun getOwner(contactId: String): String {
        val item = realm.query(RosterStorageItem::class, "primary = '$contactId'").first().find()
        return item?.owner ?: ""
    }

    fun setCustomNickName(id: String, newNickName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item = this.query(RosterStorageItem::class, "primary = '$id'").first().find()
                item?.customNickname = newNickName
            }
        }
    }

    fun setMute(chatId: String, mute: Long) {
        Log.d("iii", "in setMute chatId = $chatId")

        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item =
                    realm.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                if (item != null) {
                    findLatest(item).also {
                        it?.muteExpired = mute
                    }

                }
//            item?.muteExpired = mute
            }
        }
    }

    fun initChatDataListener(chatId: String) {
        Log.d("iii", "chatId = $chatId")
        val request = realm.query(LastChatsStorageItem::class, "primary = '$chatId'").find()
        val lastChatsFlow = request.asFlow()
        viewModelScope.launch(Dispatchers.IO) {
            lastChatsFlow.collect { changes: ResultsChange<LastChatsStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        val chat = changes.list[0]
                        withContext(Dispatchers.Main) {
                            _muteExpired.value = chat.muteExpired
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getChat(jid: String): ChatListDto? {
        var chatListDto: ChatListDto? = null

            realm.writeBlocking {
                val chat =
                    this.query(LastChatsStorageItem::class, "jid = '$jid'").first().find()
                if (chat != null) chatListDto = chat.toChatListDto()
//                _muteExpired.value = chat?.muteExpired
            }
        Log.d("iii", "chatListDto $chatListDto")
        return chatListDto
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

    fun blockContact(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item =
                    realm.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) {
                    findLatest(item).also {

                    }

                }
            }
        }

    }

    fun deleteContact(id: String, clearHistory: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            var contactJid: String?
            realm.writeBlocking {
                val item = this.query(RosterStorageItem::class, "primary = '$id'").first().find()
                contactJid = item?.jid
                item?.isDeleted = true
                item?.owner = ""
                if (clearHistory) {
                        val chat =
                            this.query(LastChatsStorageItem::class, "opponentJid = '$contactJid'")
                                .first()
                                .find()
                        chat?.lastMessage = null

                    val messages =
                        this.query(MessageStorageItem::class, "opponent = '$contactJid'").find()
                    delete(messages)
                }

                }

            }


      }


    fun addContact(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item = this.query(RosterStorageItem::class, "primary = '$id'").first().find()
                item?.isDeleted = false
            }
        }
    }



    }
