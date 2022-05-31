package com.xabber.presentation.application.fragments.message

import android.view.View
import android.widget.TextView
import com.xabber.R
import com.xabber.data.dto.MessageDto

class SystemMessageViewHolder(
    private val view: View,
    private val onAvatarClick: (MessageDto) -> Unit = {},
    private val onMessageClick: (MessageDto) -> Unit = {},
) : BasicViewHolder(
  view
) {

    private val messageText: TextView = view.findViewById(R.id.message_text)
    override fun bind(itemModel: MessageDto, next: String) {
        super.bind(itemModel, next)
       messageText.text = itemModel.messageBody

    }
}
