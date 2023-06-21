package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.ReferenceRealmObject
import com.xabber.utils.StringUtils
import com.xabber.utils.StringUtils.getDateStringForMessage
import com.xabber.utils.custom.CorrectlyTouchEventTextView
import com.xabber.utils.custom.CustomFlexboxLayout
import java.util.*

open class MessageVH(
    itemView: View,
    private val listener: MessageClickListener,
    private val longClickListener: MessageLongClickListener,
    private val fileListener: FileListener?
) : BasicMessageVH(itemView), View.OnClickListener,
    View.OnLongClickListener {

    var isUnread = false
    var messageId: String? = null

    protected var messageContainer: LinearLayout? = null
    protected var tvMessageText: CorrectlyTouchEventTextView? = null
    protected var balloon: FrameLayout? = null
    protected var tail: FrameLayout? = null
    protected var statusIcon: ImageView? = null
    protected var messageInfo: View? = null
    var textBox: CustomFlexboxLayout? = null
    var tvTime: TextView? = null

    interface FileListener {
        fun onImageClick(messagePosition: Int, position: Int, id: String)

        fun onFileClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?)

        fun onVoiceClick(
            messagePosition: Int,
            attachmentPosition: Int,
            attachmentId: String?,
            messageUID: String?,
            timestamp: Long?
        )

        fun onFileLongClick(referenceRealmObject: ReferenceRealmObject?, caller: View?)

        fun onDownloadCancel()

        fun onUploadCancel()

        fun onDownloadError(error: String?)
    }

    interface MessageClickListener {
        fun onMessageClick(caller: View, position: Int)
    }

    interface MessageLongClickListener {
        fun onLongMessageClick(position: Int)
    }

    open fun bind(messageDto: MessageDto, vhExtraData: MessageVhExtraData) {
        initViews()
        if (tvTime != null) {
            val dates = Date(messageDto.sentTimestamp)
            val time = StringUtils.getTimeText(itemView.context, dates)
            tvTime?.text = if (messageDto.editTimestamp > 0) "edit $time" else time
        }
        // messageTextTv.movementMethod = CorrectlyTouchEventTextView.LocalLinkMovementMethod

        needDate = vhExtraData.isNeedDate
        date = getDateStringForMessage(messageDto.sentTimestamp)

        // setup CHECKED
        if (vhExtraData.isChecked) {
            itemView.setBackgroundColor(
                ContextCompat.getColor(itemView.context, R.color.message_selected)
            )
        } else {
            itemView.background = null
        }
    }

    private fun initViews() {
        balloon = itemView.findViewById(R.id.balloon)
        tail = itemView.findViewById(R.id.tail)
        messageContainer = itemView.findViewById(R.id.message_container)
        tvMessageText = itemView.findViewById(R.id.message_text)
        statusIcon = itemView.findViewById(R.id.message_status_icon)
        tvTime = itemView.findViewById(R.id.message_time)
    }

    override fun onClick(view: View) {
        val adapterPosition = absoluteAdapterPosition
        if (adapterPosition == RecyclerView.NO_POSITION) {
            return
        }
        when (view.id) {
            R.id.ivImage0 -> fileListener?.onImageClick(adapterPosition, 0, messageId!!)
            R.id.ivImage1 -> fileListener?.onImageClick(adapterPosition, 1, messageId!!)
            R.id.ivImage2 -> fileListener?.onImageClick(adapterPosition, 2, messageId!!)
            R.id.ivImage3 -> fileListener?.onImageClick(adapterPosition, 3, messageId!!)
            R.id.ivImage4 -> fileListener?.onImageClick(adapterPosition, 4, messageId!!)
            R.id.ivImage5 -> fileListener?.onImageClick(adapterPosition, 5, messageId!!)
            //   R.id.ivCancelUpload -> fileListener?.onUploadCancel()
            else -> listener.onMessageClick(messageContainer!!, adapterPosition)
        }
    }

    override fun onLongClick(view: View): Boolean {
        val adapterPosition = absoluteAdapterPosition
        return if (adapterPosition == RecyclerView.NO_POSITION) {
            false
        } else {
            longClickListener.onLongMessageClick(adapterPosition)
            true
        }
    }

}
