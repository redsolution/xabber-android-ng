package com.xabber.model.dto

data class GroupDto(
    val name: String,
    val identifier: String,
    val description: String,
    val contacts: List<ContactDto?>,
    val isIncognito: Boolean,
    val isFree: Boolean,
    val indexing: Indexing
)