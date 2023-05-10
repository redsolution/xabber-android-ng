package com.xabber.data_base.models.roster

enum class RosterSubscribtion(val rawValue: String) {
    To("to"),
    From("from"),
    Both("both"),
    None("none"),
    Undefined("undefined")
}
