package com.xabber.presentation.application.fragments.chat

import com.xabber.R
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity

object StatusMaker {

    fun statusIcon(rosterItemEntity: RosterItemEntity): Int? {
        return when (rosterItemEntity) {
            RosterItemEntity.Contact -> R.drawable.status_contact
            RosterItemEntity.Server -> R.drawable.status_server
            RosterItemEntity.Bot -> R.drawable.status_bot_chat
            RosterItemEntity.PrivateChat -> R.drawable.status_private_chat
            RosterItemEntity.Groupchat -> R.drawable.status_public_group_online
            RosterItemEntity.IncognitoChat -> R.drawable.status_incognito_group_chat
            else -> {
                null
            }
        }
    }

    fun statusTint(resourceStatus: ResourceStatus): Int {
       return when (resourceStatus) {
            ResourceStatus.Online -> R.color.green_700
            ResourceStatus.Chat -> R.color.light_green_500
            ResourceStatus.Away -> R.color.amber_700
            ResourceStatus.Dnd -> R.color.red_700
            ResourceStatus.Xa -> R.color.blue_500
            ResourceStatus.Offline -> R.color.grey_500
        }
    }

}