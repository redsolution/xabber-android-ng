package com.xabber.presentation.onboarding.fragments.signup.emoji

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.databinding.ItemEmojiTypeBinding

class EmojiTypeAdapter(
    private val onTypeClick: (Int) -> Unit
) : ListAdapter<Int, EmojiTypeViewHolder>(DiffUtilCallbackEmoji) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiTypeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEmojiTypeBinding.inflate(inflater, parent, false)
        return EmojiTypeViewHolder(binding, onTypeClick)
    }

    override fun onBindViewHolder(holder: EmojiTypeViewHolder, position: Int) =
        holder.bind(getItem(position))
}

private object DiffUtilCallbackEmoji : DiffUtil.ItemCallback<Int>() {

    override fun areItemsTheSame(oldItem: Int, newItem: Int) =
        oldItem == newItem

    override fun areContentsTheSame(oldItem: Int, newItem: Int) =
        oldItem == newItem
}
