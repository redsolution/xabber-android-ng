package com.xabber.presentation.application.fragments.chatlist

import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.ChatListDto
import com.xabber.data.dto.MessageState
import com.xabber.data.dto.ResourceStatus
import com.xabber.data.dto.RosterItemEntity
import com.xabber.databinding.ItemChatBinding
import com.xabber.presentation.application.util.DateFormatter

class ChatViewHolder(
    private val binding: ItemChatBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chatList: ChatListDto, listener: ChatListAdapter.ChatListener) {
            with(binding) {

                chatName.text = chatList.username
                if (chatList.isPinned) {
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

                chatMuted.isVisible = chatList.isMuted
                chatPinned.isVisible = chatList.isPinned
                unreadMessagesWrapper.isVisible = chatList.unreadString!!.isNotEmpty()
                unreadMessagesCount.text = chatList.unreadString


                var image: Int? = null
                var tint: Int? = null
                chatStatusImage.isVisible = (chatList.unreadString.isEmpty())
                when (chatList.state) {
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
                        chatList.colorId,
                        itemView.context.theme
                    )
                )

                chatImage.setBackgroundColor(

                    itemView.resources.getColor(
                        chatList.colorId,
                        itemView.context.theme
                    )
                )

                Glide.with(itemView)
                    .load(R.drawable.ic_avatar_placeholder)
                    .centerCrop()
                    .skipMemoryCache(true)
                    .into(chatImage)


                chatTimestamp.text = DateFormatter.dateFormat(chatList.date.toString())

                chatMessage.text = chatList.message

                chatSyncImage.isVisible = chatList.isSynced
                chatMessage.text = if (chatList.isDrafted) "Изображение 229б86 KiB" else chatList.message


                if (chatList.entity == RosterItemEntity.CONTACT) {
                    val icon = when (chatList.status) {
                        ResourceStatus.OFFLINE -> R.drawable.ic_status_online
                        ResourceStatus.AWAY -> R.drawable.ic_status_away
                        ResourceStatus.ONLINE -> R.drawable.ic_status_online
                        ResourceStatus.XA -> R.drawable.ic_status_xa
                        ResourceStatus.DND -> R.drawable.ic_status_dnd
                        ResourceStatus.CHAT -> R.drawable.ic_status_chat

                    }

                    chatStatus16.isVisible = false
                    chatStatus14.isVisible = true
                    chatStatus14.setImageResource(icon)
                } else {
                    val icon =
                        when (chatList.entity) {
                            RosterItemEntity.SERVER -> {
                                when (chatList.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_server_unavailable
                                    else -> R.drawable.ic_status_server_online
                                }
                            }
                            RosterItemEntity.BOT -> {
                                when (chatList.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_bot_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_bot_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_bot_online
                                    ResourceStatus.XA -> R.drawable.ic_status_bot_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_bot_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_bot_chat

                                }
                            }
                            RosterItemEntity.INCOGNITO_GROUP -> {
                                when (chatList.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_incognito_group_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_incognito_group_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_incognito_group_online
                                    ResourceStatus.XA -> R.drawable.ic_status_incognito_group_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_incognito_group_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_incognito_group_chat

                                }
                            }


                            RosterItemEntity.GROUP -> {
                                when (chatList.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_public_group_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_public_group_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_public_group_online
                                    ResourceStatus.XA -> R.drawable.ic_status_public_group_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_public_group_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_public_group_chat
                                }
                            }

                            RosterItemEntity.PRIVATE_CHAT -> {
                                when (chatList.status) {
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
                    chatStatus14.isVisible = false
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


                if (chatList.isDrafted) {
                    val spannable = SpannableString("Drafted: ${chatList.message}")
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

                chatSyncImage.isVisible = chatList.isSynced
                if (chatList.isSystemMessage)
                    chatMessage.setTypeface(null, Typeface.ITALIC)

                itemView.setOnClickListener {
                    listener.onClickItem(chatList.username)

                }

                chatImageContainer.setOnClickListener {
                    listener.onClickAvatar(chatList.username)
                }

                itemView.setOnLongClickListener {
                    val popup = PopupMenu(itemView.context, itemView, Gravity.RIGHT)
                    if (!chatList.isPinned) popup.inflate(R.menu.context_menu_chat)
                    else popup.inflate(R.menu.context_menu_chat2)

                    popup.setOnMenuItemClickListener {

                        when (it.itemId) {
                            R.id.unpin -> listener.unPinChat(chatList.id)
                            R.id.to_pin -> {
                                listener.pinChat(chatList.id)
                            }
                            R.id.turn_of_notifications -> {
                                listener.turnOfNotifications(chatList.id)
                            }
                            R.id.customise_notifications -> {
                                listener.openSpecialNotificationsFragment()
                            }
                            R.id.delete_chat -> {
                                listener.deleteChat(chatList.id)
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