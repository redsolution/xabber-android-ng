package com.xabber.dto

data class MessageReferenceDto(
    val id: String = "",
    val messageId: String = "",
    val fileName: String = "",
    val sentDate: Double = 0.0,
    val owner: String = "",
    val jid: String = "",
    val kind_: String = "",
    val mimeType: String = "",
    val size: Long,
    var begin: Int = 0,
    var end: Int = 0,
    var metadata_: String = "",
    var isDownloaded: Boolean = false,
    var isUploaded: Boolean = false,
    var isMissed: Boolean = false,
    var hasError: Boolean = false,
    var uri: String? = null,
    var width: Int = 0,
    var height: Int = 0,
    val isGeo: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isVoiceMessage:Boolean = false
)
