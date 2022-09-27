package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import android.widget.ImageView
import com.xabber.R
import com.xabber.model.dto.MessageDto
import com.xabber.model.xmpp.messages.MessageSendingState

object MessageDeliveryStatusHelper {
    fun setupStatusImageView(messageRealmObject: MessageDto, imageView: ImageView) {
        imageView.visibility =
            if (messageRealmObject.messageBody == null || !messageRealmObject.isOutgoing) {
                View.GONE
            } else {
                View.VISIBLE
            }
        imageView.setImageResource(getMessageStatusIconResource(messageRealmObject))
    }

    fun getMessageStatusIconResource(messageRealmObject: MessageDto): Int =
        getMessageStatusIconResourceByStatus(messageRealmObject.messageSendingState)

    fun getMessageStatusIconResourceByStatus(messageStatus: MessageSendingState): Int {
        return when (messageStatus) {
            MessageSendingState.Sended -> R.drawable.ic_check
            MessageSendingState.Deliver -> R.drawable.ic_check_green
            MessageSendingState.Read -> R.drawable.ic_check_all_green
            MessageSendingState.Error -> R.drawable.ic_exclamation_mark_outline
            else -> R.drawable.ic_clock_outline
        }

    }
}