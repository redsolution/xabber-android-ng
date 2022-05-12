package com.xabber.data.dto

data class ContactDto(
    // id = owner + jid
    // круг
    val owner: String,
    val jid: String?,
    val group: String?,
    val userName: String? = null,
    val subtitle: String? = null,// сообщение или jid
    val status: ResourceStatus? = null,
    val entity: RosterItemEntity? = null,
)