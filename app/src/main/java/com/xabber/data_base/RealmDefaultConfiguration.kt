package com.xabber.data_base

import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageForwardsInlineStorageItem
import com.xabber.models.xmpp.messages.MessageReferenceStorageItem
import com.xabber.models.xmpp.messages.MessageStorageItem
import com.xabber.models.xmpp.presences.ResourceStorageItem
import com.xabber.models.xmpp.roster.RosterGroupStorageItem
import com.xabber.models.xmpp.roster.RosterStorageItem
import io.realm.kotlin.RealmConfiguration

fun defaultRealmConfig(): RealmConfiguration {
    return RealmConfiguration.Builder(
        setOf(
            AccountStorageItem::class,
            LastChatsStorageItem::class,
            RosterStorageItem::class,
            MessageStorageItem::class,
            AvatarStorageItem::class,
            ResourceStorageItem::class,
            MessageReferenceStorageItem::class,
            RosterGroupStorageItem::class,
            MessageReferenceStorageItem::class,
            MessageForwardsInlineStorageItem::class
        )
    ).build()
}