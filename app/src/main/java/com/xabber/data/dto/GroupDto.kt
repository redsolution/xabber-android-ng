package com.xabber.data.dto

import com.xabber.presentation.application.fragments.chatlist.Indexing

data class GroupDto(
    val name: String,
    val identifier: String,
    val description: String,
    val contacts: List<ContactDto?>,
    val isIncognito: Boolean,
    val isFree: Boolean,
    val indexing: Indexing
)