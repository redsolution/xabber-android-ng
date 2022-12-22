package com.xabber.presentation.application.fragments.contacts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xabber.data_base.defaultRealmConfig
import com.xabber.model.dto.ChatListDto
import com.xabber.model.dto.ContactDto
import com.xabber.model.xmpp.roster.RosterStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactsViewModel : ViewModel() {
    var contacts = ArrayList<ContactDto>()

    val realm = Realm.open(defaultRealmConfig())
    private val _contactList = MutableLiveData<List<ChatListDto>>()
    val contactList: LiveData<List<ChatListDto>> = _contactList

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                realm.query(RosterStorageItem::class).find()
            request.asFlow().collect { changes: ResultsChange<RosterStorageItem> ->
                when (changes) {
                    is UpdatedResults -> {
                        changes.list
                        contacts.clear()


                    }
                    else -> {}
                }
            }
        }
    }


}