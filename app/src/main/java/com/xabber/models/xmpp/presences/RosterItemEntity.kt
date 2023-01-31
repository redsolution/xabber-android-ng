package com.xabber.models.xmpp.presences

enum class RosterItemEntity(val rawValue: String) {
    Contact("contact"),
    Groupchat("groupchat"),
    Bot("bot"),
    Server("server"),
    IncognitoChat("incognito"),
    PrivateChat("private"),
    EncryptedChat("encrypted"),
    Issue("issue")
}