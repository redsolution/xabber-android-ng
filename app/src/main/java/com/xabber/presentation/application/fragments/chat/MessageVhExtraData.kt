package com.xabber.presentation.application.fragments.chat

data class MessageVhExtraData(
    val isUnread: Boolean,
    val isChecked: Boolean,
    val isNeedTail: Boolean,
    val isNeedDate: Boolean,
    val isNeedName: Boolean,
    val isGroup: Boolean
)
