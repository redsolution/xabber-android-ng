package com.xabber.presentation.application.fragments.message

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.xabber.data.dto.MessageDto

abstract class BasicViewHolder(private val view: View,): RecyclerView.ViewHolder(view) {

   open  fun bind(itemModel: MessageDto, next: String) { }
}