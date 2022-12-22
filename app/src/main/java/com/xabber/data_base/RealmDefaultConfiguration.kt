package com.xabber.data_base

import com.xabber.model.xmpp.account.AccountStorageItem
import com.xabber.model.xmpp.last_chats.LastChatsStorageItem
import com.xabber.model.xmpp.messages.MessageForwardsInlineStorageItem
import com.xabber.model.xmpp.messages.MessageReferenceStorageItem
import com.xabber.model.xmpp.messages.MessageStorageItem
import com.xabber.model.xmpp.presences.ResourceStorageItem
import com.xabber.model.xmpp.roster.RosterGroupStorageItem
import com.xabber.model.xmpp.roster.RosterStorageItem
import io.realm.kotlin.RealmConfiguration

fun defaultRealmConfig(): RealmConfiguration {
    return RealmConfiguration.Builder(
        setOf(
            AccountStorageItem::class,
            LastChatsStorageItem::class,
            RosterStorageItem::class,
            MessageStorageItem::class,
//        AvatarStorageItem::class,
            ResourceStorageItem::class,
            MessageReferenceStorageItem::class,
            RosterGroupStorageItem::class,
            MessageReferenceStorageItem::class,
            MessageForwardsInlineStorageItem::class
        )
    ).build()
}