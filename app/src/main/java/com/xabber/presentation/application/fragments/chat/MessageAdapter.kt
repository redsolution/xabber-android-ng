package com.xabber.presentation.application.fragments.chat

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.model.dto.MessageDto
import com.xabber.model.xmpp.messages.MessageDisplayType
import com.xabber.databinding.ItemMessageIncomingBinding
import com.xabber.databinding.ItemMessageOutgoingBinding
import com.xabber.databinding.ItemMessageSystemBinding
import com.xabber.presentation.application.fragments.chat.message.BasicMessageVH
import com.xabber.utils.StringUtils

class MessageAdapter(
    private val listener: Listener
) : ListAdapter<MessageDto, BasicMessageVH>(DiffUtilCallback) {
    private var checkBoxVisible = false

    companion object {
        const val SYSTEM_MESSAGE = 0
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
    }

    interface Listener {
        fun copyText(text: String)
        fun forwardMessage(messageDto: MessageDto)
        fun replyMessage(messageDto: MessageDto)
        fun deleteMessage(messageDto: MessageDto)
        fun editMessage(primary: String)
        fun onLongClick(primary: String)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicMessageVH {
        val view: View
        return when (viewType) {
            SYSTEM_MESSAGE -> {
                SystemMessageMessageVH(
                    ItemMessageSystemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
            OUTGOING_MESSAGE -> {
                OutgoingMessageVH(
                    ItemMessageOutgoingBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ), listener
                )
            }
            INCOMING_MESSAGE -> {
                IncomingMessageVH(
                    ItemMessageIncomingBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ), listener
                )

            }
            else -> {
                throw IllegalStateException("Unsupported message view type!")
            }
        }
    }


    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holderMessage: BasicMessageVH, position: Int) {
        var isNeedTail = true
        if (position - 1 != null && position != 0) {
            isNeedTail =
                getItem(position - 1).owner != getItem(position).owner || getIsNeedDay(position - 1)
        }
        val isNeedTitle = getItem(position + 1).owner != getItem(position).owner || getIsNeedDay(position + 1)

        return holderMessage.bind(getItem(position), isNeedTail, getIsNeedDay(position), checkBoxVisible, isNeedTitle)
    }



    private fun getIsNeedDay(chekedPosition: Int): Boolean {
        var needDay = true
        if (chekedPosition + 1 < chekedPosition + 1 != null && chekedPosition + 1 < itemCount) {
            needDay = !StringUtils.isSameDay(
                getItem(chekedPosition).sentTimestamp,
                getItem(chekedPosition + 1).sentTimestamp
            )
        }
        return needDay
    }

    fun showCheckbox(isShow: Boolean) {
        checkBoxVisible = isShow
        notifyDataSetChanged()
    }

    fun getCheckBoxIsVisible() : Boolean = checkBoxVisible

    override fun getItemViewType(position: Int): Int {
        return when {
            getItem(position).displayType == MessageDisplayType.System -> {
                SYSTEM_MESSAGE
            }
            getItem(position).isOutgoing -> {
                OUTGOING_MESSAGE
            }
            else -> {
                INCOMING_MESSAGE
            }
        }
    }
}


private object DiffUtilCallback : DiffUtil.ItemCallback<MessageDto>() {

    override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto) =
        oldItem.primary == newItem.primary

    override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto) =
        oldItem.primary == newItem.primary &&
                oldItem.isOutgoing == newItem.isOutgoing &&
                oldItem.owner == newItem.owner &&
                oldItem.opponent == newItem.opponent &&
                oldItem.messageBody == newItem.messageBody &&
                oldItem.messageSendingState == newItem.messageSendingState &&
                oldItem.sentTimestamp == newItem.sentTimestamp &&
                oldItem.editTimestamp == newItem.editTimestamp &&
                oldItem.displayType == newItem.displayType &&
                oldItem.canEditMessage == newItem.canEditMessage &&
                oldItem.canDeleteMessage == newItem.canDeleteMessage &&
                oldItem.isGroup == newItem.isGroup
}
