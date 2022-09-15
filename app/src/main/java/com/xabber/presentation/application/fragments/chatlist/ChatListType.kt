package com.xabber.presentation.application.fragments.chatlist

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
enum class ChatListType : Parcelable {
    RECENT,
    UNREAD,
    ARCHIVE
}
