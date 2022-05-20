package com.xabber.presentation.application.fragments.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.ContactDto
import com.xabber.databinding.ItemContactBinding

class ContactAdapter(
    private val listener: Listener
) : ListAdapter<ContactDto, ContactViewHolder>(DiffUtilCallback) {

    interface Listener {
        fun onAvatarClick()

        fun onContactClick(userName: String)

        fun editContact()

        fun deleteContact()

        fun blockContact()
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