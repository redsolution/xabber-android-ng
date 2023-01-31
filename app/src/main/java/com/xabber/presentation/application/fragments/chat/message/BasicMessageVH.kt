package com.xabber.presentation.application.fragments.chat.message

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.xabber.models.dto.MessageDto

abstract class BasicMessageVH(view: View, listener: ChatAdapter.Listener? = null) :
    RecyclerView.ViewHolder(view) {
    var needDate = false
    var date: String? = null

    @RequiresApi(Build.VERSION_CODES.N)
    open fun bind(
        messageDto: MessageDto,
        isNeedTail: Boolean = true,
        needDay: Boolean = true,
        showCheckbox: Boolean = false,
        isNeedTitle: Boolean, isNeedUnread: Boolean
    ) {
    }
@RequiresApi(Build.VERSION_CODES.N)
    open fun bind(
        messageDto: MessageDto,
        isNeedTail: Boolean = true,
        needDay: Boolean = true,
        showCheckbox: Boolean = false,
        isNeedTitle: Boolean, isNeedUnread: Boolean, payloads: List<Any>
    ) {
    }
}
