package com.xabber.presentation.application.fragments.chatlist

import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.support.v4.media.RatingCompat.StarStyle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.model.xmpp.presences.ResourceStatus
import com.xabber.model.xmpp.presences.RosterItemEntity
import com.xabber.utils.DateFormatter
import jp.wasabeef.glide.transformations.CropTransformation
import jp.wasabeef.glide.transformations.MaskTransformation


class ChatListViewHolder(
    private val binding: ItemChatListBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(chatListDto: ChatListDto, listener: ChatListAdapter.ChatListener) {
        with(binding) {
            // color divider
            profileDivider.setBackgroundColor(
                itemView.resources.getColor(
                    chatListDto.colorId,
                    itemView.context.theme
                )
            )

            // avatar
//            val mPictureBitmap =
//                BitmapFactory.decodeResource(itemView.resources, chatListDto.drawableId)
//            val mMaskBitmap =
//                BitmapFactory.decodeResource(itemView.resources, UiChanger.getMask().size56)
//                    .extractAlpha()
//            val maskedDrawable =
//                MaskedDrawableBitmapShader()
//            maskedDrawable.setPictureBitmap(mPictureBitmap)
//            maskedDrawable.setMaskBitmap(mMaskBitmap)
//
//            val mask = MaskPrepare.getDrawableMask(
//                itemView.resources,
//                chatListDto.drawableId,
//                UiChanger.getMask().size56
//            )

            val multiTransformation = MultiTransformation(CircleCrop())

            Glide.with(itemView).load(chatListDto.drawableId)
                .apply(RequestOptions.bitmapTransform(multiTransformation))
                .into(binding.imChatListItemAvatar)


      //   imChatListItemAvatar.setImageResource(chatListDto.drawableId)
        //    imChatListItemAvatar.setImageDrawable(mask)
            // name
            tvChatListName.text = chatListDto.owner

            // last message
            tvChatListLastMessage.text = chatListDto.lastMessageBody ?: ""

            // timeStamp
            tvChatListTimestamp.text =
                DateFormatter.dateFormat(chatListDto.lastMessageDate.toString())

            Log.d("del", "${chatListDto.owner} + ${chatListDto.pinnedDate}")
            // pinned -> background and icon
            if (chatListDto.pinnedDate > 0) {
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
            imChatListPinned.isVisible = chatListDto.pinnedDate > 0

            // muted
            imChatListMuted.isVisible = chatListDto.muteExpired > 0

            // unread messages
            if (chatListDto.unreadString.isNotEmpty()) {
                unreadMessagesWrapper.isVisible = true
                unreadMessagesCount.text = chatListDto.unreadString
            } else {
                unreadMessagesWrapper.isVisible = false
            }

            // message status
            var image: Int? = null
            var tint: Int? = null
            imChatListStatusMessage.isVisible =
                chatListDto.unreadString.isEmpty()
            when (chatListDto.lastMessageState) {
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

//            if (tint != null && image != null) {
//                Glide.with(itemView)
//                    .load(image)
//                    .centerCrop()
//                    .skipMemoryCache(true)
//                    .into(imChatListStatusMessage)
//                imChatListStatusMessage.setColorFilter(
//                    ContextCompat.getColor(itemView.context, tint),
//                    PorterDuff.Mode.SRC_IN
//                )
//            }
            if (tint != null && image != null) {
                binding.imChatListStatusMessage.setImageResource(image)
                binding.imChatListStatusMessage.setColorFilter(
                    ContextCompat.getColor(itemView.context, tint),
                    PorterDuff.Mode.SRC_IN
                )
            }

            // synced
            chatSyncImage.isVisible = chatListDto.isSynced
            //     chatMessage.text = if (chatList.) "Изображение 229б86 KiB" else chatList.lastMessage

            // contact status
            if (chatListDto.entity == RosterItemEntity.Contact) {
                val icon = when (chatListDto.status) {
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
                    when (chatListDto.entity) {
                        RosterItemEntity.Server -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_server_unavailable
                                else -> R.drawable.status_server_online
                            }
                        }
                        RosterItemEntity.Bot -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_bot_unavailable
                                ResourceStatus.Away -> R.drawable.status_bot_away
                                ResourceStatus.Online -> R.drawable.status_bot_online
                                ResourceStatus.Xa -> R.drawable.status_bot_xa
                                ResourceStatus.Dnd -> R.drawable.status_bot_dnd
                                ResourceStatus.Chat -> R.drawable.status_bot_chat

                            }
                        }
                        RosterItemEntity.IncognitoChat -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_incognito_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_incognito_group_away
                                ResourceStatus.Online -> R.drawable.status_incognito_group_online
                                ResourceStatus.Xa -> R.drawable.status_incognito_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_incognito_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_incognito_group_chat

                            }
                        }

                        RosterItemEntity.Groupchat -> {
                            when (chatListDto.status) {
                                ResourceStatus.Offline -> R.drawable.status_public_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_public_group_away
                                ResourceStatus.Online -> R.drawable.status_public_group_online
                                ResourceStatus.Xa -> R.drawable.status_public_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_public_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_public_group_chat
                            }
                        }

                        RosterItemEntity.PrivateChat -> {
                            when (chatListDto.status) {
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
            if (chatListDto.DraftMessage != null) {
                val spannable = SpannableString("Drafted: ${chatListDto.DraftMessage}")
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

            if (chatListDto.isSystemMessage) {
                tvChatListLastMessage.setTypeface(null, Typeface.ITALIC)
            } else tvChatListLastMessage.setTypeface(null, Typeface.NORMAL)

            // onClick
            itemView.setOnClickListener {
                listener.onClickItem(chatListDto)
            }

            // popup menu
            itemView.setOnLongClickListener {
                val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
                popup.inflate(R.menu.popup_menu_chat_list_item)
                if (chatListDto.isArchived) {
                    popup.menu.removeItem(R.id.to_pin)
                    popup.menu.removeItem(R.id.unpin)
                } else {
                    if (chatListDto.pinnedDate > 0) {
                        popup.menu.removeItem(R.id.to_pin)
                    } else {
                        popup.menu.removeItem(R.id.unpin)
                    }

                    if (chatListDto.muteExpired > 0) {
                        popup.menu.removeItem(R.id.turn_of_notifications)
                    } else {
                        popup.menu.removeItem(R.id.enable_notifications)
                    }

                }
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.unpin -> listener.unPinChat(chatListDto.id)
                        R.id.to_pin -> {
                            listener.pinChat(chatListDto.id)
                        }
                        R.id.turn_of_notifications -> {
                            listener.turnOfNotifications(chatListDto.id)
                        }
                        R.id.enable_notifications -> {
                            listener.enableNotifications(chatListDto.id)
                        }
                        R.id.customise_notifications -> {
                            listener.openSpecialNotificationsFragment()
                        }
                        R.id.delete -> {
                            listener.deleteChat(chatListDto.id)
                        }
                    }
                    true
                }
                popup.show()
                true
            }

        }
    }

    fun a() {}
}
