package com.xabber.models.xmpp.messages

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class MessageSendingState(val rawValue: Int) : Parcelable {
    Sending(0),
    Sent(1),
    Deliver(2),
    Read(3),
    Error(4),
    None(5),
    NotSent(6),
    Uploading(7),
}