package com.xabber.models.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AccountDto(
    val id: String,
    var order: Int = 0,
    val jid: String,
    var nickname: String,
    var enabled: Boolean = true,
    var statusMessage: String = "",
    val colorKey: String,
    val hasAvatar: Boolean = false
) : Parcelable, Comparable<AccountDto> {
    override fun compareTo(other: AccountDto): Int {
      return if (this.enabled && other.enabled)  this.order.compareTo(other.order)
        else this.enabled.compareTo(other.enabled)
    }
}