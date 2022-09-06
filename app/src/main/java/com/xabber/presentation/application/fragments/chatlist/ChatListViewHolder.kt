package com.xabber.presentation.application.fragments.chatlist

import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.helper.widget.Layer
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.databinding.ItemChatListBinding
import com.xabber.utils.mask.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.utils.DateFormatter

class ChatListViewHolder(
    private val binding: ItemChatListBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(chatList: ChatListDto, listener: ChatListAdapter.ChatListener) {
        with(binding) {
            // color divider
            profileDivider.setBackgroundColor(
                itemView.resources.getColor(
                    chatList.colorId,
                    itemView.context.theme
                )
            )

            // avatar
            val mPictureBitmap =
                BitmapFactory.decodeResource(itemView.resources, chatList.drawableId)
            val mMaskBitmap =
                BitmapFactory.decodeResource(itemView.resources, UiChanger.getMask().size56)
                    .extractAlpha()
            val maskedDrawable =
                MaskedDrawableBitmapShader()
            maskedDrawable.setPictureBitmap(mPictureBitmap)
            maskedDrawable.setMaskBitmap(mMaskBitmap)

            Glide.with(itemView)
                .load(maskedDrawable)
                .centerCrop()
                .skipMemoryCache(true)
                .into(imChatListItemAvatar)
            // name
            tvChatListName.text = chatList.displayName

            // last message
            tvChatListLastMessage.text = chatList.lastMessageBody ?: ""

            // timeStamp
            tvChatListTimestamp.text = DateFormatter.dateFormat(chatList.lastMessageDate.toString())

            // pinned -> background and icon
            if (chatList.pinnedDate > 0) {
                chatGround.setBackgroundColor(
                    itemView.resources.getColor(
                        R.color.item_chat_list_pinned_color_state,
                        itemView.context.theme
                    )
                )
            } else {
                chatGround.setBackgroundColor(
                    itemView.resources.getColor(
                        R.color.item_chat_list_color_state,
                        itemView.context.theme
                    )
                )
            }
            imChatListPinned.isVisible = chatList.pinnedDate > 0

            // muted
            imChatListMuted.isVisible = chatList.muteExpired > 0

            // unread messages
            if (chatList.unreadString != null && chatList.unreadString.isNotEmpty()) {
                unreadMessagesWrapper.isVisible = true
                unreadMessagesCount.text = chatList.unreadString
            } else {
                unreadMessagesWrapper.isVisible = false
            }

            // message status
            var image: Int? = null
            var tint: Int? = null
            imChatListStatusMessage.isVisible = chatList.unreadString == null || chatList.unreadString.isEmpty()
            when (chatList.lastMessageState) {
                MessageSendingState.Sending -> {
                    tint = R.color.grey_500
                    image = R.drawable.ic_clock_outline
                }
                MessageSendingState.Sended -> {
                    tint = R.color.grey_500
                    image = R.drawable.ic_check_green
                }
                MessageSendingState.Deliver -> {
                    tint = R.color.green_500
                    image = R.drawable.ic_check_green
                }
                MessageSendingState.Read -> {
                    tint = R.color.green_500
                    image = R.drawable.ic_check_all_green
                }
                MessageSendingState.Error -> {
                    tint = R.color.red_500
                    image = R.drawable.ic_exclamation_mark_outline
                }
                MessageSendingState.NotSended -> {
                    tint = R.color.grey_500
                    image = R.drawable.ic_clock_outline
                }
                MessageSendingState.Uploading -> {
                    tint = R.color.blue_500
                    image = R.drawable.ic_clock_outline
                }
                MessageSendingState.None -> {
                    imChatListStatusMessage.isVisible = false
                }
                else -> {}
            }

            if (tint != null && image != null) {
                Glide.with(itemView)
                    .load(image)
                    .centerCrop()
                    .skipMemoryCache(true)
                    .into(imChatListStatusMessage)
                imChatListStatusMessage.setColorFilter(
                    ContextCompat.getColor(itemView.context, tint),
                    PorterDuff.Mode.SRC_IN
                )
            }

            // synced
            chatSyncImage.isVisible = chatList.isSynced
            //     chatMessage.text = if (chatList.) "Изображение 229б86 KiB" else chatList.lastMessage

            // contact status
            if (chatList.entity == RosterItemEntity.Contact) {
                val icon = when (chatList.status) {
                    ResourceStatus.Offline -> R.drawable.status_online
                    ResourceStatus.Away -> R.drawable.status_away
                    ResourceStatus.Online -> R.drawable.status_online
                    ResourceStatus.Xa -> R.drawable.ic_status_xa
                    ResourceStatus.Dnd -> R.drawable.status_dnd
                    ResourceStatus.Chat -> R.drawable.status_chat
                }
                imChatStatus16.isVisible = false
                imChatStatus14.isVisible = true
                imChatStatus14.setImageResource(icon)
            } else {
                val icon =
                    when (chatList.entity) {
                        RosterItemEntity.Server -> {
                            when (chatList.status) {
                                ResourceStatus.Offline -> R.drawable.status_server_unavailable
                                else -> R.drawable.status_server_online
                            }
                        }
                        RosterItemEntity.Bot -> {
                            when (chatList.status) {
                                ResourceStatus.Offline -> R.drawable.status_bot_unavailable
                                ResourceStatus.Away -> R.drawable.status_bot_away
                                ResourceStatus.Online -> R.drawable.status_bot_online
                                ResourceStatus.Xa -> R.drawable.status_bot_xa
                                ResourceStatus.Dnd -> R.drawable.status_bot_dnd
                                ResourceStatus.Chat -> R.drawable.status_bot_chat

                            }
                        }
                        RosterItemEntity.IncognitoChat -> {
                            when (chatList.status) {
                                ResourceStatus.Offline -> R.drawable.status_incognito_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_incognito_group_away
                                ResourceStatus.Online -> R.drawable.status_incognito_group_online
                                ResourceStatus.Xa -> R.drawable.status_incognito_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_incognito_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_incognito_group_chat

                            }
                        }

                        RosterItemEntity.Groupchat -> {
                            when (chatList.status) {
                                ResourceStatus.Offline -> R.drawable.status_public_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_public_group_away
                                ResourceStatus.Online -> R.drawable.status_public_group_online
                                ResourceStatus.Xa -> R.drawable.status_public_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_public_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_public_group_chat
                            }
                        }

                        RosterItemEntity.PrivateChat -> {
                            when (chatList.status) {
                                ResourceStatus.Offline -> R.drawable.status_private_chat_unavailable
                                ResourceStatus.Away -> R.drawable.status_private_chat_away
                                ResourceStatus.Online -> R.drawable.status_private_chat_online
                                ResourceStatus.Xa -> R.drawable.status_private_chat_xa
                                ResourceStatus.Dnd -> R.drawable.status_private_chat_dnd
                                ResourceStatus.Chat -> R.drawable.status_private_chat
                            }
                        }
                        else -> {
                            0
                        }
                    }
                imChatStatus16.isVisible = true
                imChatStatus14.isVisible = false
                imChatStatus14.setImageResource(icon)
            }


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

            // Draft
            if (chatList.DraftMessage != null) {
                val spannable = SpannableString("Drafted: ${chatList.DraftMessage}")
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
                tvChatListLastMessage.text = spannable
            }

            if (chatList.isSystemMessage) {
                tvChatListLastMessage.setTypeface(null, Typeface.ITALIC)
            } else tvChatListLastMessage.setTypeface(null, Typeface.NORMAL)

            // onClick
            itemView.setOnClickListener {
                listener.onClickItem(chatList)
            }

            // popup menu
            itemView.setOnLongClickListener {
                val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
                if (chatList.pinnedDate > 0) popup.inflate(R.menu.popup_menu_chatlist_item_pinned)
                else popup.inflate(R.menu.popup_menu_chat_item_unpinned)

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

        }
    }
}