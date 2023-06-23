package com.xabber.presentation.application.fragments.chat.message

import android.view.View

class OutgoingMessageVH internal constructor(
    itemView: View,
    menuItemListener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageVH(itemView, menuItemListener, onViewClickListener)