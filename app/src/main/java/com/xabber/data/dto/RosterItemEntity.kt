package com.xabber.data.dto

enum class RosterItemEntity(val value: String) {
    CONTACT("contact"), // все 500
    GROUP("groupchat"),
    BOT("bot"),
    SERVER("server"),
    INCOGNITO_GROUP("incognito"),
    PRIVATE_CHAT("private")
}