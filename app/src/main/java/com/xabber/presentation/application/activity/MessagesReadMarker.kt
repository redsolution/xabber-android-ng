package com.xabber.presentation.application.activity

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.model.xmpp.messages.MessageStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults

import kotlinx.coroutines.*

object MessagesReadMarker {
   // val viewModel: ChatListViewModel by activityViewModels()
    var allCount = 0
var id = 0
var toggle = false
fun counterUnreadInChat(owner: String, opponent: String) {

}

    fun chId(newId: Int) {
        id = newId
    }

  fun startCounter() {
        Log.d("all", "init")
        val realm = Realm.open(defaultRealmConfig())
        val request = realm.query(MessageStorageItem::class)
        val unreadFlow = request.asFlow()
        GlobalScope.launch(Dispatchers.Unconfined) {

                unreadFlow.collect { changes: ResultsChange<MessageStorageItem> ->
                    when (changes) {
                        is UpdatedResults -> {
                            allCount = 0
                            changes.list
                           changes.list.forEach { T -> if (!T.isRead) allCount++ }
                            withContext(Dispatchers.Main) {

                                Log.d("badge", "M $allCount")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }





}
