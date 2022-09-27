package com.xabber.presentation.application.fragments.chat.message

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.model.dto.MessageDto

abstract class BasicMessageVH internal constructor(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    val messageTextTv: AppCompatTextView = itemView.findViewById(R.id.message_text)
    var needDate = false
    var date: String? = null

    init {
      //  messageTextTv.setTextAppearance(itemView.context)
    }

}