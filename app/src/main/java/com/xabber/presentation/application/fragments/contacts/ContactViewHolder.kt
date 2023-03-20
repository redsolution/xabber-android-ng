package com.xabber.presentation.application.fragments.contacts

import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemContactBinding
import com.xabber.models.dto.ContactDto
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.presentation.application.activity.MaskManager


class ContactViewHolder(
    private val binding: ItemContactBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(contact: ContactDto, listener: ContactAdapter.Listener) {
        with(binding) {
            contactName.text =
                if (contact.customNickName != null && contact.customNickName.isNotEmpty()) contact.customNickName else contact.nickName
            //  contactSubtitle.text = contact.subtitle

            if (contact.entity == RosterItemEntity.Contact) {
                val icon = when (contact.status) {
                    ResourceStatus.Offline -> R.drawable.status_offline
                    ResourceStatus.Away -> R.drawable.status_away
                    ResourceStatus.Online -> R.drawable.status_online
                    ResourceStatus.Xa -> R.drawable.ic_status_xa
                    ResourceStatus.Dnd -> R.drawable.status_dnd
                    ResourceStatus.Chat -> R.drawable.status_chat
                }

                contactStatus14.isVisible = true
                contactStatus14.setImageResource(icon)

            } else {
                val icon =
                    when (contact.entity) {
                        RosterItemEntity.Server -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.status_server_unavailable
                                else -> R.drawable.status_server
                            }
                        }
                        RosterItemEntity.Bot -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.status_bot_unavailable
                                ResourceStatus.Away -> R.drawable.status_bot_away
                                ResourceStatus.Online -> R.drawable.status_bot_online
                                ResourceStatus.Xa -> R.drawable.status_bot_xa
                                ResourceStatus.Dnd -> R.drawable.status_bot_dnd
                                ResourceStatus.Chat -> R.drawable.status_bot_chat
                            }
                        }
                        RosterItemEntity.IncognitoChat -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.status_incognito_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_incognito_group_away
                                ResourceStatus.Online -> R.drawable.status_incognito_group_online
                                ResourceStatus.Xa -> R.drawable.status_incognito_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_incognito_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_incognito_group_chat
                            }
                        }


                        RosterItemEntity.Groupchat -> {
                            when (contact.status) {
                                ResourceStatus.Offline -> R.drawable.status_public_group_unavailable
                                ResourceStatus.Away -> R.drawable.status_public_group_away
                                ResourceStatus.Online -> R.drawable.status_public_group_online
                                ResourceStatus.Xa -> R.drawable.status_public_group_xa
                                ResourceStatus.Dnd -> R.drawable.status_public_group_dnd
                                ResourceStatus.Chat -> R.drawable.status_public_group_chat
                            }
                        }

                        RosterItemEntity.PrivateChat -> {
                            when (contact.status) {
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

                contactStatus14.isVisible = true
                contactStatus14.setImageResource(icon)
            }


            // avatar
            binding.shapeView.setDrawable(MaskManager.mask)
            Glide.with(itemView).load(contact.avatar)
                .into(binding.contactImage)


            contactImage.setOnClickListener {
                listener.onAvatarClick(contact)
            }

            binding.root.setOnClickListener {
                listener.onContactClick(
                    contact.owner, contact.jid!!, contact.avatar
                )
            }

            itemView.setOnLongClickListener {
                val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
                popup.inflate(R.menu.popup_menu_contact_item)

                popup.setOnMenuItemClickListener {

                    when (it.itemId) {
                        R.id.edit_contact -> listener.editContact(
                            contact,
                            contact.avatar,
                            contact.color
                        )
                        R.id.delete_contact -> listener.deleteContact(contact.nickName!!)
                        R.id.block_contact -> listener.blockContact(contact.nickName!!)
                    }
                    true
                }
                popup.show()
                true
            }
        }
    }

}
