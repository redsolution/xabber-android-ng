package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class OutgoingMessageVH(
    itemView: View, inflater: LayoutInflater,
    menuItemListener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageViewHolder(itemView, inflater, menuItemListener, onViewClickListener) {

    private val tvName: TextView? = itemView.findViewById(R.id.tv_message_username)

    override fun bind(message: MessageStorageItem, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        if (tvName != null) {
            tvName.isVisible = vhExtraData.isNeedName && vhExtraData.isGroup
            if (tvName.isVisible) {
                tvName.text = "You:"
            }
        }
    }
}