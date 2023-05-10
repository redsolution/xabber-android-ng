package com.xabber.presentation.application.fragments.chat.message

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.databinding.ItemMessageIncomingBinding
import com.xabber.databinding.ItemMessageOutgoingBinding
import com.xabber.databinding.ItemMessageSystemBinding
import com.xabber.dto.MessageDto
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.utils.StringUtils

class ChatAdapter(
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
        fun pinMessage(messageDto: MessageDto)
        fun forwardMessage(messageDto: MessageDto)
        fun replyMessage(messageDto: MessageDto)
        fun deleteMessage(primary: String)
        fun editMessage(primary: String, text: String)
        fun onLongClick(primary: String)
        fun checkItem(isChecked: Boolean, primary: String)
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
        var isNeedTail = false
        var isNeedTitle = false
//        if (position + 1 != null && position != 0) {
//            isNeedTail =
//                getItem(position + 1).owner != getItem(position).owner || getIsNeedDay(position + 1)
//        }

var isNeedUnread = false
        isNeedTail = isNeedTails(position)
        return holderMessage.bind(
            getItem(position),
            isNeedTail,
            getIsNeedDay(position),
            checkBoxVisible,
            isNeedTitle, getIsNeedUnread(position)
        )
    }

    override fun onBindViewHolder(
        holder: BasicMessageVH,
        position: Int,
        payloads: MutableList<Any>
    ) {

        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                holder.bind(
                    getItem(position), false,
                    getIsNeedDay(position),
                    checkBoxVisible,
                    false, getIsNeedUnread(position), payloads
                )
            }
            Log.d("iii", "pos = $position")
            if (position - 1 != null) notifyItemChanged(position + 1)
        }
    }

    private fun isNeedTails(position: Int): Boolean {
        if (position == currentList.size - 1) {
            return true
        } else {
            if (position < currentList.size - 1) {
                if (getItem(position).isOutgoing != getItem(position + 1).isOutgoing) return true
            }
        }
        return false
    }

    private fun getIsNeedDay(chekedPosition: Int): Boolean {
        var needDay = true
        if (chekedPosition != 0) {
        if (chekedPosition - 1 < chekedPosition - 1 != null && chekedPosition - 1 < itemCount) {
            needDay = !StringUtils.isSameDay(
                getItem(chekedPosition).sentTimestamp,
                getItem(chekedPosition - 1).sentTimestamp
            )
        }
        }
        return needDay
    }

    private fun getIsNeedUnread(chekedPosition: Int): Boolean {
        var unread = false
        if (chekedPosition != 0) {
            if (chekedPosition - 1 < chekedPosition - 1 != null && chekedPosition - 1 < itemCount) {

            if (!getItem(chekedPosition).isOutgoing && getItem(chekedPosition).isUnread) {
    unread = true
}
            }

    }
        return unread
    }


    fun getPositionId(position: Int): String = getItem(position).primary

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

    override fun getItemCount(): Int {
        return currentList.size
    }


    private object DiffUtilCallback : DiffUtil.ItemCallback<MessageDto>() {

        override fun areItemsTheSame(oldItem: MessageDto, newItem: MessageDto) =
            oldItem.primary == newItem.primary

        override fun areContentsTheSame(oldItem: MessageDto, newItem: MessageDto) =
            oldItem == newItem

//        override fun getChangePayload(oldItem: MessageDto, newItem: MessageDto): Any {
//            val diffBundle = Bundle()
//            if (oldItem.messageSendingState != newItem.messageSendingState) diffBundle.putParcelable(
//                AppConstants.PAYLOAD_MESSAGE_SENDING_STATE,
//                newItem.messageSendingState
//            )
//            return diffBundle
//        }
    }

    fun setSelectedMode(isSelected: Boolean) {
        checkBoxVisible = isSelected
    }


}