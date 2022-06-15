package com.xabber.presentation.application.fragments.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.ImageDto
import com.xabber.databinding.ItemAttachedFileBinding


class FileAdapter(private val listener: Listener) : ListAdapter<ImageDto, FileViewHolder>(
    DiffUtilCallback
) {

    interface Listener {
        fun deleteFile()

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAttachedFileBinding.inflate(inflater, parent, false)
        return FileViewHolder(binding.root)
    }


    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<ImageDto>() {

    override fun areItemsTheSame(oldItem: ImageDto, newItem: ImageDto) =
        oldItem == newItem

    override fun areContentsTheSame(oldItem: ImageDto, newItem: ImageDto) =
        oldItem.bitmap!!.equals(newItem.bitmap)
}
}