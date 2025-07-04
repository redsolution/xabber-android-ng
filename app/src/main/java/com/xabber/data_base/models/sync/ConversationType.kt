package com.xabber.data_base.models.sync

enum class ConversationType(val rawValue: String) {
        Regular("urn:xabber:chat"),
        Group("https://xabber.com/protocol/groups"),
        Channel("https://xabber.com/protocol/channels"),
        Omemo("urn:xmpp:omemo:2"),
        Omemo1("urn:xmpp:omemo:1"),
        Axolotl("eu.siacs.conversations.axolotl"),
        Notifications("urn:xabber:xen:0"),
        Saved("urn:xabber:favorites:0")
    }


