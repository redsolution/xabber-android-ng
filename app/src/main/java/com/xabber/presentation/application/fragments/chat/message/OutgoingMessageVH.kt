package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import com.xabber.presentation.application.fragments.chat.chatmodel.ChatFragmentModel

class OutgoingMessageVH(
    itemView: View, inflater: LayoutInflater,
    menuItemListener: ChatFragmentModel.MenuItemListener?,
    onViewClickListener: ChatFragmentModel.OnViewClickListener?
) : MessageViewHolder(itemView, inflater, menuItemListener, onViewClickListener)