package com.xabber.presentation.application.fragments.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.R
import com.xabber.databinding.ItemGroupBinding
import com.xabber.dto.GroupDto

class GroupAdapter(private val listener: Listener) : ListAdapter<GroupDto, GroupViewHolder>(GroupDiffCallback()) {

    interface Listener {
        fun onGroupClick(group: GroupDto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class GroupViewHolder(
    private val binding: ItemGroupBinding,
    private val listener: GroupAdapter.Listener
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

    fun bind(group: GroupDto) {
        binding.groupName.text = group.groupName
        binding.contactsCount.text = group.contactCount.toString()
        binding.groupIcon.setImageResource(R.drawable.label_outline)
        if (group.isSystemGroup) {
            binding.groupIcon.setBackgroundResource(R.color.blue_500)
        } else {
            binding.groupIcon.background = null // Clear background for non-system groups
        }
        binding.contactsCount.setTextColor(ContextCompat.getColor(itemView.context, R.color.grey_500))
        binding.root.setOnClickListener {
            listener.onGroupClick(group)
        }
    }
}

class GroupDiffCallback : DiffUtil.ItemCallback<GroupDto>() {
    override fun areItemsTheSame(oldItem: GroupDto, newItem: GroupDto): Boolean {
        return oldItem.primary == newItem.primary
    }

    override fun areContentsTheSame(oldItem: GroupDto, newItem: GroupDto): Boolean {
        return oldItem == newItem
    }
}