package com.xabber.presentation.application.fragments.contacts

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.ContactDto
import com.xabber.data.dto.ResourceStatus
import com.xabber.data.dto.RosterItemEntity
import com.xabber.databinding.ItemContactBinding

class ContactAdapter(
    private val listener: Listener
) : ListAdapter<ContactDto, ContactAdapter.ContactViewHolder>(DiffUtilCallback) {

    interface Listener {
        fun onAvatarClick()

        fun onContactClick(userName: String)

        fun editContact()

        fun deleteContact()

        fun blockContact()
    }

    class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ContactDto, listener: Listener) {
            with(binding) {
                Glide.with(itemView)
                    .load(R.drawable.ic_avatar_placeholder)
                    .centerCrop()
                    .skipMemoryCache(true)
                    .into(contactImage)
                contactName.text = contact.userName
                contactSubtitle.text = contact.subtitle


                if (contact.entity == RosterItemEntity.CONTACT) {
                    val icon = when (contact.status) {
                        ResourceStatus.OFFLINE -> R.drawable.ic_status_online
                        ResourceStatus.AWAY -> R.drawable.ic_status_away
                        ResourceStatus.ONLINE -> R.drawable.ic_status_online
                        ResourceStatus.XA -> R.drawable.ic_status_xa
                        ResourceStatus.DND -> R.drawable.ic_status_dnd
                        ResourceStatus.CHAT -> R.drawable.ic_status_chat
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
                            RosterItemEntity.SERVER -> {
                                when (contact.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_server_unavailable
                                    else -> R.drawable.ic_status_server_online
                                }
                            }
                            RosterItemEntity.BOT -> {
                                when (contact.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_bot_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_bot_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_bot_online
                                    ResourceStatus.XA -> R.drawable.ic_status_bot_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_bot_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_bot_chat

                                    else -> {
                                        0
                                    }
                                }
                            }
                            RosterItemEntity.INCOGNITO_GROUP -> {
                                when (contact.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_incognito_group_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_incognito_group_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_incognito_group_online
                                    ResourceStatus.XA -> R.drawable.ic_status_incognito_group_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_incognito_group_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_incognito_group_chat

                                    else -> {
                                        0
                                    }
                                }
                            }


                            RosterItemEntity.GROUP -> {
                                when (contact.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_public_group_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_public_group_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_public_group_online
                                    ResourceStatus.XA -> R.drawable.ic_status_public_group_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_public_group_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_public_group_chat
                                    else -> {
                                        0
                                    }
                                }
                            }

                            RosterItemEntity.PRIVATE_CHAT -> {
                                when (contact.status) {
                                    ResourceStatus.OFFLINE -> R.drawable.ic_status_private_chat_unavailable
                                    ResourceStatus.AWAY -> R.drawable.ic_status_private_chat_away
                                    ResourceStatus.ONLINE -> R.drawable.ic_status_private_chat_online
                                    ResourceStatus.XA -> R.drawable.ic_status_private_chat_xa
                                    ResourceStatus.DND -> R.drawable.ic_status_private_chat_dnd
                                    ResourceStatus.CHAT -> R.drawable.ic_status_private_chat
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

                contactImageContainer.setOnClickListener {
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
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemContactBinding.inflate(inflater, parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) =
        holder.bind(getItem(position), listener)
}

private object DiffUtilCallback : DiffUtil.ItemCallback<ContactDto>() {

    override fun areItemsTheSame(oldItem: ContactDto, newItem: ContactDto) =
        oldItem.userName == newItem.userName

    override fun areContentsTheSame(oldItem: ContactDto, newItem: ContactDto) =
        oldItem.userName == newItem.userName &&
                oldItem.status == newItem.status &&
                oldItem.subtitle == newItem.subtitle
}