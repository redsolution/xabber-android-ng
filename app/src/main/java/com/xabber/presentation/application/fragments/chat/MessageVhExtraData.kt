package com.xabber.presentation.application.fragments.chat

import com.xabber.presentation.application.fragments.chat.message.MessageVH

data class MessageVhExtraData(
    val listener: MessageVH.FileListener?,
    val mainMessageTimestamp: Long?,
    val isUnread: Boolean,
    val isChecked: Boolean,
    val isNeedTail: Boolean,
    val isNeedDate: Boolean,
    val isNeedName: Boolean,
    val isGroup: Boolean
)
