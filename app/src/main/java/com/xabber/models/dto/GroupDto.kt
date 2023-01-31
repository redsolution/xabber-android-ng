package com.xabber.models.dto

data class GroupDto(
    val name: String,
    val identifier: String,
    val description: String,
    val contacts: List<ContactDto?>,
    val isIncognito: Boolean,
    val isFree: Boolean,
)