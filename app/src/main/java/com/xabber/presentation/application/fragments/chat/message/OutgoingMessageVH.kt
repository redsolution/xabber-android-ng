package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View

class OutgoingMessageVH internal constructor(
    itemView: View, inflater: LayoutInflater,
    menuItemListener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageViewHolder(itemView, inflater, menuItemListener, onViewClickListener)