package com.xabber.presentation.application.fragments.chat

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.ChatDto
import com.xabber.data.dto.ResourceStatus
import com.xabber.databinding.ItemChatBinding
import com.xabber.presentation.application.util.DateFormatter
import com.xabber.presentation.application.util.getStatusColor

class ChatAdapter(
    private val listener: ChatListener
) : ListAdapter<ChatDto, ChatAdapter.ChatViewHolder>(DiffUtilCallback) {

    interface ChatListener {
        fun onClickItem(name: String)

        fun pinChat(id: Int)

        //  fun swipeItem(id: Int)

        fun unPinChat(id: Int)

        fun deleteChat(id: Int)

        fun turnOfNotifications(id: Int)

        fun openSpecialNotificationsFragment()

        fun onClickAvatar(name: String)

    }


    class ChatViewHolder(
        private val binding: ItemChatBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatDto, listener: ChatListener) {
            with(binding) {

                chatName.text = chat.username
                if (chat.isPinned) {
                    chatGround.setBackgroundColor(
                        itemView.resources.getColor(
                            R.color.grey_100,
                            itemView.context.theme
                        )
                    )
                } else {
                    chatGround.setBackgroundColor(
                        itemView.resources.getColor(
                            R.color.white,
                            itemView.context.theme
                        )
                    )

                }

                chatMuted.isVisible = chat.isMuted
                chatPinned.isVisible = chat.isPinned
                unreadMessagesWrapper.isVisible = chat.unread > 0
                unreadMessagesCount.text = chat.unread.toString()


                var image: Int? = null
                var tint: Int? = null
                chatStatusImage.isVisible = (chat.unread < 1)
                when (chat.state) {
                    MessageState.SENDING -> {
                        tint = R.color.grey_500
                        image = R.drawable.ic_material_clock_outline_24
                    }
                    MessageState.SENT -> {
                        tint = R.color.grey_500
                        image = R.drawable.ic_material_check_24
                    }
                    MessageState.DELIVERED -> {
                        tint = R.color.green_500
                        image = R.drawable.ic_material_check_24
                    }
                    MessageState.READ -> {
                        tint = R.color.green_500
                        image = R.drawable.ic_material_check_all_24
                    }
                    MessageState.ERROR -> {
                        tint = R.color.red_500
                        image = R.drawable.ic_material_alert_circle_outline_24
                    }
                    MessageState.NOT_SENT -> {
                        tint = R.color.grey_500
                        image = R.drawable.ic_material_clock_outline_24
                    }
                    MessageState.UPLOADING -> {
                        tint = R.color.blue_500
                        image = R.drawable.ic_material_clock_outline_24
                    }
                    MessageState.NONE -> {
                        chatStatusImage.isVisible = false
                    }
                }
                if (tint != null && image != null) {
                    Glide.with(itemView)
                        .load(image)
                        .centerCrop()
                        .skipMemoryCache(true)
                        .into(chatStatusImage)
                    chatStatusImage.setColorFilter(
                        ContextCompat.getColor(itemView.context, tint),
                        PorterDuff.Mode.SRC_IN
                    )
                }


                profileDivider.setBackgroundColor(
                    itemView.resources.getColor(
                        chat.colorId,
                        itemView.context.theme
                    )
                )

                chatImage.setBackgroundColor(
                    itemView.resources.getColor(
                        chat.colorId,
                        itemView.context.theme
                    )
                )

                Glide.with(itemView)
                    .load(R.drawable.ic_avatar_placeholder)
                    .centerCrop()
                    .skipMemoryCache(true)
                    .into(chatImage)


                chatTimestamp.text = DateFormatter.dateFormat(chat.date.toString())

                chatMessage.text = chat.message

                chatSyncImage.isVisible = chat.isSynced
                chatMessage.text = if (chat.isDrafted) "Изображение 229б86 KiB" else chat.message


                if (chat.entity == RosterItemEntity.CONTACT) {
                    val icon = when (chat.status) {
                        ResourceStatus.OFFLINE -> R.drawable.ic_status_online
                        ResourceStatus.AWAY -> R.drawable.ic_status_away
                        ResourceStatus.ONLINE -> R.drawable.ic_status_online
                        ResourceStatus.XA -> R.drawable.ic_status_xa
                        ResourceStatus.DND -> R.drawable.ic_status_dnd
                        ResourceStatus.CHAT -> R.drawable.ic_status_chat

                    }

                    chatStatus16.isVisible = false
                    imContainer12.isVisible = true
                    imContainer12.setImageResource(icon)
                } else {
                    val icon =
                        when (chat.entity) {
                            RosterItemEntity.SERVER -> {
                                when (chat.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_server_unavailable
                                    else -> R.drawable.ic_status_server_online
                                }
                            }
                            RosterItemEntity.BOT -> {
                                when (chat.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_bot_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_bot_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_bot_online
                                    ResourceStatus.XA -> R.drawable.ic_status_bot_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_bot_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_bot_chat

                                }
                            }
                            RosterItemEntity.INCOGNITO_GROUP -> {
                                when (chat.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_incognito_group_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_incognito_group_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_incognito_group_online
                                    ResourceStatus.XA -> R.drawable.ic_status_incognito_group_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_incognito_group_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_incognito_group_chat

                                }
                            }


                            RosterItemEntity.GROUP -> {
                                when (chat.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_public_group_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_public_group_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_public_group_online
                                    ResourceStatus.XA -> R.drawable.ic_status_public_group_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_public_group_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_public_group_chat
                                }
                            }

                            RosterItemEntity.PRIVATE_CHAT -> {
                                when (chat.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_private_chat_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_private_chat_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_private_chat_online
                                    ResourceStatus.XA -> R.drawable.ic_status_private_chat_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_private_chat_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_private_chat
                                }
                            }

                            else -> {
                                0
                            }


                        }
                    chatStatus16.isVisible = true
                    imContainer12.isVisible = false
                    chatStatus16.setImageResource(icon)

                }



                    //   RosterItemEntity.PRIVATE_CHAT ->


                    //     val chatStatusContainer = chatStatusContainer12
                    //   if (chat.entity in listOf(CONTACT, ISSUE))
                    //        chatStatusContainer12
                    //    else
                    //       chatStatusContainer16

                    //        chatStatusContainer.isVisible = true
                    //    chatStatusContainer.setCardBackgroundColor(
                    //        itemView.resources.getColor(
                    //            chat.getStatusColor(),
                    //             itemView.context.theme
                    //         )
                    //        )


                    //      if (chat.userNickname != null) {
                    //          val spannable = SpannableString("${chat.userNickname}\n${chat.message}")
                    //          spannable.setSpan(
                    //              ForegroundColorSpan(
                    //                  itemView.resources.getColor(
                    ////                      R.color.grey_900,
                    //                     itemView.context.theme
                    // //                   )
                    //               ),
                    //                0,
                    //               chat.userNickname.length,
                    //             Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                    //          )
                    //           chatMessage.text = spannable
                    //       } else
                    //    chatMessage.text = chat.message


                    if (chat.isDrafted) {
                        val spannable = SpannableString("Drafted: ${chat.message}")
                        spannable.setSpan(
                            ForegroundColorSpan(
                                itemView.resources.getColor(
                                    R.color.red_500,
                                    itemView.context.theme
                                )
                            ),
                            0,
                            8,
                            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                        )
                        chatMessage.text = spannable
                    }

                    chatSyncImage.isVisible = chat.isSynced
                    if (chat.isSystemMessage)
                        chatMessage.setTypeface(null, Typeface.ITALIC)

                    itemView.setOnClickListener {
                        listener.onClickItem(chat.username)

                    }

                    chatImageContainer.setOnClickListener {
                        listener.onClickAvatar(chat.username)
                    }

                    itemView.setOnLongClickListener {
                        val popup = PopupMenu(itemView.context, itemView, Gravity.RIGHT)
                        if (!chat.isPinned) popup.inflate(R.menu.context_menu_chat)
                        else popup.inflate(R.menu.context_menu_chat2)

                        popup.setOnMenuItemClickListener {

                            when (it.itemId) {
                                R.id.unpin -> listener.unPinChat(chat.id)
                                R.id.to_pin -> {
                                    listener.pinChat(chat.id)
                                }
                                R.id.turn_of_notifications -> {
                                    listener.turnOfNotifications(chat.id)
                                }
                                R.id.customise_notifications -> {
                                    listener.openSpecialNotificationsFragment()
                                }
                                R.id.delete_chat -> {
                                    listener.deleteChat(chat.id)
                                }

                            }
                            true
                        }
                        popup.show()
                        true
                    }


                    fun onSwipeChatItem() {
//listener.swipeItem(chat.id)

                    }
                }
            }
        }



        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemChatBinding.inflate(inflater, parent, false)
            return ChatViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {


            holder.bind(getItem(position), listener)

        }

        private object DiffUtilCallback : DiffUtil.ItemCallback<ChatDto>() {

            override fun areItemsTheSame(oldItem: ChatDto, newItem: ChatDto) =
                oldItem.jid == newItem.jid

            override fun areContentsTheSame(oldItem: ChatDto, newItem: ChatDto): Boolean =
                oldItem.username == newItem.username &&
                        oldItem.message == newItem.message &&
                        oldItem.date == newItem.date &&
                        oldItem.state == newItem.state &&
                        oldItem.isMuted == newItem.isMuted &&
                        oldItem.isSynced == newItem.isSynced &&
                        oldItem.status == newItem.status &&
                        oldItem.entity == newItem.entity &&
                        oldItem.unread == newItem.unread &&
                        oldItem.unreadString == newItem.unreadString &&
                        oldItem.colorId == newItem.colorId &&
                        oldItem.isDrafted == newItem.isDrafted &&
                        oldItem.hasAttachment == newItem.hasAttachment &&
                        oldItem.userNickname == newItem.userNickname &&
                        oldItem.isSystemMessage == newItem.isSystemMessage &&
                        oldItem.isPinned == newItem.isPinned
        }
    }