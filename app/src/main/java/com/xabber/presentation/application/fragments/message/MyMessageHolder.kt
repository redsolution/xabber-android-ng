package com.xabber.presentation.application.fragments.message

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data.dto.MessageDto

class MyMessageHolder(item: View) : RecyclerView.ViewHolder(item) {
    private val tvBreed: TextView = item.findViewById(R.id.tv_text_content)

    fun initItem(message: MessageDto) {
        tvBreed.text = message.messageBody
    }
}