package com.xabber.presentation.application.CloudStorage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemCloudStorageBinding
import java.text.DecimalFormat

class StorageBlockAdapter(private val blocks: List<StorageBlock>) :
    RecyclerView.Adapter<StorageBlockAdapter.StorageBlockViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StorageBlockViewHolder {
        val binding = ItemCloudStorageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StorageBlockViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StorageBlockViewHolder, position: Int) {
        holder.bind(blocks[position])
    }

    override fun getItemCount(): Int = blocks.size

    class StorageBlockViewHolder(private val binding: ItemCloudStorageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(block: StorageBlock) {
            binding.imCloudStorageItem.setImageResource(block.iconResId)
            binding.Item.text = block.type
            binding.tvChatListLastMessage.text = "Size: ${formatSize(block.sizeInBytes)}"
            binding.size.text = formatSize(block.sizeInBytes)
        }

        private fun formatSize(bytes: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return "${DecimalFormat("#.##").format(size)} ${units[unitIndex]}"
        }
    }
}