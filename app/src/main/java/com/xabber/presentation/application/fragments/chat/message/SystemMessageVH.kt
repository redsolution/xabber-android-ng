package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class SystemMessageVH internal constructor(
    itemView: View,
    listener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageVH(itemView, listener, onViewClickListener) {

    override fun bind(message: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        tvMessageText?.text = message.messageBody
    }
}
