package com.xabber.presentation.application.fragments.chat.message

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.xabber.model.dto.MessageDto

abstract class BasicMessageVH(view: View, listener: MessageAdapter.Listener? = null) :
    RecyclerView.ViewHolder(view) {


    @RequiresApi(Build.VERSION_CODES.N)
    open fun bind(
        messageDto: MessageDto,
        isNeedTail: Boolean = true,
        needDay: Boolean = true,
        showCheckbox: Boolean = false,
        isNeedTitle: Boolean
    ) {
    }

}
