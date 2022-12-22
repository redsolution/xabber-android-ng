package com.xabber.model.dto

import android.os.Parcelable
import com.xabber.model.xmpp.messages.MessageSendingState
import kotlinx.parcelize.Parcelize

@Parcelize
data class LastMessage(
    val body: String,
    val date: Long,
    val lastMessageState: Int = 0,
    val isOutgoing: Boolean,
    val isSystemMessage: Boolean = false
): Parcelable
