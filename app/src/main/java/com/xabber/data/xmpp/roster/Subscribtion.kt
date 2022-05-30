package com.xabber.data.xmpp.roster

enum class RosterSubscribtion(val rawValue: String) {
    To("to"),
    From("from"),
    Both("both"),
    None("none"),
    Undefined("undefined")
}