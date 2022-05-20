package com.xabber.xmpp.messages

enum class MessageSendingState(val rawValue: Int) {
    Sending(0),
    Sended(1),
    Deliver(2),
    Read(3),
    Error(4),
    None(5),
    NotSended(6),
    Uploading(7),
}