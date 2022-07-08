package com.xabber.presentation.application.fragments.contacts

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.view.Gravity
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.ContactDto
import com.xabber.databinding.ItemContactBinding
import com.xabber.data.xmpp.presences.ResourceStatus
import com.xabber.data.xmpp.presences.RosterItemEntity
import com.xabber.presentation.application.activity.MaskChanger
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader

class ContactViewHolder(
    private val binding: ItemContactBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(contact: ContactDto, listener: ContactAdapter.Listener) {
        with(binding) {
            Glide.with(itemView)
                .load(R.drawable.ic_avatar_placeholder)
                .centerCrop()
                .skipMemoryCache(true)
                .into(contactImage)
            contactName.text = contact.userName
            contactSubtitle.text = contact.subtitle


            if (contact.entity == RosterItemEntity.Contact) {
                val icon = when (contact.status) {
                    ResourceStatus.Offline -> R.drawable.ic_status_online
                    ResourceStatus.Away -> R.drawable.ic_status_away
                    ResourceStatus.Online -> R.drawable.ic_status_online
                    ResourceStatus.Xa -> R.drawable.ic_status_xa
                    ResourceStatus.Dnd -> R.drawable.ic_status_dnd
                    ResourceStatus.Chat -> R.drawable.ic_status_chat
                    else -> {
                        0
                    }
                }
                contactStatus16.isVisible = false
                contactStatus14.isVisible = true
                contactStatus14.setImageResource(icon)

            } else {
                val icon =
                    when (contact.entity) {
                        RosterItemEntity.Server -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.ic_status_server_unavailable
                                else -> R.drawable.ic_status_server_online
                            }
                        }
                        RosterItemEntity.Bot -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.ic_status_bot_unavailable
                                ResourceStatus.Away -> R.drawable.ic_status_bot_away
                                ResourceStatus.Online -> R.drawable.ic_status_bot_online
                                ResourceStatus.Xa -> R.drawable.ic_status_bot_xa
                                ResourceStatus.Dnd -> R.drawable.ic_status_bot_dnd
                                ResourceStatus.Chat -> R.drawable.ic_status_bot_chat

                                else -> {
                                    0
                                }
                            }
                        }
                        RosterItemEntity.IncognitoChat -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.ic_status_incognito_group_unavailable
                                ResourceStatus.Away -> R.drawable.ic_status_incognito_group_away
                                ResourceStatus.Online -> R.drawable.ic_status_incognito_group_online
                                ResourceStatus.Xa -> R.drawable.ic_status_incognito_group_xa
                                ResourceStatus.Dnd -> R.drawable.ic_status_incognito_group_dnd
                                ResourceStatus.Chat -> R.drawable.ic_status_incognito_group_chat

                                else -> {
                                    0
                                }
                            }
                        }


                        RosterItemEntity.Groupchat -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.ic_status_public_group_unavailable
                                ResourceStatus.Away -> R.drawable.ic_status_public_group_away
                                ResourceStatus.Online -> R.drawable.ic_status_public_group_online
                                ResourceStatus.Xa -> R.drawable.ic_status_public_group_xa
                                ResourceStatus.Dnd -> R.drawable.ic_status_public_group_dnd
                                ResourceStatus.Chat -> R.drawable.ic_status_public_group_chat
                                else -> {
                                    0
                                }
                            }
                        }

                        RosterItemEntity.PrivateChat -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.ic_status_private_chat_unavailable
                                ResourceStatus.Away -> R.drawable.ic_status_private_chat_away
                                ResourceStatus.Online -> R.drawable.ic_status_private_chat_online
                                ResourceStatus.Xa -> R.drawable.ic_status_private_chat_xa
                                ResourceStatus.Dnd -> R.drawable.ic_status_private_chat_dnd
                                ResourceStatus.Chat -> R.drawable.ic_status_private_chat
                                else -> {
                                    0
                                }
                            }
                        }

                        else -> {
                            0
                        }
                    }
                contactStatus16.isVisible = true
                contactStatus14.isVisible = false
                contactStatus16.setImageResource(icon)
            }

            contactImage.setBackgroundResource(R.drawable.star56)


            contactImage.setOnClickListener {
                listener.onAvatarClick()
            }

            binding.root.setOnClickListener {
                listener.onContactClick(contact.userName!!)
            }

            itemView.setOnLongClickListener {
                val popup = PopupMenu(itemView.context, itemView, Gravity.RIGHT)
                popup.inflate(R.menu.contact_context_menu)

                popup.setOnMenuItemClickListener {

                    when (it.itemId) {
                        R.id.edit_contact -> listener.editContact()
                        R.id.delete_contact -> listener.deleteContact()
                        R.id.block_contact -> listener.blockContact()

                    }
                    true
                }
                popup.show()
                true
            }




              contactImage.setBackgroundResource(MaskChanger.getMask().size48)

        }


    }


}