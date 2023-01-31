package com.xabber.models.dto

import android.os.Parcelable
import androidx.annotation.ColorRes
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContactDto(
    val primary: String,
    val owner: String,
    val nickName: String? = null,
    val jid: String?,
    val customNickName: String? = null,
    @ColorRes val color: Int,
    val avatar: Int,
    val group: String?,
    val subtitle: String? = null,   //сообщение или jid
    val status: ResourceStatus,
    val entity: RosterItemEntity,
    var isDeleted: Boolean = false,
    var isHide: Boolean = false
) : Parcelable, Comparable<ContactDto> {
    override fun compareTo(other: ContactDto): Int {
     val name = if (customNickName != null && customNickName.isNotEmpty()) customNickName else nickName
        val otherName = if (other.customNickName != null && other.customNickName.isNotEmpty()) other.customNickName else other.nickName
        return name!!.compareTo(otherName!!)
    }
}