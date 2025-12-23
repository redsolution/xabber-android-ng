package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import android.widget.ImageView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem

object MessageDeliveryStatusHelper {
    fun setupStatusImageView(message: MessageStorageItem, imageView: ImageView) {
        imageView.visibility =
            if (message.body.isEmpty() || !message.outgoing) {
                View.GONE
            } else {
                View.VISIBLE
            }
        imageView.setImageResource(getMessageStatusIconResource(message))
    }

    fun getMessageStatusIconResource(message: MessageStorageItem): Int =
        getMessageStatusIconResourceByStatus(message.state)

    fun getMessageStatusIconResourceByStatus(messageStatus: MessageSendingState): Int {
        return when (messageStatus) {
            MessageSendingState.Sent -> R.drawable.ic_check
            MessageSendingState.Deliver -> R.drawable.ic_check_green
            MessageSendingState.Read -> R.drawable.ic_check_all_green
            MessageSendingState.Error -> R.drawable.ic_exclamation_mark_outline
            else -> R.drawable.ic_clock_outline
        }
    }
}