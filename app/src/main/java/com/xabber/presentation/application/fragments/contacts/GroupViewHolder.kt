package com.xabber.presentation.application.fragments.contacts

import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemGroupContactBinding
import com.xabber.model.dto.GroupDto

class GroupViewHolder(
    private val binding: ItemGroupContactBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(group: GroupDto, listener: ContactAdapter.Listener) {
    }

}

