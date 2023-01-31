package com.xabber.models.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContactGroupDto(
    val id: String,
    var owner: String = "",
    var name: String = "",
    var isSystemGroup: Boolean = false,      // general
    var isCollapsed: Boolean = false,
    var order: Int = 0,
    var contacts: List<ContactDto>? = null
) : Parcelable