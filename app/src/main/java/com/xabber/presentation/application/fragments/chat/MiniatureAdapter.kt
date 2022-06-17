package com.xabber.presentation.application.fragments.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.data.dto.FileDto
import com.xabber.databinding.ItemAttachedFileBinding


class MiniatureAdapter(private val listener: Listener) : ListAdapter<FileDto, FileViewHolder>(
    DiffUtilCallback
) {

    interface Listener {
        fun deleteFile(fileFto: FileDto)

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return FileViewHolder(  ItemAttachedFileBinding.inflate(inflater, parent, false))
    }


    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    private object DiffUtilCallback : DiffUtil.ItemCallback<FileDto>() {

    override fun areItemsTheSame(oldItem: FileDto, newItem: FileDto) =
        oldItem == newItem

    override fun areContentsTheSame(oldItem: FileDto, newItem: FileDto) =
        oldItem.bitmap!!.equals(newItem.bitmap)
}
}