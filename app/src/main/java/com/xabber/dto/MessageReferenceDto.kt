package com.xabber.dto

data class MessageReferenceDto(
    var id: String = "",
    var messageId: String = "",
    var sentDate: Double = 0.0,
    var owner: String = "",
    var jid: String = "",
    var kind_: String = "",
    var mimeType: String = "",
    var begin: Int = 0,
    var end: Int = 0,
    var metadata_: String = "",
    var isDownloaded: Boolean = false,
    var isUploaded: Boolean = false,
    var isMissed: Boolean = false,
    var hasError: Boolean = false,
    var uri: String? = null)
