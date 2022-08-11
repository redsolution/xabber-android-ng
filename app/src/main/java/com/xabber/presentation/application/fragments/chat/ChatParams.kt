package com.xabber.presentation.application.fragments.chat

import android.os.Parcelable
import com.xabber.data.dto.ChatListDto
import kotlinx.parcelize.Parcelize

@Parcelize
class ChatParams(val chatListDto: ChatListDto) : Parcelable