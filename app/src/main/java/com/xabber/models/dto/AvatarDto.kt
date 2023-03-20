package com.xabber.models.dto

data class AvatarDto(
    val id: String = "",
    val jid: String = "",
    val owner: String = "",
    var fileUri: String = "",
    var uploadUrl: String? = null,
    var image96: String? = null,
    var image128: String? = null,
    var image192: String? = null,
    var image384: String? = null,
    var image512: String? = null
)
