package com.xabber

import com.xabber.xmpp.account.AccountStorageItem
import com.xabber.xmpp.avatar.AvatarStorageItem
import com.xabber.xmpp.last_chats.LastChatsStorageItem
import com.xabber.xmpp.messages.MessageReferenceStorageItem
import com.xabber.xmpp.messages.MessageStorageItem
import com.xabber.xmpp.presences.ResourceStorageItem
import com.xabber.xmpp.roster.RosterStorageItem
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