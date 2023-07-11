package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class MediaStoreImage(
    val id: Long,
    val name: String?,
    val dateAdded: Date,
    val uri: Uri?
) : Parcelable
