package com.xabber.data.dto

import com.xabber.presentation.application.fragments.chat.ResourceStatus
import com.xabber.presentation.application.fragments.chat.RosterItemEntity

data class ContactDto(
    val kind: ContactKind,
    val owner: String,
    val jid: String?,
    val group: String?,
    val title: String? = null,
    val subtitle: String? = null,
    val status: ResourceStatus? = null,
    val entity: RosterItemEntity? = null,
    val collapsed: Boolean? = null,
    val groupPrimary: String? = null
)