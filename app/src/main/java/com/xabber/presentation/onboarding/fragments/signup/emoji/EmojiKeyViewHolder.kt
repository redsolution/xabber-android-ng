package com.xabber.presentation.onboarding.fragments.signup.emoji

import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemEmojiKeyBinding

class EmojiKeyViewHolder(
    private val binding: ItemEmojiKeyBinding,
    private val onKeyClick: (String) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: String) {
        with(binding) {
            keyText.text = item
            keyText.setOnClickListener { onKeyClick(item) }
        }
    }
}
