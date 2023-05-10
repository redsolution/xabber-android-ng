package com.xabber.data_base

import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageForwardsInlineStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import io.realm.kotlin.RealmConfiguration

fun defaultRealmConfig(): RealmConfiguration {
    return RealmConfiguration.Builder(
        setOf(
            com.xabber.data_base.models.account.AccountStorageItem::class,
            LastChatsStorageItem::class,
            RosterStorageItem::class,
            MessageStorageItem::class,
            com.xabber.data_base.models.avatar.AvatarStorageItem::class,
            ResourceStorageItem::class,
            RosterGroupStorageItem::class,
            MessageReferenceStorageItem::class,
            MessageForwardsInlineStorageItem::class
        )
    ).build()
}
