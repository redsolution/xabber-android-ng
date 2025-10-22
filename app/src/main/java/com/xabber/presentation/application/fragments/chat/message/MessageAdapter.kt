package com.xabber.presentation.application.fragments.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.message.IncomingMessageVH
import com.xabber.presentation.application.fragments.chat.message.MessageViewHolder
import com.xabber.presentation.application.fragments.chat.message.OutgoingMessageVH
import com.xabber.presentation.application.fragments.chat.message.SystemMessageVH
import com.xabber.utils.isSameDayWith
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

class MessageAdapter(
    private val layoutInflater: LayoutInflater,
    private val listener: MenuItemListener? = null,
    private val onViewClickListener: OnViewClickListener? = null,
    val messages: ArrayList<MessageDto> = ArrayList(),
    private val isGroup: Boolean,
    private val onBindListener: ((MessageDto?) -> Unit)? = null,
    private val realm: Realm, // New: Pass Realm instance
    private val onMessagesUpdated: (List<MessageDto>) -> Unit // New: Callback to notify ChatViewModel
) : RecyclerView.Adapter<MessageViewHolder>() {


    private var collection: RealmResults<MessageStorageItem>? = null
    private var currentList: List<MessageDto> = emptyList()
    private var observingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return currentList.getOrNull(position)?.primary?.hashCode()?.toLong() ?: RecyclerView.NO_ID
    }

    private var firstUnreadMessageID: String? = null
    private val checkedItemIds: MutableList<String> = ArrayList()
    private val TAG = "MessageAdapter"

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

    override fun getItemCount(): Int = currentList.size


    fun startObserving(owner: String, opponent: String, conversationType: String, recyclerView: RecyclerView) {
        stopObserving()
        val query = realm.query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND isDeleted = false",
            owner, opponent, conversationType
        ).sort("sentDate", Sort.ASCENDING)
        collection = query.find()
        observingJob = scope.launch {
            collection!!.asFlow()
                .debounce(600.milliseconds)  // Debounce updates by 300ms to batch rapid changes and reduce UI flicker
                .collect { changes: ResultsChange<MessageStorageItem> ->
                    val newDtos = when (changes) {
                        is InitialResults -> changes.list.mapNotNull { it.toMessageDto() }
                        is UpdatedResults -> changes.list.mapNotNull { it.toMessageDto() }
                    }.sortedBy { it.sentTimestamp }  // Keep sort if sentTimestamp doesn't perfectly align with Realm's sentDate sort

                    withContext(Dispatchers.Main) {
                        // Save current scroll position before update
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                        val firstVisibleView = layoutManager?.findViewByPosition(firstVisiblePosition)
                        val offset = firstVisibleView?.top ?: 0
                        val oldItemCount = currentList.size

                        // Efficiently update via DiffUtil (already optimized for partial changes)
                        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                            override fun getOldListSize(): Int = currentList.size
                            override fun getNewListSize(): Int = newDtos.size
                            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                                return currentList[oldItemPosition].primary == newDtos[newItemPosition].primary
                            }
                            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                                val oldItem = currentList[oldItemPosition]
                                val newItem = newDtos[newItemPosition]
                                return oldItem.messageBody == newItem.messageBody &&
                                        oldItem.sentTimestamp == newItem.sentTimestamp &&
                                        oldItem.isOutgoing == newItem.isOutgoing &&
                                        oldItem.references == newItem.references &&
                                        oldItem.isUnread == newItem.isUnread &&
                                        oldItem.isChecked == newItem.isChecked &&
                                        oldItem.messageSendingState == newItem.messageSendingState &&
                                        oldItem.archivedId == newItem.archivedId
                            }
                        })

                        // Update list and notify
                        currentList = newDtos
                        onMessagesUpdated(newDtos)

                        // Apply updates and handle scroll restoration
                        diffResult.dispatchUpdatesTo(this@MessageAdapter)

                        // If items were inserted (common for new messages at end), adjust scroll to maintain view
                        // This assumes append-only behavior; for general cases, consider always scrolling to a stable key
//                        if (newDtos.size > oldItemCount) {
//                            val insertedCount = newDtos.size - oldItemCount
//                            if (firstVisiblePosition != RecyclerView.NO_POSITION) {
//                                layoutManager?.scrollToPositionWithOffset(
//                                    firstVisiblePosition + insertedCount,
//                                    offset
//                                )
//                            }
//                        }
                    }
                }
        }
    }

    fun stopObserving() {
        observingJob?.cancel()
        observingJob = null
        collection = null
        currentList = emptyList()
        notifyDataSetChanged()
    }

    fun updateAdapter(messageDtoList: List<MessageDto>) {
        Log.v(TAG, "Updating adapter with ${messageDtoList.size} messages")
        val newList = messageDtoList
            .filter { it.primary.isNotEmpty() }
            .distinctBy { it.primary }
            .sortedBy { it.sentTimestamp }
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = messages.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return messages[oldItemPosition].primary == newList[newItemPosition].primary
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = messages[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.messageBody == newItem.messageBody &&
                        oldItem.sentTimestamp == newItem.sentTimestamp &&
                        oldItem.isOutgoing == newItem.isOutgoing &&
                        oldItem.references == newItem.references &&
                        oldItem.isUnread == newItem.isUnread &&
                        oldItem.isChecked == newItem.isChecked &&
                        oldItem.messageSendingState == newItem.messageSendingState &&
                        oldItem.archivedId == newItem.archivedId
            }
        })
        messages.clear()
        messages.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
        notifyUnreadState()
    }


    override fun getItemViewType(position: Int): Int {
        val message = currentList.getOrNull(position) ?: return INCOMING_MESSAGE
        return when {
            message.displayType == MessageDisplayType.System -> SYSTEM_MESSAGE
            message.isOutgoing -> OUTGOING_MESSAGE
            else -> INCOMING_MESSAGE
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when (viewType) {
            OUTGOING_MESSAGE -> OutgoingMessageVH(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_outgoing, parent, false
                ), layoutInflater, listener, onViewClickListener
            )
            INCOMING_MESSAGE -> IncomingMessageVH(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_incoming, parent, false
                ), layoutInflater, listener, onViewClickListener
            )
            SYSTEM_MESSAGE -> SystemMessageVH(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_message_system, parent, false
                ), layoutInflater, listener, onViewClickListener
            )
            else -> throw IllegalStateException("Unsupported view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = currentList.getOrNull(position) ?: return
        Log.v(TAG, "Binding message: primary=${message.primary}, body=${message.messageBody.take(50)}, isOutgoing=${message.isOutgoing}, isUnread=${message.isUnread}, isChecked=${message.isChecked}")
        holder.setIsRecyclable(true)
        holder.messageId = message.primary
        val extraData = MessageVhExtraData(
            isUnread = message.isUnread && (firstUnreadMessageID == null || message.primary == firstUnreadMessageID),
            isChecked = checkedItemIds.contains(message.primary),
            isNeedTail = isMessageNeedTail(position),
            isNeedDate = isMessageNeedDate(position),
            isNeedName = isMessageNeedName(position),
            isGroup = isGroup
        )
        when (holder) {
            is IncomingMessageVH -> holder.bind(message, extraData)
            is OutgoingMessageVH -> holder.bind(message, extraData)
            is SystemMessageVH -> holder.bind(message, extraData)
        }
        onBindListener?.invoke(message)
    }

    private fun isMessageNeedDate(position: Int): Boolean {
        val message = getMessageItem(position) ?: return true
        val previousMessage = getMessageItem(position - 1) ?: return true
        return !message.sentTimestamp.isSameDayWith(previousMessage.sentTimestamp)
    }

    private fun isMessageNeedTail(position: Int): Boolean {
        if (ChatSettingsManager.bottom) {
            val message = getMessageItem(position) ?: return true
            val nextMessage = getMessageItem(position + 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false
            else message.isOutgoing != nextMessage.isOutgoing
        } else {
            val message = getMessageItem(position) ?: return true
            val preMessage = getMessageItem(position - 1) ?: return true
            return if (message.references.size > 0 && message.messageBody.isEmpty()) false
            else message.isOutgoing != preMessage.isOutgoing
        }
    }

    private fun isMessageNeedName(position: Int): Boolean {
        if (!isGroup) return false
        val message = getMessageItem(position) ?: return false
        val preMessage = getMessageItem(position - 1) ?: return true
        return message.isOutgoing != preMessage.isOutgoing || message.opponentJid != preMessage.opponentJid
    }

    fun getMessageItem(position: Int): MessageDto? =
        if (position in 0 until currentList.size) currentList[position] else null

    fun setFirstUnreadMessageId(id: String?) {
        firstUnreadMessageID = id
        notifyUnreadState()
    }


    private fun notifyUnreadState() {
        currentList.forEachIndexed { index, message ->
            if (message.isUnread && (firstUnreadMessageID == null || message.primary == firstUnreadMessageID)) {
                notifyItemChanged(index)
            }
        }
    }

    companion object {
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_MESSAGE = 2
        const val SYSTEM_MESSAGE = 3
    }

    override fun onDetachedFromRecyclerView(parent: RecyclerView) {
        super.onDetachedFromRecyclerView(parent)
        stopObserving()
        scope.cancel()
    }
}