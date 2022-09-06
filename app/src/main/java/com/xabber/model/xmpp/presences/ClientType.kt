package com.xabber.model.xmpp.presences

enum class ClientType(val rawValue: String) {
    Unknown("none"),
    Bot("bot"),
    Console("console"),
    Game("game"),
    Handheld("handheld"),
    Pc("pc"),
    Phone("phone"),
    Sms("sms"),
    Web("web"),
    Groupchat("groupchat")
}