package com.xabber.presentation.application.fragments.message

import android.view.View
import com.xabber.data.xmpp.messages.MessageReferenceStorageItem

class MessageVH(
    view: View,
    private val listener: MessageClickListener,
    private val longClickListener: MessageLongClickListener,
    private val fileListener: FileListener?,
): BasicViewHolder(view, null), View.OnClickListener {

    interface FileListener {
        fun onImageClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?)
        fun onFileClick(messagePosition: Int, attachmentPosition: Int, messageUID: String?)
        fun onVoiceClick(
            messagePosition: Int,
            attachmentPosition: Int,
            attachmentId: String?,
            messageUID: String?,
            timestamp: Long?
        )

        fun onFileLongClick(messageReferenceStorageItem: MessageReferenceStorageItem?, caller: View?)
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

    override fun onClick(p0: View?) {

    }
}