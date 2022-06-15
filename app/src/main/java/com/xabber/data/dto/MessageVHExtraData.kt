package com.xabber.data.dto

import com.xabber.presentation.application.fragments.message.OutgoingMessageVH

class MessageVHExtraData(
    val isUnread: Boolean,
    val isChecked: Boolean,
    val isNeedTail: Boolean,
    val isNeedDate: Boolean,
    val isNeedName: Boolean,)