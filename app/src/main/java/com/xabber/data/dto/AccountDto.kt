package com.xabber.data.dto

data class AccountDto(
    var name: String,
    val groups: List<GroupDto?>,
)