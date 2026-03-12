package com.xabber.presentation.application.fragments.chat.message

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.MediaPlayer
import android.text.util.Linkify
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.HttpFileUploadManager
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.audio.VoiceMessagePresenterManager
import com.xabber.utils.StringUtils.getDateStringForMessage
import com.xabber.utils.StringUtils.getTimeText
import com.xabber.utils.custom.CorrectlyTouchEventTextView
import com.xabber.utils.custom.PlayerVisualizerView
import java.util.*
import java.util.concurrent.TimeUnit

abstract class MessageViewHolder(
    itemView: View,
    private val inflater: LayoutInflater,
    private val menuItemListener: MessageAdapter.MenuItemListener?,
    private val onViewClickListener: MessageAdapter.OnViewClickListener?
) : RecyclerView.ViewHolder(itemView), FilesAdapter.OnFileClickListener {
    var needDate = false
    var date: String? = null
    var isUnread = false
    var messageId: String? = null

    private val context: Context = itemView.context
    private var messageContainer: LinearLayout? = null
    private var balloon: FrameLayout? = null
    private var tail: FrameLayout? = null
    protected var tvMessageText: CorrectlyTouchEventTextView? = null
    private var statusIcon: ImageView? = null
    private var tvTime: TextView? = null
    private val TAG = "MessageViewHolder"

    // Track what content type was last inflated to avoid re-inflation
    private var lastContentType: Int = CONTENT_NONE
    // MediaPlayer reference for proper cleanup on recycle
    private var currentMediaPlayer: MediaPlayer? = null

    // Cached color filters — only 2 variants (incoming/outgoing), allocated once
    private val outgoingColorFilter: PorterDuffColorFilter
    private val incomingColorFilter: PorterDuffColorFilter

    private companion object {
        const val CONTENT_NONE = 0
        const val CONTENT_TEXT = 1
        const val CONTENT_IMAGE = 2
        const val CONTENT_GEO = 3
        const val CONTENT_VOICE = 4
        const val CONTENT_FILES = 5
        const val CONTENT_FILES_TEXT = 6
        const val CONTENT_IMAGE_TEXT = 7
    }

    init {
        balloon = itemView.findViewById(R.id.balloon)
        tail = itemView.findViewById(R.id.tail)
        messageContainer = itemView.findViewById(R.id.message_container)
        tvMessageText = itemView.findViewById(R.id.message_text)
        statusIcon = itemView.findViewById(R.id.message_status_icon)
        tvTime = itemView.findViewById(R.id.message_time)

        val outgoingColor = ContextCompat.getColor(context, R.color.white)
        val incomingColor = ContextCompat.getColor(context, R.color.blue_50)
        outgoingColorFilter = PorterDuffColorFilter(outgoingColor, PorterDuff.Mode.SRC_IN)
        incomingColorFilter = PorterDuffColorFilter(incomingColor, PorterDuff.Mode.SRC_IN)
    }

    /** Called when ViewHolder is recycled — release heavy resources */
    fun onRecycled() {
        releaseMediaPlayer()
    }

    private fun releaseMediaPlayer() {
        try {
            currentMediaPlayer?.release()
        } catch (_: Exception) { }
        currentMediaPlayer = null
    }

    open fun bind(message: MessageStorageItem, vhExtraData: MessageVhExtraData) {
        val images = ArrayList<MessageReferenceStorageItem>()
        val otherFiles = ArrayList<MessageReferenceStorageItem>()

        if (message.references.isNotEmpty()) {
            for (reference in message.references) {
                if (reference.kind_ == "groupchat" || reference.kind_ == "system-message") continue
                val category = FileCategory.determineFileCategory(reference.mimeType ?: "")
                if (category == FileCategory.IMAGE || category == FileCategory.VIDEO) {
                    images.add(reference)
                } else {
                    otherFiles.add(reference)
                }
            }
        }

        val needTail = if (message.references.isNotEmpty()) {
            if (message.references[0].isGeo) false
            else if (message.references[0].isAudioMessage) vhExtraData.isNeedTail
            else if (otherFiles.isEmpty() && message.body.isEmpty()) false else vhExtraData.isNeedTail
        } else vhExtraData.isNeedTail

        val displayType = when {
            message.displayAs_ == "system" || message.conversationType_ == "https://xabber.com/protocol/groups#system-message" -> MessageDisplayType.System
            message.body.isEmpty() && message.references.isNotEmpty() -> MessageDisplayType.Images
            else -> MessageDisplayType.Text
        }

        // Determine what content type this message needs
        val contentType = when {
            displayType == MessageDisplayType.System -> CONTENT_NONE
            message.references.isNotEmpty() && message.references[0].isGeo -> CONTENT_GEO
            message.references.isNotEmpty() && message.references[0].isAudioMessage -> CONTENT_VOICE
            images.isNotEmpty() && message.body.isNotEmpty() -> CONTENT_IMAGE_TEXT
            images.isNotEmpty() -> CONTENT_IMAGE
            otherFiles.isNotEmpty() && message.body.isNotEmpty() -> CONTENT_FILES_TEXT
            otherFiles.isNotEmpty() -> CONTENT_FILES
            message.body.isNotEmpty() -> CONTENT_TEXT
            else -> CONTENT_NONE
        }

        val sameContentType = contentType == lastContentType && messageContainer?.childCount != 0

        if (displayType != MessageDisplayType.System) {
            if (sameContentType && contentType == CONTENT_TEXT) {
                // Text → Text: fast path — update text without re-inflating views
                updateTextInPlace(message)
            } else {
                // All other cases: full rebuild to ensure correct content display.
                // With view type segregation, RecyclerView only recycles matching
                // content types, so this branch is hit less often.
                rebuildContent(message, images, otherFiles)
            }

            setupOnClick(message, vhExtraData.isChecked)
            setupOnLongClick(message.primary, vhExtraData.isChecked)
            setBalloonBackground(message.outgoing, needTail)
            setItemCheckedBackground(vhExtraData.isChecked)
        } else {
            // System message — clear content
            if (lastContentType != CONTENT_NONE) {
                releaseMediaPlayer()
                messageContainer?.removeAllViews()
                balloon?.removeAllViews()
            }
        }

        lastContentType = contentType
        needDate = vhExtraData.isNeedDate
        isUnread = vhExtraData.isUnread
        messageId = message.primary
        date = getDateStringForMessage(message.sentDate)
    }

    /** Full rebuild: remove all views and re-inflate content */
    private fun rebuildContent(
        message: MessageStorageItem,
        images: ArrayList<MessageReferenceStorageItem>,
        otherFiles: ArrayList<MessageReferenceStorageItem>
    ) {
        releaseMediaPlayer()
        messageContainer?.removeAllViews()
        if (message.references.isNotEmpty()) {
            if (message.references[0].isGeo) {
                addGeoLocationBox(message, message.references[0].latitude ?: 0.0, message.references[0].longitude ?: 0.0)
            } else if (message.references[0].isAudioMessage) {
                addVoiceMessageBox(message.references[0].uri!!, message)
            } else {
                if (images.isNotEmpty()) addImageAndVideoBox(message, images)
                if (otherFiles.isNotEmpty()) addFilesBox(message, otherFiles)
            }
        }
        if (message.body.isNotEmpty()) addTextBox(message)
    }

    /** Fast path: update text message content without removing/inflating views */
    private fun updateTextInPlace(message: MessageStorageItem) {
        // Views are cached — no need for findViewById on every bind
        val text = if (message.body.isEmpty()) context.getString(R.string.empty_message) else message.body
        tvMessageText?.text = text
        applyLinksIfNeeded(text)
        setTime(message.sentDate, message.editDate)
        if (statusIcon != null) setStatusIcon(statusIcon!!, message)
    }

    private fun setTime(sentTime: Long, editTime: Long) {
        val date = Date(if (editTime > 0) editTime else sentTime)
        val time = getTimeText(context, date)
        tvTime?.text = if (editTime > 0) context.getString(R.string.edit) + " $time" else time
        tvTime?.setTextColor(ContextCompat.getColor(context, R.color.grey_500))
    }

    private fun setBalloonBackground(isOutgoing: Boolean, needTail: Boolean) {
        val balloonBackground = ContextCompat.getDrawable(
            context,
            if (needTail) ChatSettingsManager.tail else ChatSettingsManager.simple
        )
        val tailBackground = ContextCompat.getDrawable(context, ChatSettingsManager.tailDrawable)
        val colorFilter = if (isOutgoing) outgoingColorFilter else incomingColorFilter
        balloonBackground?.colorFilter = colorFilter
        tailBackground?.colorFilter = colorFilter

        balloon?.background = balloonBackground
        tail?.background = tailBackground
        tail?.isInvisible = !needTail || ChatSettingsManager.messageTypeValue?.rawValue == 2

        if (tail != null && tail!!.isVisible && !ChatSettingsManager.bottom) turnOverTail()
    }

    private fun turnOverTail() {
        balloon?.scaleY = -1f
        tail?.scaleY = -1f
        setAlign()
    }

    private fun setAlign() {
        val layoutParams = tail?.layoutParams as RelativeLayout.LayoutParams
        layoutParams.removeRule(RelativeLayout.ALIGN_BOTTOM)
        layoutParams.addRule(RelativeLayout.ALIGN_TOP, R.id.message_container)
        tail?.layoutParams = layoutParams
    }

    fun animateTailChange(needTail: Boolean, isOutgoing: Boolean) {
        // Animate the balloon background swap
        setBalloonBackground(isOutgoing, needTail)
        // Animate the tail visibility with a fade
        tail?.let { tailView ->
            val targetAlpha = if (needTail && ChatSettingsManager.messageTypeValue?.rawValue != 2) 1f else 0f
            tailView.animate()
                .alpha(targetAlpha)
                .setDuration(150)
                .withEndAction {
                    tailView.isInvisible = targetAlpha == 0f
                    tailView.alpha = 1f // Reset alpha for future reuse
                }
                .start()
        }
    }

    private fun setItemCheckedBackground(isChecked: Boolean) {
        itemView.setBackgroundColor(ContextCompat.getColor(context, if (isChecked) R.color.selected else R.color.transparent))
    }

    private fun addGeoLocationBox(message: MessageStorageItem, latitude: Double, longitude: Double) {
        val geoLocationBox = GeoLocationBuilder()
        val geoLocationView: View = geoLocationBox.inflateView(messageContainer!!)
        geoLocationBox.addGeoLocationBox(geoLocationView, message, latitude, longitude, onViewClickListener)
        messageContainer?.addView(geoLocationView)
    }

    private fun addVoiceMessageBox(path: String, message: MessageStorageItem) {
        val voiceMessageBox = inflater.inflate(R.layout.voice_message_box, messageContainer, false)
        voiceMessageBox.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        messageContainer?.addView(voiceMessageBox)
        val presenter = voiceMessageBox.findViewById<PlayerVisualizerView>(R.id.player_visualizer)
        val button = voiceMessageBox.findViewById<ImageButton>(R.id.btn_play)
        val tvDuration = voiceMessageBox.findViewById<TextView>(R.id.tv_duration)
        setMessageInfo(message)
        val time = HttpFileUploadManager.getVoiceLength(path)
        tvDuration.text = String.format(
            Locale.getDefault(), "%02d:%02d",
            TimeUnit.SECONDS.toMinutes(time),
            TimeUnit.SECONDS.toSeconds(time)
        )
        VoiceMessagePresenterManager.getInstance().sendWaveDataIfSaved(path, presenter)
        releaseMediaPlayer()
        val mediaPlayer = MediaPlayer()
        currentMediaPlayer = mediaPlayer
        var isPlaying = false
        var isPrepared = false

        try {
            mediaPlayer.setDataSource(path)
            mediaPlayer.setOnPreparedListener { isPrepared = true }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare media player for voice message: ${e.message}")
        }

        button?.setOnClickListener {
            try {
                if (!isPrepared) return@setOnClickListener
                if (isPlaying) {
                    mediaPlayer.pause()
                    button.setImageResource(R.drawable.ic_play)
                    isPlaying = false
                } else {
                    mediaPlayer.start()
                    isPlaying = true
                    button.setImageResource(R.drawable.ic_pause)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing/pausing voice message: ${e.message}")
                Toast.makeText(context, R.string.unable_to_play_audio, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addImageAndVideoBox(message: MessageStorageItem, images: ArrayList<MessageReferenceStorageItem>) {
        val builder = ImageGridBuilder()
        val imageGridView: View = builder.inflateView(messageContainer!!, images.size)
        imageGridView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        builder.bindView(imageGridView, message, images)
        messageContainer?.addView(imageGridView)
        val infoStamp = imageGridView.findViewById<LinearLayoutCompat>(R.id.message_info)
        val imageTime = imageGridView.findViewById<TextView>(R.id.tv_image_sending_time)
        val status = imageGridView.findViewById<ImageView>(R.id.iv_image_message_status)
        infoStamp.isVisible = message.body.isEmpty() && message.references.size == images.size
        if (infoStamp.isVisible) {
            setImageTime(imageTime, message)
            setStatusIcon(status, message)
        }
        val image0 = imageGridView.findViewById<ImageView>(R.id.ivImage0)
        val image1 = imageGridView.findViewById<ImageView>(R.id.ivImage1)
        val image2 = imageGridView.findViewById<ImageView>(R.id.ivImage2)
        val image3 = imageGridView.findViewById<ImageView>(R.id.ivImage3)
        val image4 = imageGridView.findViewById<ImageView>(R.id.ivImage4)
        val image5 = imageGridView.findViewById<ImageView>(R.id.ivImage5)

        val onClickListener = View.OnClickListener {
            when (it.id) {
                R.id.ivImage0 -> onViewClickListener?.onImageOrVideoClick(0, messageId!!)
                R.id.ivImage1 -> onViewClickListener?.onImageOrVideoClick(1, messageId!!)
                R.id.ivImage2 -> onViewClickListener?.onImageOrVideoClick(2, messageId!!)
                R.id.ivImage3 -> onViewClickListener?.onImageOrVideoClick(3, messageId!!)
                R.id.ivImage4 -> onViewClickListener?.onImageOrVideoClick(4, messageId!!)
                R.id.ivImage5 -> onViewClickListener?.onImageOrVideoClick(5, messageId!!)
            }
        }
        image0?.setOnClickListener(onClickListener)
        image1?.setOnClickListener(onClickListener)
        image2?.setOnClickListener(onClickListener)
        image3?.setOnClickListener(onClickListener)
        image4?.setOnClickListener(onClickListener)
        image5?.setOnClickListener(onClickListener)
    }

    private fun setImageTime(imageTime: TextView, message: MessageStorageItem) {
        val date = Date(if (message.editDate > 0) message.editDate else message.sentDate)
        val time = getTimeText(context, date)
        imageTime.text = if (message.editDate > 0) "edit $time" else time
        val currentTime = System.currentTimeMillis()
        val sentTime = message.sentDate
        val isSuspicious = sentTime > currentTime + 3_600_000 || sentTime < currentTime - 365L * 24 * 60 * 60 * 1000
        if (isSuspicious) {
            Log.w(
                TAG,
                "Suspicious image timestamp for messageId=${message.primary}: sentTimestamp=$sentTime, " +
                        "currentTime=$currentTime, date=$date, displayed=$time"
            )
            imageTime.setTextColor(ContextCompat.getColor(context, R.color.red_500))
            imageTime.text = context.getString(R.string.invalid_timestamp)
        } else {
            imageTime.setTextColor(ContextCompat.getColor(context, R.color.black))
        }
    }

    private fun addFilesBox(message: MessageStorageItem, files: ArrayList<MessageReferenceStorageItem>) {
        val filesBox = inflater.inflate(R.layout.files_box, messageContainer, false)
        filesBox.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        messageContainer?.addView(filesBox)
        val adapter = FilesAdapter(files, message.sentDate, this)
        val recyclerView = filesBox.findViewById<RecyclerView>(R.id.file_list_rv)
        recyclerView.adapter = adapter
        adapter.submitList(files)
        setMessageInfo(message)
    }

    private fun addTextBox(message: MessageStorageItem) {
        val textBox = inflater.inflate(R.layout.text_box, messageContainer, false)
        textBox.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        messageContainer?.addView(textBox)
        setMessageText(message.body)
        setMessageInfo(message)
    }

    private fun setMessageText(text: String) {
        tvMessageText = messageContainer?.findViewById(R.id.message_text)
        val displayText = if (text.isEmpty()) context.getString(R.string.empty_message) else text
        tvMessageText?.text = displayText
        applyLinksIfNeeded(displayText)
        tvMessageText?.movementMethod = CorrectlyTouchEventTextView.LocalLinkMovementMethod
    }

    /** Only run Linkify regex when the text likely contains a URL */
    private fun applyLinksIfNeeded(text: String) {
        val tv = tvMessageText ?: return
        if (text.contains("://") || text.contains("www.") || text.contains("@")) {
            Linkify.addLinks(tv, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        }
    }

    private fun setMessageInfo(message: MessageStorageItem) {
        // Re-find views from messageContainer since they're inside dynamically inflated content
        tvTime = messageContainer?.findViewById(R.id.message_time)
        statusIcon = messageContainer?.findViewById(R.id.message_status_icon)
        setTime(message.sentDate, message.editDate)
        if (statusIcon != null) setStatusIcon(statusIcon!!, message)
    }

    private fun setStatusIcon(statusIcon: ImageView, message: MessageStorageItem) {
        statusIcon.isVisible = message.outgoing && message.state != MessageSendingState.Uploading
        if (statusIcon.isVisible) {
            MessageDeliveryStatusHelper.setupStatusImageView(message, statusIcon)
        }
    }

    private fun setupOnClick(message: MessageStorageItem, isChecked: Boolean) {
        itemView.setOnClickListener {
            if (Check.getSelectedMode()) {
                onViewClickListener?.checkItem(!isChecked, message.primary)
            } else {
                if (menuItemListener != null) {
                    val popup = PopupMenu(it.context, it, Gravity.START)
                    popup.setForceShowIcon(true)
                    popup.inflate(if (message.outgoing) R.menu.popup_menu_message_outgoing else R.menu.popup_menu_message_incoming)
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.copy -> menuItemListener.copyText(message.body)
                            R.id.pin -> menuItemListener.pinMessage(message)
                            R.id.forward -> menuItemListener.forwardMessage(message)
                            R.id.reply -> menuItemListener.replyMessage(message)
                            R.id.delete_message -> menuItemListener.deleteMessage(message.primary)
                            R.id.edit -> menuItemListener.editMessage(message.primary, message.body)
                        }
                        true
                    }
                    popup.show()
                }
            }
        }
    }

    private fun setupOnLongClick(messagePrimary: String, isChecked: Boolean) {
        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) onViewClickListener?.onLongClick(messagePrimary)
            else onViewClickListener?.checkItem(!isChecked, messagePrimary)
            true
        }
    }

    override fun onFileClick(path: String) {
        val contentResolver = XabberApplication.applicationContext().contentResolver
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(path.toUri(), contentResolver.getType(path.toUri()))
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file: ${e.message}")
            Toast.makeText(context, context.resources.getString(R.string.unable_to_open_file), Toast.LENGTH_SHORT).show()
        }
    }
}