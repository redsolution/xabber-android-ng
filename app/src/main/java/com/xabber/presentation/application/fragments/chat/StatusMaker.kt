package com.xabber.presentation.application.fragments.chat

import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity

object StatusMaker {

    fun statusIcon(rosterItemEntity: RosterItemEntity): Int? {
        return when (rosterItemEntity) {
            RosterItemEntity.CONTACT -> R.drawable.status_contact
            RosterItemEntity.SERVER -> R.drawable.status_server
            RosterItemEntity.BOT -> R.drawable.status_bot_chat
            RosterItemEntity.PRIVATE_CHAT -> R.drawable.status_private_chat
            RosterItemEntity.GROUP_CHAT -> R.drawable.status_public_group_online
            RosterItemEntity.INCOGNITO -> R.drawable.status_incognito_group_chat
            else -> {
                null
            }
        }
    }

    fun statusTint(resourceStatus: ResourceStatus): Int {
       return when (resourceStatus) {
            ResourceStatus.ONLINE -> R.color.green_700
            ResourceStatus.CHAT -> R.color.light_green_500
            ResourceStatus.AWAY -> R.color.amber_700
            ResourceStatus.DND -> R.color.red_700
            ResourceStatus.XA -> R.color.blue_500
            ResourceStatus.OFFLINE -> R.color.grey_500
        }
    }

    fun deliverMessageStatusIcon(messageSendingState: MessageSendingState): Pair<Int?, Int?> {
       val image = when (messageSendingState) {
            MessageSendingState.Sending, MessageSendingState.Uploading -> R.drawable.ic_clock_outline
            MessageSendingState.Sent, MessageSendingState.Deliver -> R.drawable.ic_check
            MessageSendingState.Read -> R.drawable.ic_check_all_green
            MessageSendingState.Error, MessageSendingState.NotSent -> R.drawable.ic_exclamation_mark_outline
            MessageSendingState.None -> null
        }

      val tint = when (messageSendingState) {
            MessageSendingState.Sending, MessageSendingState.Sent, MessageSendingState.NotSent, MessageSendingState.Uploading -> R.color.grey_500
            MessageSendingState.Deliver, MessageSendingState.Read -> R.color.green_500
            MessageSendingState.Error -> R.color.red_500
            MessageSendingState.None -> null
        }

        return Pair(image, tint)
    }

}
