package com.xabber.presentation.application.fragments.contacts

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.model.dto.ChatListDto
import com.xabber.model.dto.ContactDto
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.model.xmpp.roster.RosterStorageItem
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactAccountViewModel : ViewModel() {
    val realm = Realm.open(defaultRealmConfig())
    private val _contactAccount = MutableLiveData<ContactDto>()
    val contactAccount: LiveData<ContactDto> = _contactAccount
    private val _muteExpired = MutableLiveData<Long>()
    val muteExpired: LiveData<Long> = _muteExpired


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
            color = R.color.blue_500,
            avatar = R.drawable.img,
            isHide = contact.isHidden,
            entity = RosterItemEntity.Contact,
            status = ResourceStatus.Chat,
            group = null
        )
        _contactAccount.value = contactDto

        return contactDto
    }


    fun initListenerContactAccount(id: String) {

    }

    fun getChatId(jid: String): String {
        val item = realm.query(LastChatsStorageItem::class, "opponentJid = '$jid'").first().find()
        return item?.primary ?: ""
    }

    fun getOwner(contactId: String): String {
        val item = realm.query(RosterStorageItem::class, "primary = '$contactId'").first().find()
        return item?.owner ?: ""
    }

    fun setCustomNickName(newNickName: String) {
        viewModelScope.launch(Dispatchers.IO) {

        }
    }

    fun setMute(chatId: String, mute: Long) {
        Log.d("iii", "in setMute chatId = $chatId")

        viewModelScope.launch(Dispatchers.IO) {
            realm.write {
                val item = realm.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                if (item != null) {
                    findLatest(item).also {
                        it?.muteExpired = mute
                    }

                }
//            item?.muteExpired = mute
        } }
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
                        Log.d("iii", "chat = ${chat.opponentJid}")
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
        viewModelScope.async() {
            realm.writeBlocking {
                val chat =
                    realm.query(LastChatsStorageItem::class, "opponentJid = '$jid'").first().find()
                if (chat != null) chatListDto = ChatListDto(
                    id = chat.primary,
                    owner = chat.owner,
                    opponentJid = chat.opponentJid,
                    muteExpired = chat.muteExpired,
                    isArchived = chat.isArchived,
                    isSynced = chat.isSynced,
                    draftMessage = chat.draftMessage,
                    lastMessageBody = if (chat.lastMessage != null) chat.lastMessage!!.body else "",
                    lastMessageDate = if (chat.lastMessage != null) chat.lastMessage!!.date else 0L,
                    lastMessageState = if (chat.lastMessage != null) MessageSendingState.Read else MessageSendingState.None,
                    colorId = chat.color,
                    displayName = chat.rosterItem!!.nickname,
                    drawableId = chat.avatar,
                    status = ResourceStatus.Chat,
                    entity = RosterItemEntity.Contact,
                    outgoing = if (chat.lastMessage != null) chat.lastMessage!!.outgoing else false,
                    customName = if (chat.rosterItem != null) chat.rosterItem!!.customNickname else ""

                )
                _muteExpired.value = chat?.muteExpired
            }
        }
        return chatListDto
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
    }

    fun blockContact(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            realm.writeBlocking {
                val item = realm.query(LastChatsStorageItem::class, "primary = '$id'").first().find()
                if (item != null) {
                    findLatest(item).also {

                    }

                }
            }
        }

    }


}