package com.xabber.presentation.application.fragments.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.databinding.ItemContactBinding
import com.xabber.models.dto.ContactDto

class ContactAdapter(
    private val listener: Listener
) : ListAdapter<ContactDto, ContactViewHolder>(DiffUtilCallback) {

    interface Listener {
        fun onAvatarClick(contactDto: ContactDto)

        fun onContactClick(owner: String, opponentJid: String, avatar: Int)

        fun editContact(contactDto: ContactDto, avatar: Int, color: String)

        fun deleteContact(userName: String)

        fun blockContact(userName: String)
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
        oldItem.primary == newItem.primary

    override fun areContentsTheSame(oldItem: ContactDto, newItem: ContactDto) =
        oldItem == newItem
}