package com.xabber.presentation.application.fragments.chat

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.xabber.data.dto.ImageDto

class FileViewHolder(private val binding: View
) : RecyclerView.ViewHolder(binding) {

    fun bind(fileDto: ImageDto, listener: MiniatureAdapter.Listener) {
     //   Glide.with(binding).load(fileDto.bitmap).into(binding)
    }
}