package com.xabber.presentation.application.fragments.contacts

import android.graphics.PorterDuff
import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemContactBinding
import com.xabber.dto.ContactDto
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.presentation.application.manage.MaskManager


class ContactViewHolder(
    private val binding: ItemContactBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(contact: ContactDto, listener: ContactAdapter.Listener) {

            binding.contactName.text =
                if (contact.customNickName != null && contact.customNickName.isNotEmpty()) contact.customNickName else contact.nickName

            binding.contactSubtitle.text = contact.jid

            val icon = when (contact.entity) {
                RosterItemEntity.CONTACT -> R.drawable.status_contact
                RosterItemEntity.SERVER -> R.drawable.status_server
                RosterItemEntity.BOT -> R.drawable.status_bot_chat
                RosterItemEntity.PRIVATE_CHAT -> R.drawable.status_private_chat
                RosterItemEntity.GROUP_CHAT -> R.drawable.status_public_group_online
                RosterItemEntity.INCOGNITO -> R.drawable.status_incognito_group_chat
                else -> {
                    null
                }
            }

            val tint = when (contact.status) {
                ResourceStatus.ONLINE -> R.color.green_700
                ResourceStatus.CHAT -> R.color.light_green_500
                ResourceStatus.AWAY -> R.color.amber_700
                ResourceStatus.DND -> R.color.red_700
                ResourceStatus.XA -> R.color.blue_500
                ResourceStatus.OFFLINE -> R.color.grey_500
            }

            if (icon != null) {
                binding.contactStatus14.isVisible = true
                binding.contactStatus14.setImageResource(icon)
                binding.contactStatus14.setColorFilter(
                    ContextCompat.getColor(itemView.context, tint),
                   PorterDuff.Mode.SRC_IN
                )
            }

            // avatar
            binding.shapeView.setDrawable(MaskManager.mask)
            Glide.with(itemView).load(contact.avatar)
                .into(binding.contactImage)


            binding.contactImage.setOnClickListener {
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
