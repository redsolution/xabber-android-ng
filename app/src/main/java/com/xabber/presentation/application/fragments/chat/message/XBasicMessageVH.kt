package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R

abstract class XBasicMessageVH internal constructor(
    itemView: View, @StyleRes appearance: Int
) : RecyclerView.ViewHolder(itemView) {

    val messageTextTv: AppCompatTextView = itemView.findViewById(R.id.message_text)
    var needDate = false
    var date: String? = null

    init {
        messageTextTv.setTextAppearance(itemView.context, appearance)
    }

}