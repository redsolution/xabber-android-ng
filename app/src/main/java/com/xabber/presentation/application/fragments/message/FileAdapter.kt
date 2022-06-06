package com.xabber.presentation.application.fragments.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.FileDto
import com.xabber.databinding.ItemAttachedFileBinding


class FileAdapter(private val listener: Listener) : ListAdapter<FileDto, FileViewHolder>(
    DiffUtilCallback
) {

    interface Listener {
        fun deleteFile()

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAttachedFileBinding.inflate(inflater, parent, false)
        return FileViewHolder(binding)
    }


    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<FileDto>() {

    override fun areItemsTheSame(oldItem: FileDto, newItem: FileDto) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: FileDto, newItem: FileDto) =
        oldItem.bitmap!!.equals(newItem.bitmap)
}
}