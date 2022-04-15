package com.xabber.data.dto

enum class MessageKind(val value: String) {
    INITIAL("INITIAL"),
    TEXT("TEXT"),
    QUOTE("QUOTE"),
    FILES("FILES"),
    IMAGES("IMAGES"),
    GEO("GEO"),
    AUDIO("AUDIO"),
    VOICE("VOICE"),
    CALL("CALL"),
    SYSTEM("SYSTEM"),
    STICKER("STICKER"),
}