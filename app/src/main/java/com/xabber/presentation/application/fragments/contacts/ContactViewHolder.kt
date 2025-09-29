package com.xabber.presentation.application.fragments.contacts

import android.graphics.PorterDuff
import android.util.Log
import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.databinding.ItemContactBinding
import com.xabber.dto.ContactDto
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.presentation.application.manage.MaskManager
import io.realm.kotlin.Realm


class ContactViewHolder(
    private val binding: ItemContactBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(contact: ContactDto, listener: ContactAdapter.Listener) {
        binding.contactName.text =
            if (contact.customNickName != null && contact.customNickName.isNotEmpty()) contact.customNickName else contact.nickName
        binding.contactSubtitle.text = contact.jid

        // Check if the contact is a group chat
        val realm = Realm.open(defaultRealmConfig())
        val isGroupChat = realm.query(
            LastChatsStorageItem::class,
            "jid = $0 AND owner = $1 AND conversationType_ = $2",
            contact.jid, contact.owner, "https://xabber.com/protocol/groups"
        ).first().find() != null
        realm.close()

        val icon = if (isGroupChat) {
            RosterItemEntity.GROUP_CHAT
        } else {
            when (contact.entity) {
                RosterItemEntity.CONTACT -> RosterItemEntity.CONTACT
                RosterItemEntity.SERVER -> RosterItemEntity.SERVER
                RosterItemEntity.BOT -> RosterItemEntity.BOT
                RosterItemEntity.PRIVATE_CHAT -> RosterItemEntity.PRIVATE_CHAT
                RosterItemEntity.INCOGNITO -> RosterItemEntity.INCOGNITO
                else -> RosterItemEntity.CONTACT
            }
        }

        val iconRes = when (icon) {
            RosterItemEntity.CONTACT -> R.drawable.status_contact
            RosterItemEntity.SERVER -> R.drawable.status_server
            RosterItemEntity.BOT -> R.drawable.status_bot_chat
            RosterItemEntity.PRIVATE_CHAT -> R.drawable.status_private_chat
            RosterItemEntity.GROUP_CHAT -> R.drawable.status_public_group_online
            RosterItemEntity.INCOGNITO -> R.drawable.status_incognito_group_chat
            else -> null
        }

        val tint = when (contact.status) {
            ResourceStatus.ONLINE -> R.color.green_700
            ResourceStatus.CHAT -> R.color.light_green_500
            ResourceStatus.AWAY -> R.color.amber_700
            ResourceStatus.DND -> R.color.red_700
            ResourceStatus.XA -> R.color.blue_500
            ResourceStatus.OFFLINE -> R.color.grey_500
        }

        if (iconRes != null) {
            binding.contactStatus14.isVisible = true
            binding.contactStatus14.setImageResource(iconRes)
            binding.contactStatus14.setColorFilter(
                ContextCompat.getColor(itemView.context, tint),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.contactStatus14.isVisible = false
        }

        // Disable click events for group chats
        if (isGroupChat) {
            binding.contactImage.isEnabled = false
            binding.root.isEnabled = false
            Log.d("ContactViewHolder", "Disabled click for group chat: jid=${contact.jid}")
        } else {
            binding.contactImage.isEnabled = true
            binding.root.isEnabled = true
            binding.contactImage.setOnClickListener {
                listener.onAvatarClick(contact)
            }
            binding.root.setOnClickListener {
                listener.onContactClick(contact.owner, contact.jid!!, contact.avatar)
            }
        }

        // Avatar
        binding.shapeView.setDrawable(MaskManager.mask)
        Glide.with(itemView).load(contact.avatar).into(binding.contactImage)

        itemView.setOnLongClickListener {
            if (isGroupChat) {
                Log.d("ContactViewHolder", "Long click ignored for group chat: jid=${contact.jid}")
                return@setOnLongClickListener true
            }
            val popup = PopupMenu(itemView.context, itemView, Gravity.CENTER)
            popup.inflate(R.menu.popup_menu_contact_item)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.edit_contact -> listener.editContact(contact, contact.avatar, contact.color)
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