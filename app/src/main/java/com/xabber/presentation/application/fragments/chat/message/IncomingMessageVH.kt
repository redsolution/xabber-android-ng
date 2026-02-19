package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class IncomingMessageVH(
    itemView: View, inflater: LayoutInflater,
    menuItemListener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageViewHolder(itemView, inflater, menuItemListener, onViewClickListener) {

    override fun bind(message: MessageStorageItem, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        val tvName = itemView.findViewById<TextView>(R.id.tv_message_username)
        if (tvName != null) {
            tvName.isVisible = vhExtraData.isNeedName && vhExtraData.isGroup
            if (tvName.isVisible) {
                tvName.text = message.groupchatDisplayedNickname ?: ""
            }
        }
    }

}