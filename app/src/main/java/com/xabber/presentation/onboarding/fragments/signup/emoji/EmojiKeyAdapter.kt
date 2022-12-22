package com.xabber.presentation.onboarding.fragments.signup.emoji

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.databinding.ItemEmojiKeyBinding

class EmojiKeyAdapter(
    private val onKeyClick: (String) -> Unit
) : ListAdapter<String, EmojiKeyViewHolder>(DiffUtilCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiKeyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEmojiKeyBinding.inflate(inflater, parent, false)
        return EmojiKeyViewHolder(binding, onKeyClick)
    }

    override fun onBindViewHolder(holder: EmojiKeyViewHolder, position: Int) =
        holder.bind(getItem(position))
}

private object DiffUtilCallback : DiffUtil.ItemCallback<String>() {

    override fun areItemsTheSame(oldItem: String, newItem: String) =
        oldItem == newItem

    override fun areContentsTheSame(oldItem: String, newItem: String) =
        oldItem == newItem
}
