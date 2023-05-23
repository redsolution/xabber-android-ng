package com.xabber.presentation.application.fragments.test

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.models.dto.MessageBalloonColors
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.application.fragments.chat.ReferenceRealmObject
import com.xabber.presentation.application.fragments.chat.message.XBasicMessageVH
import com.xabber.presentation.application.fragments.chat.message.XMessageVH
import com.xabber.presentation.application.fragments.test.MessageAdapter.Companion.VIEW_TYPE_INCOMING_MESSAGE
import com.xabber.presentation.application.util.isSameDayWith

class MessageAdapter(
    private val listener: Listener? = null,
    private val context: Context,
    private val messageRealmObjects: ArrayList<MessageDto>,
    private val fileListener: XMessageVH.FileListener? = null,
    private val adapterListener: AdapterListener? = null,
    private val bindListener: XIncomingMessageVH.BindListener? = null,
    private val avatarClickListener: XIncomingMessageVH.OnMessageAvatarClickListener? = null,
) : ListAdapter<MessageDto, XBasicMessageVH>(MessageAdapter.DiffUtilCallback),
    XMessageVH.MessageClickListener,
    XMessageVH.MessageLongClickListener,
    XMessageVH.FileListener, XIncomingMessageVH.OnMessageAvatarClickListener {

    private var firstUnreadMessageID: String? = null
    private var isCheckMode = false

    private var uu = 0

    //    private val isSavedMessagesMode: Boolean =
//        chat.account.bareJid.toString() == chat.contactJid.bareJid.toString()
    private var recyclerView: RecyclerView? = null

    val checkedItemIds: MutableList<String> = ArrayList()
    //  val checkedMessageRealmObjects: MutableList<MessageRealmObject?> = ArrayList()

    init {
        //  messageRealmObjects.addChangeListener(realmListener)
    }

//    private fun getItem(index: Int): MessageRealmObject? {
//        require(index >= 0) { "Only indexes >= 0 are allowed. Input was: $index" }
//        return when {
//            index >= messageRealmObjects.size -> null
//            messageRealmObjects.isValid && messageRealmObjects.isLoaded -> messageRealmObjects[index]
//            else -> null
//        }
//    }

//    override fun getItemCount(): Int {
//        return current
//    }
//        if (messageRealmObjects.isValid && messageRealmObjects.isLoaded) {
//            messageRealmObjects.size
//        } else {
//            0
//        }

    override fun getItemViewType(position: Int): Int {
//        val messageRealmObject = getMessageItem(position) ?: return 0
//
//        if (messageRealmObject.action != null || messageRealmObject.isGroupchatSystem) {
//            return VIEW_TYPE_SYSTEM_MESSAGE
//        }
//
//        val isMeInGroup =
//            messageRealmObject.groupchatUserId != null
//                    && chat is GroupChat
//                    && getMe(chat) != null
//                    && getMe(chat)!!.memberId != null
//                    && (getMe(chat)!!.memberId == messageRealmObject.groupchatUserId)
//
//        val isNeedUnpackSingleMessageForSavedMessages = (isSavedMessagesMode
//                && messageRealmObject.hasForwardedMessages()
//                && MessageRepository.getForwardedMessages(messageRealmObject).size == 1)
//
//        return if (isNeedUnpackSingleMessageForSavedMessages) {
//            val innerSingleSavedMessage =
//                MessageRepository.getForwardedMessages(messageRealmObject)[0]
//
//            val contact =
//                if (innerSingleSavedMessage.originalFrom != null) {
//                    innerSingleSavedMessage.originalFrom
//                } else {
//                    innerSingleSavedMessage.user.toString()
//                }
//
//            when {
//                !contact.contains(chat.account.bareJid.toString()) -> {
//                    VIEW_TYPE_SAVED_SINGLE_COMPANION_MESSAGE
//                }
//                else -> VIEW_TYPE_SAVED_SINGLE_OWN_MESSAGE
//            }
//        } else {

        return if (!messageRealmObjects[position].isOutgoing) VIEW_TYPE_INCOMING_MESSAGE else VIEW_TYPE_OUTGOING_MESSAGE

    }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): XBasicMessageVH {
        Log.d("uuu", "onCreate")
        return when (viewType) {

//            VIEW_TYPE_SYSTEM_MESSAGE -> SystemMessageVH(
//                LayoutInflater.from(parent.context).inflate(
//                    R.layout.item_system_message, parent, false
//                )
//            )

            VIEW_TYPE_INCOMING_MESSAGE -> XIncomingMessageVH(
                listener,
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_incoming_dev, parent, false
                ),
                this, this, this, bindListener, this, R.style.ThemeApplication
            )

//            VIEW_TYPE_SAVED_SINGLE_COMPANION_MESSAGE -> SavedCompanionMessageVH(
//                LayoutInflater.from(parent.context).inflate(
//                    R.layout.item_message_incoming, parent, false
//                ),
//                this, this, this, bindListener, this, SettingsManager.chatsAppearanceStyle()
//            )

            VIEW_TYPE_OUTGOING_MESSAGE -> XOutgoingMessageVH(
                listener,
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_outgoing_test, parent, false
                ),
                this, this, this, R.style.ThemeApplication
            )

//            VIEW_TYPE_SAVED_SINGLE_OWN_MESSAGE -> SavedOwnMessageVh(
//                LayoutInflater.from(parent.context).inflate(
//                    R.layout.item_message_outgoing, parent, false
//                ),
//                this, this, this, SettingsManager.chatsAppearanceStyle()
//            )
            else -> throw IllegalStateException("Unsupported view type!")
        }
    }

    private fun isMessageNeedTail(position: Int): Boolean {
      if (MessageChanger.typeValue % 2 != 0) {
        val message = getMessageItem(position) ?: return true
        val nextMessage = getMessageItem(position + 1) ?: return true
        return if (message.references.size > 0 && message.messageBody.isEmpty()) false else message.isOutgoing xor nextMessage.isOutgoing }
        else {
          val message = getMessageItem(position) ?: return true
          val preMessage = getMessageItem(position - 1) ?: return true
          return if (message.references.size > 0 && message.messageBody.isEmpty()) false else message.isOutgoing xor preMessage.isOutgoing
      }
    }

    private fun isMessageNeedDate(position: Int): Boolean {
        val message = getMessageItem(position) ?: return true
        val previousMessage = getMessageItem(position - 1) ?: return true
        val result = !(message.sentTimestamp isSameDayWith previousMessage.sentTimestamp)
        Log.d(
            "ddd",
            "message.sentTimestamp = ${message.sentTimestamp}, previousMessage.sentTimestamp = ${previousMessage.sentTimestamp}, $result"
        )

        return result
    }

//    private fun isFirstMessageUnread(position: Int): Boolean {
//        var result = false
//        val message = getMessageItem(position)
//
//        if (message == null) result = false
//        else {
//            if (message.isOutgoing) result = false
//            else {
//                val previousMessage = getMessageItem(position - 1)
//                if (previousMessage == null) result = false
//                else {
//                    if (!previousMessage.isUnread && message.isUnread) result = true
//                }
//            }
//        }
//        uu = 1
//        return result
//    }

    private fun isMessageNeedName(position: Int): Boolean {
        val message = getMessageItem(position) ?: return false
        val previousMessage = getMessageItem(position - 1) ?: return false

        return false
    }

    override fun onBindViewHolder(holder: XBasicMessageVH, position: Int) {
        val viewType = getItemViewType(position)
        val message = getMessageItem(position)

        if (message == null) {

            return
        }

        holder.setIsRecyclable(false)

        // setup message uniqueId
        (holder as? XMessageVH)?.messageId = message.primary

//        val groupMember =
//            if (message.groupchatUserId != null && message.groupchatUserId.isNotEmpty()) {
//                getGroupMemberById(chat.account, chat.contactJid, message.groupchatUserId)
//            } else {
//                null
//            }

        val isNeedDate = isMessageNeedDate(position)

        val outgoingRegularTypedValue = TypedValue()
//        context.theme.resolveAttribute(R.attr.message_background, outgoingRegularTypedValue, true)
//        val outgoingForwardedTypedValue = TypedValue()
//        context.theme.resolveAttribute(
//            R.attr.forwarded_outgoing_message_background,
//            outgoingForwardedTypedValue,
//            true
//        )

        // val isAuthorMe = groupMember?.isMe ?: !message.isIncoming
        val states = arrayOf(
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_pressed)
        )

        val colors = intArrayOf(
            Color.WHITE,
            Color.WHITE,
            Color.WHITE,
            Color.WHITE
        )

        val balloonColors = MessageBalloonColors(
            ColorStateList(states, colors),
            ColorStateList(states, colors),
            ColorStateList(states, colors),
            ColorStateList(states, colors)
        )

        val extraData = MessageVhExtraData(
            fileListener,
            balloonColors,
            message.sentTimestamp,
            //   message.isUnread,
            message.primary == firstUnreadMessageID,
            checkedItemIds.contains(message.primary),
            isMessageNeedTail(position),
            isNeedDate,
            isMessageNeedName(position)
        )

        when (viewType) {
            VIEW_TYPE_INCOMING_MESSAGE -> {
                (holder as? XIncomingMessageVH)?.bind(message, extraData)
            }

            VIEW_TYPE_OUTGOING_MESSAGE -> {
                (holder as? XOutgoingMessageVH)?.bind(message, extraData)
            }

        }
    }

    fun getMessageItem(position: Int): MessageDto? =
        when {
            position == RecyclerView.NO_POSITION -> null
            position < messageRealmObjects.size -> messageRealmObjects[position]
            else -> null
        }

    override fun onMessageClick(caller: View, position: Int) {
//        if (isCheckMode && recyclerView?.isComputingLayout != true) {
//            addOrRemoveCheckedItem(position)
//        } else {
//            messageListener?.onMessageClick(caller, position)
//        }
    }

    override fun onLongMessageClick(position: Int) {
        addOrRemoveCheckedItem(position)
    }

    override fun onMessageAvatarClick(position: Int) {
        if (isCheckMode && recyclerView?.isComputingLayout != true) {
            addOrRemoveCheckedItem(position)
        } else {
            avatarClickListener?.onMessageAvatarClick(position)
        }
    }

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
    }

    override fun onImageClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?) {
        if (isCheckMode) {
            addOrRemoveCheckedItem(messagePosition)
        } else {
            fileListener?.onImageClick(messagePosition, attachmentPosition, messageUID)
        }
    }

    override fun onFileClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?) {
        if (isCheckMode) {
            addOrRemoveCheckedItem(messagePosition)
        } else {
            fileListener?.onFileClick(messagePosition, attachmentPosition, messageUID)
        }
    }

    override fun onVoiceClick(
        messagePosition: Int,
        attachmentPosition: Int,
        attachmentId: String?,
        messageUID: String?,
        timestamp: Long?
    ) {
        if (isCheckMode) {
            addOrRemoveCheckedItem(messagePosition)
        } else {
            fileListener?.onVoiceClick(
                messagePosition, attachmentPosition, attachmentId, messageUID, timestamp
            )
        }
    }

    override fun onFileLongClick(referenceRealmObject: ReferenceRealmObject?, caller: View?) {
        //  TODO("Not yet implemented")
    }

//    override fun onFileLongClick(referenceRealmObject: ArrayList<MessageDto>, caller: View?) {
//      //  fileListener?.onFileLongClick(referenceRealmObject, caller)
//    }

    override fun onDownloadCancel() {
        fileListener?.onDownloadCancel()
    }

    override fun onUploadCancel() {
        fileListener?.onUploadCancel()
    }

    override fun onDownloadError(error: String?) {
        fileListener?.onDownloadError(error)
    }

    /** Checked items  */
    private fun addOrRemoveCheckedItem(position: Int) {
//        if (recyclerView?.isComputingLayout == true || recyclerView?.isAnimating == true) {
//            return
//        }
//
//        recyclerView?.stopScroll()
//        val messageRealmObject = getMessageItem(position)
//        val uniqueId = messageRealmObject?.primaryKey
//
//        if (checkedItemIds.contains(uniqueId)) {
//            checkedMessageRealmObjects.remove(messageRealmObject)
//            checkedItemIds.remove(uniqueId)
//        } else {
//            uniqueId?.let { checkedItemIds.add(it) }
//            checkedMessageRealmObjects.add(messageRealmObject)
//        }
//
//        val isCheckModePrevious = isCheckMode
//
//        isCheckMode = checkedItemIds.size > 0
//        if (isCheckMode != isCheckModePrevious) {
//            notifyDataSetChanged()
//        } else {
//            notifyItemChanged(position)
//        }
//
//        adapterListener?.onChangeCheckedItems(checkedItemIds.size)
    }

    fun resetCheckedItems() {
        if (checkedItemIds.size > 0) {
            checkedItemIds.clear()
            //  checkedMessageRealmObjects.clear()
            isCheckMode = false
            notifyDataSetChanged()
            adapterListener?.onChangeCheckedItems(checkedItemIds.size)
        }
    }

    fun release() {
        //  messageRealmObjects.removeChangeListener(realmListener)
    }

    companion object {
        const val VIEW_TYPE_INCOMING_MESSAGE = 1
        const val VIEW_TYPE_OUTGOING_MESSAGE = 2
        const val VIEW_TYPE_SAVED_SINGLE_OWN_MESSAGE = 3
        const val VIEW_TYPE_SAVED_SINGLE_COMPANION_MESSAGE = 4
        const val VIEW_TYPE_SYSTEM_MESSAGE = 5
    }

    interface AdapterListener {
        fun onMessagesUpdated()
        fun onChangeCheckedItems(checkedItems: Int)
        fun scrollTo(position: Int)
    }

    override fun getItemCount(): Int = messageRealmObjects.size


    fun updateAdapter(mess: ArrayList<MessageDto>) {
        messageRealmObjects.clear()
        messageRealmObjects.addAll(mess)
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


    fun setUnreadFirstId(): String? {
        var id: String? = null
        for (i in 0 until messageRealmObjects.size) {
            if (!messageRealmObjects[i].isOutgoing && messageRealmObjects[i].isUnread) {
                if (i != 0) {
                    if (messageRealmObjects[i - 1] != null && !messageRealmObjects[i - 1].isUnread)
                        id = messageRealmObjects[i].primary
                    return id
                }
            }
        }
        Log.d("yyy", "id = $id")
        return id
    }

}