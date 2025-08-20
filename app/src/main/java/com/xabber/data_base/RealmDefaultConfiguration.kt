package com.xabber.data_base

import com.xabber.common.ProcessedMessageId
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.avatar.AvatarStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageForwardsInlineStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.data_base.models.roster.BlockStorageItem
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.global_index.GroupChatIndexStorageItem
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import com.xabber.xmpp.groupchat.GroupchatInvitesStorageItem
import com.xabber.xmpp.groupchat.GroupchatUserStorageItem
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import com.xabber.xmpp.messages.message.TemporaryMessageStanzaStorageItem
import com.xabber.xmpp.notifications.NotificationStorageItem
import com.xabber.xmpp.notifications.XMPPNotificationsManagerStorageItem
import com.xabber.xmpp.roster.RosterDisplayNameStorageItem
import com.xabber.xmpp.voip.voIPManager.CallMetadataStorageItem
import com.xabber.xmpp.x509.X509StorageItem
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
            RosterGroupStorageItem::class,
            MessageReferenceStorageItem::class,
            MessageForwardsInlineStorageItem::class,
            BlockStorageItem::class,
            DeviceStorageItem::class,
            GroupChatIndexStorageItem::class,
            GroupChatStorageItem::class,
//            GroupchatUserStorageItem::class,
            GroupchatInvitesStorageItem::class,
            MessageStanzaStorageItem::class,
            TemporaryMessageStanzaStorageItem::class,
            XMPPNotificationsManagerStorageItem::class,
            NotificationStorageItem::class,
            CallMetadataStorageItem::class,
            X509StorageItem::class,
            RosterDisplayNameStorageItem::class,
            ProcessedMessageId::class

        )
    ).build()
}
