package com.xabber

import com.xabber.data.xmpp.account.AccountStorageItem
import com.xabber.data.xmpp.last_chats.LastChatsStorageItem
import com.xabber.data.xmpp.messages.MessageReferenceStorageItem
import com.xabber.data.xmpp.messages.MessageStorageItem
import com.xabber.data.xmpp.presences.ResourceStorageItem
import com.xabber.data.xmpp.roster.RosterStorageItem
import io.realm.RealmConfiguration

fun defaultRealmConfig() : RealmConfiguration {
    return RealmConfiguration.Builder(
        setOf(
            AccountStorageItem::class,
            LastChatsStorageItem::class,
            RosterStorageItem::class,
            MessageStorageItem::class,
//        AvatarStorageItem::class,
            ResourceStorageItem::class,
            MessageReferenceStorageItem::class
        )
    ).build()
}