package com.xabber.xmpp.presences

enum class ResourceStatus(val rawValue: String) {
    Offline("offline"),
    Xa("xa"),
    Away("away"),
    Dnd("dnd"),
    Online("online"),
    Chat("chat")
}