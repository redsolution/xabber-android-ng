package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class SystemMessageVH internal constructor(
    itemView: View, inflater: LayoutInflater,
    listener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageViewHolder(itemView, inflater, listener, onViewClickListener) {

    override fun bind(message: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        tvMessageText?.text = message.messageBody
    }
}
