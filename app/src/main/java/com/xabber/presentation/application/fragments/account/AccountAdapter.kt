package com.xabber.presentation.application.fragments.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.models.dto.AccountDto
import com.xabber.models.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.fragments.chatlist.ChatListViewHolder

class AccountAdapter(
    private val listener: Listener
) : ListAdapter<AccountDto, AccountViewHolder>(DiffUtilCallback) {

    interface Listener {
        fun setEnabled(id: String, isChecked: Boolean)
        fun onClick(id: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder =
        AccountViewHolder(
            ItemAccountForPreferenceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), listener)

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) =
        holder.bind(getItem(position))

    override fun onBindViewHolder(
        holder: AccountViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bind(getItem(position), payloads)
        }
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<AccountDto>() {

        override fun areItemsTheSame(oldItem: AccountDto, newItem: AccountDto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AccountDto, newItem: AccountDto) =
            oldItem == newItem

        override fun getChangePayload(oldItem: AccountDto, newItem: AccountDto): Any {
            val diffBundle = Bundle()
            if (oldItem.enabled != newItem.enabled) diffBundle.putBoolean(
                "enabled",
                newItem.enabled
            )
            if (oldItem.hasAvatar != newItem.hasAvatar) diffBundle.putBoolean("avatar", newItem.hasAvatar)
            return diffBundle
        }
    }

}
