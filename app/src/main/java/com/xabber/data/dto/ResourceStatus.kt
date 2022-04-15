package com.xabber.data.dto

enum class ResourceStatus(val value: String) {
    OFFLINE("offline"),
    XA("xa"),
    AWAY("away"),
    DND("dnd"),
    ONLINE("online"),
    CHAT("chat"),
}