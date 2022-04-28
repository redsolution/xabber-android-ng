package com.xabber.data.dto

enum class ResourceStatus(val value: String) {
    OFFLINE("offline"), // grey_300
    XA("xa"), // blue отошел давно
    AWAY("away"),  // yellow
    DND("dnd"),  // не беспокоить красный
    ONLINE("online"),
    CHAT("chat"),  // светозел
}