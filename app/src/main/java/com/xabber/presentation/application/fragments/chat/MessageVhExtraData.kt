package com.xabber.models.dto

import android.content.res.ColorStateList
import com.xabber.presentation.application.fragments.chat.message.MessageVH

data class MessageVhExtraData(
    val listener: MessageVH.FileListener?,
   // val fwdListener: ForwardedAdapter.ForwardListener?,
    val colors: MessageBalloonColors,
    val mainMessageTimestamp: Long?,
    val isUnread: Boolean,
    val isChecked: Boolean,
    val isNeedTail: Boolean,
    val isNeedDate: Boolean,
    val isNeedName: Boolean,
)

data class MessageBalloonColors(
    val incomingRegularBalloonColors: ColorStateList,
    val incomingForwardedBalloonColors: ColorStateList,

    val outgoingRegularBalloonColors: ColorStateList,
    val outgoingForwardedBalloonColors: ColorStateList,
)