package com.xabber.presentation.application.CloudStorage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R

class StorageUsageAdapter(private val items: List<StorageUsageItem>) : RecyclerView.Adapter<StorageUsageAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.storageUsageTitle)
        val size: TextView = itemView.findViewById(R.id.storageUsageSize)
        val colorView: View = itemView.findViewById(R.id.storageUsageColor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_storage_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.size.text = item.size
        holder.colorView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, item.colorRes))
    }

    override fun getItemCount(): Int {
        return items.size
    }
}

data class StorageUsageItem(val title: String, val size: String, val colorRes: Int)