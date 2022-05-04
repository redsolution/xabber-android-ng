package com.xabber.presentation.application.fragments.contacts

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.ContactDto
import com.xabber.databinding.ItemContactBinding
import com.xabber.presentation.application.util.getStatusColor
import com.xabber.presentation.application.util.getStatusIcon

class ContactAdapter(
    private val listener: Listener
) : ListAdapter<ContactDto, ContactAdapter.ContactViewHolder>(DiffUtilCallback) {

    interface Listener {
        fun onAvatarClick()

        fun onContactClick()

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

                contact.getStatusColor()?.let { colorId ->
                    contactStatusContainer.setCardBackgroundColor(
                        itemView.resources.getColor(
                            colorId,
                            itemView.context.theme
                        )
                    )
                }
                //  contactStatusContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                //      if (contact.entity in listOf(CONTACT, ISSUE)) {
                //          this.width = 12.dp
                //          this.height = 12.dp
                //          contactStatusContainer.radius = 6.dp.toFloat()
                //      } else {
                //          this.width = 14.dp
                //           this.height = 14.dp
                //           contactStatusContainer.radius = 7.dp.toFloat()
                //       }
                //  }

                contact.entity?.getStatusIcon()?.let { iconId ->
                    Glide.with(itemView)
                        .load(iconId)
                        .centerCrop()
                        .skipMemoryCache(true)
                        .into(contactStatus)
                    contactStatus.background = ResourcesCompat.getDrawable(
                        itemView.resources,
                        iconId,
                        itemView.context.theme
                    )
                }

                contactImageContainer.setOnClickListener {
                    listener.onAvatarClick()
                }

                binding.root.setOnClickListener {
                    listener.onContactClick()
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