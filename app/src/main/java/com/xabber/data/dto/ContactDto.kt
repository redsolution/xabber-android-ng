package com.xabber.data.dto

import android.os.Parcelable
import androidx.annotation.ColorRes
import com.xabber.data.xmpp.presences.ResourceStatus
import com.xabber.data.xmpp.presences.RosterItemEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContactDto(
    val primary: String,
    val owner: String,
    val userName: String? = null,
    val name: String,
    val surname: String,
    val jid: String?,
    @ColorRes val color: Int,
    val avatar: Int,
    val group: String?,
    val subtitle: String? = null,// сообщение или jid
    val status: ResourceStatus? = null,
    val entity: RosterItemEntity? = null,
) : Parcelable