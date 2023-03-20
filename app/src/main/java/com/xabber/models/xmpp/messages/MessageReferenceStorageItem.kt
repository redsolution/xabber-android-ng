package com.xabber.models.xmpp.messages


import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

enum class Kind(val rawValue: String) {
    media ("media"),
    voice ("voice"),
    forward ("forward"),
    markup ("markup"),
    mention ("mention"),
    quote ("quote"),
    groupchat ("groupchat"),
    call ("call"),
    none ("")
}
class MessageReferenceStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""
    var messageId: String = ""
    var sentDate: Double = 0.0
    var owner: String = ""
    var jid: String = ""
    var kind_: String = ""
    var mimeType: String = ""
    var begin: Int = 0
    var end: Int = 0
    var metadata_: String = ""
    var isDownloaded: Boolean = false
    var isUploaded: Boolean = false
    var isMissed: Boolean = false
    var hasError: Boolean = false
}
