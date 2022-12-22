package com.xabber.model.dto

import android.os.Parcelable
import androidx.annotation.ColorRes
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
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
    var isHide: Boolean = false
) : Parcelable