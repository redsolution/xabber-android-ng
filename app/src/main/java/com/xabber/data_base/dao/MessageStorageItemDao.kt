package com.xabber.data_base.dao

import com.xabber.models.xmpp.messages.MessageStorageItem
import io.realm.kotlin.Realm

class MessageStorageItemDao(private val realm: Realm) {

    fun markAllMessagesAsRead() {
        realm.writeBlocking {
            val messages = this.query(MessageStorageItem::class).find()
            messages.forEach {
                it.isRead = true
            }
        }
    }

}