package com.xabber.presentation.application.fragments.message

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.xabber.data.dto.MessageDto

abstract class BasicViewHolder(view: View, listener: MessageAdapter.Listener?) : RecyclerView.ViewHolder(view) {


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