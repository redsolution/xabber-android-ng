package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.util.isSameDayWith

class MessageAdapter(
    private val listener: MenuItemListener? = null,
    private val onViewClickListener: OnViewClickListener? = null,
    private val messages: ArrayList<MessageDto>,
    private val bindListener: IncomingMessageVH.BindListener? = null,
    private val avatarClickListener: IncomingMessageVH.OnMessageAvatarClickListener? = null,
    private val isGroup: Boolean
) : ListAdapter<MessageDto, MessageVH>(DiffUtilCallback),
    MessageVH.MessageClickListener,
    MessageVH.MessageLongClickListener,
    IncomingMessageVH.OnMessageAvatarClickListener {

    private var firstUnreadMessageID: String? = null
    private var isCheckMode = false
    private var recyclerView: RecyclerView? = null
    private val checkedItemIds: MutableList<String> = ArrayList()

    interface MenuItemListener {

        fun copyText(text: String)

        fun pinMessage(messageDto: MessageDto)

        fun forwardMessage(messageDto: MessageDto)

        fun replyMessage(messageDto: MessageDto)

        fun deleteMessage(primary: String)

        fun editMessage(primary: String, text: String)
    }

    interface OnViewClickListener {

        fun onLongClick(primary: String)

        fun checkItem(isChecked: Boolean, primary: String)

        fun onImageOrVideoClick(startPosition: Int, messageId: String)

        fun onLocationClick(latitude: Double, longitude: Double)
    }

    override fun getItemCount(): Int = messages.size

    fun updateAdapter(messageDtoList: List<MessageDto>) {
        messages.clear()
        messages.addAll(messageDtoList)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].displayType == MessageDisplayType.System) SYSTEM_MESSAGE else if (!messages[position].isOutgoing) INCOMING_MESSAGE else OUTGOING_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageVH {
        return when (viewType) {

            OUTGOING_MESSAGE -> OutgoingMessageVH(

                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_outgoing, parent, false
                ),
                listener,
                onViewClickListener,
            )

            INCOMING_MESSAGE -> IncomingMessageVH(

                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_incoming, parent, false
                ),
                listener,
                onViewClickListener, bindListener, this
            )

            SYSTEM_MESSAGE -> SystemMessageVH(

                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_system, parent, false
                ),
                listener, onViewClickListener
            )

            else -> throw IllegalStateException("Unsupported view type!")
        }
    }

    private fun isMessageNeedDate(position: Int): Boolean {
        val message = getMessageItem(position) ?: return true
        val previousMessage = getMessageItem(position - 1) ?: return true
        return !(message.sentTimestamp isSameDayWith previousMessage.sentTimestamp)
    }

    private fun isMessageNeedTail(position: Int): Boolean {
        if (ChatSettingsManager.bottom) {
            val message = getMessageItem(position) ?: return true
            val nextMessage = getMessageItem(position + 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false else message.isOutgoing xor nextMessage.isOutgoing
        } else {
            val message = getMessageItem(position) ?: return true
            val preMessage = getMessageItem(position - 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false else message.isOutgoing xor preMessage.isOutgoing
        }
    }

    private fun isMessageNeedName(position: Int): Boolean {
        if (!isGroup) return false
        val message = getMessageItem(position)
        val preMessage = getMessageItem(position - 1)
        return if (message?.isOutgoing == false && preMessage?.isOutgoing == true) true else message?.opponentJid != preMessage?.opponentJid
    }

    override fun onBindViewHolder(holder: MessageVH, position: Int) {
        val viewType = getItemViewType(position)
        val message = getMessageItem(position)
        holder.setIsRecyclable(false)
        if (message == null) return
        (holder as? MessageVH)?.messageId = message.primary
        val isNeedDate = isMessageNeedDate(position)

        val extraData = MessageVhExtraData(
            message.primary == firstUnreadMessageID,
            checkedItemIds.contains(message.primary),
            isMessageNeedTail(position),
            isNeedDate,
            isMessageNeedName(position), isGroup
        )

        when (viewType) {
            INCOMING_MESSAGE -> {
                (holder as? IncomingMessageVH)?.bind(message, extraData)
            }

            OUTGOING_MESSAGE -> {
                (holder as? OutgoingMessageVH)?.bind(message, extraData)
            }

            SYSTEM_MESSAGE -> {
                (holder as? SystemMessageVH)?.bind(message, extraData)
            }
        }
    }

    fun getMessageItem(position: Int): MessageDto? =
        when {
            position == RecyclerView.NO_POSITION -> null
            position < messages.size -> messages[position]
            else -> null
        }

    override fun onMessageClick(caller: View, position: Int) {
        if (isCheckMode && recyclerView?.isComputingLayout != true) {
          //  addOrRemoveCheckedItem(position)
        } else {
         //   messageListener?.onMessageClick(caller, position)
        }
    }

    override fun onLongMessageClick(position: Int) {
     //   addOrRemoveCheckedItem(position)
    }

    override fun onMessageAvatarClick(position: Int) {
        if (isCheckMode && recyclerView?.isComputingLayout != true) {
        //    addOrRemoveCheckedItem(position)
        } else {
            avatarClickListener?.onMessageAvatarClick(position)
        }
    }

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
    }

//    override fun onVoiceClick(
//        messagePosition: Int,
//        attachmentPosition: Int,
//        attachmentId: String?,
//        messageUID: String?,
//        timestamp: Long?
//    ) {
//        if (isCheckMode) {
//           // addOrRemoveCheckedItem(messagePosition)
//        } else {
//            fileListener?.onVoiceClick(
//                messagePosition, attachmentPosition, attachmentId, messageUID, timestamp
//            )
//        }
//    }

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

    companion object {
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
        const val SYSTEM_MESSAGE = 3
    }

}
