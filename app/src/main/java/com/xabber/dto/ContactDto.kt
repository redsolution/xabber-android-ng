package com.xabber.dto

import android.os.Parcelable
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContactDto(
    val primary: String,
    val owner: String,
    val nickName: String? = null,
    val jid: String?,
    val customNickName: String? = null,
    val color: String,
    val avatar: Int,
    val group: String?,
    val subtitle: String? = null,
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    var isDeleted: Boolean = false,
    var isHide: Boolean = false
) : Parcelable, Comparable<ContactDto> {
    override fun compareTo(other: ContactDto): Int {
        val name = when {
            customNickName?.isNotEmpty() == true -> customNickName
            nickName?.isNotEmpty() == true -> nickName
            else -> jid ?: ""
        }
        val otherName = when {
            other.customNickName?.isNotEmpty() == true -> other.customNickName
            other.nickName?.isNotEmpty() == true -> other.nickName
            else -> other.jid ?: ""
        }
        return name.compareTo(otherName, ignoreCase = true)
    }
}