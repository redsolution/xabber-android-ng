package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R

class DateHeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val dateTextView: TextView = itemView.findViewById(R.id.tv_date)

    fun bind(dateText: String) {
        dateTextView.text = dateText
    }
}