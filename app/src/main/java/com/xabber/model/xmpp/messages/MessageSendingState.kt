package com.xabber.model.xmpp.messages

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
enum class MessageSendingState(val rawValue: Int): Parcelable {
    Sending(0),
    Sended(1),
    Deliver(2),
    Read(3),
    Error(4),
    None(5),
    NotSended(6),
    Uploading(7),
}