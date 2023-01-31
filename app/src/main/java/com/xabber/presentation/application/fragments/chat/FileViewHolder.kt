package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.models.dto.FileDto
import com.xabber.databinding.ItemAttachedFileBinding
import com.xabber.presentation.application.fragments.chat.message.FileCategory

class FileViewHolder(
    private val binding: ItemAttachedFileBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(fileDto: FileDto, listener: MiniatureAdapter.Listener) {

        Log.d("uuu", "${FileCategory.determineFileCategory(fileDto.file.path)}")
        Glide.with(binding.root).load(Uri.fromFile(fileDto.file)).error(
            Glide.with(binding.root).load(
                R.drawable.ic_recent_image_placeholder
            )
        ).into(binding.itemFile)

        binding.imageDeleteFile.setOnClickListener {
            listener.deleteFile(fileDto)
        }
    }
}