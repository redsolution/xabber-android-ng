package com.xabber.data.xmpp.sync

enum class ConversationType(val rawValue: String) {
    Regular("https://xabber.com/protocol/synchronization#chat"),
    Group("https://xabber.com/protocol/groups"),
    Channel("https://xabber.com/protocol/channels"),
    Omemo1("urn:xmpp:omemo:1"),
    Omemo2("urn:xmpp:omemo:2"),
}