package com.xabber.presentation.application.fragments.message

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.FileDto
import com.xabber.data.dto.ImageDto
import com.xabber.databinding.ItemAttachedFileBinding

class FileViewHolder(private val binding: View
) : RecyclerView.ViewHolder(binding) {

    fun bind(fileDto: ImageDto, listener: FileAdapter.Listener) {
     //   Glide.with(binding).load(fileDto.bitmap).into(binding)
    }
}