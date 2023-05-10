package com.xabber.dto

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.util.*

@Parcelize
data class MediaDto(val id: Long, val name: String, val date: Date, val uri: Uri, val duration: Long = 0): Parcelable, Comparable<MediaDto> {
    override fun compareTo(other: MediaDto): Int = other.date.compareTo(this.date)
}
