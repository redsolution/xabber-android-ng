package com.xabber.presentation.application.fragments.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data.dto.MessageDto

class MessageTestAdapter : RecyclerView.Adapter<MyMessageHolder>() {
    private val messageList = ArrayList<MessageDto>()

    companion object {
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
    }


    override fun getItemViewType(position: Int): Int {
        return if (messageList[position].isOutgoing == true) OUTGOING_MESSAGE
        else INCOMING_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyMessageHolder {
        val itemView = if (viewType == OUTGOING_MESSAGE) LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_message_outgoing,
                parent,
                false
            ) else LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_incoming, parent, false)
        return MyMessageHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyMessageHolder, position: Int) {
        bind(holder, position)
    }

    private fun bind(holder: MyMessageHolder, position: Int) {
        holder.initItem(messageList[position])
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    fun updateList(newBreedModels: ArrayList<MessageDto>) {
        messageList.clear()
        messageList.addAll(newBreedModels)
        notifyDataSetChanged()
    }
}