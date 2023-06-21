package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class SystemMessageVH internal constructor(
    private val listener: MessageAdapter.Listener?,
    itemView: View?, messageListener: MessageClickListener?,
    longClickListener: MessageLongClickListener?,
    private val fileListener: FileListener?
) : MessageVH(itemView!!, messageListener!!, longClickListener!!, fileListener) {

    override fun bind(messageDto: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(messageDto, vhExtraData)
        tvMessageText?.text = messageDto.messageBody
    }
}
