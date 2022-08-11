package com.xabber.presentation.onboarding.fragments.signup.emoji

import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemEmojiTypeBinding

class EmojiTypeViewHolder(
    private val binding: ItemEmojiTypeBinding,
    private val onTypeClick: (Int) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(@StringRes item: Int) {
        with(binding) {
            emojiType.setText(item)
            emojiType.setBackgroundResource(R.color.transparent)
            emojiType.setOnClickListener {
                onTypeClick(item)
            }
        }
    }
}
