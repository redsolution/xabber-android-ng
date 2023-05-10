package com.xabber.presentation.application.fragments.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ChatParams(
    val id: String,
    val avatar: Int? = null
) : Parcelable
