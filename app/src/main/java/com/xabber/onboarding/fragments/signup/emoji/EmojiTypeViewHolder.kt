package com.xabber.onboarding.fragments.signup.emoji

import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemEmojiTypeBinding

class EmojiTypeViewHolder(
    private val binding: ItemEmojiTypeBinding,
    private val onTypeClick: (Int) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(@DrawableRes item: Int) {
        with(binding) {
            emojiType.setImageDrawable(
                ContextCompat.getDrawable(
                    itemView.context,
                    item
                )
            )
            emojiType.setOnClickListener { onTypeClick(item) }
        }
    }
}