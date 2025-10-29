package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.chatmodel.ChatFragmentModel
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class IncomingMessageVH(
    itemView: View, inflater: LayoutInflater,
    menuItemListener: ChatFragmentModel.MenuItemListener?,
    onViewClickListener: ChatFragmentModel.OnViewClickListener?
) : MessageViewHolder(itemView, inflater, menuItemListener, onViewClickListener) {

    override fun bind(message: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        val tvName = itemView.findViewById<TextView>(R.id.tv_message_username)
        if (tvName != null) {
            tvName.isVisible = vhExtraData.isNeedName && message.isGroup
            tvName.layoutParams?.height = LinearLayout.LayoutParams.WRAP_CONTENT
        }
    }
}