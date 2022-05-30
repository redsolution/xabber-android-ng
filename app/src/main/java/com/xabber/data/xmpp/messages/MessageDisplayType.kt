package com.xabber.data.xmpp.messages

enum class MessageDisplayType(val rawValue: String) {
    Text("text"),
    Files("files"),
    Images("images"),
    Voice("voice"),
    Call("call"),
    System("system"),
    Sticker("sticker"),
    Quote("quote"), // цитата
    Initial("initial") // плашка
}