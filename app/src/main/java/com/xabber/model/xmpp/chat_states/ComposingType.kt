package com.xabber.model.xmpp.chat_states

enum class ComposingType(val rawValue: String) {
    none("none"),
    typing("typing"),
    voice("voice"),
    video("video"),
    uploadFile("uploadFile"),
    uploadImage("uploadImage"),
    uploadAudio("uploadAudio")
}