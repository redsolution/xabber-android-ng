package com.xabber.presentation.application.fragments.chat.message

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.MediaPlayer
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
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.HttpFileUploadManager
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.audio.VoiceMessagePresenterManager
import com.xabber.utils.StringUtils
import com.xabber.utils.StringUtils.getDateStringForMessage
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

    init {
        balloon = itemView.findViewById(R.id.balloon)
        tail = itemView.findViewById(R.id.tail)
        messageContainer = itemView.findViewById(R.id.message_container)
        tvMessageText = itemView.findViewById(R.id.message_text)
        statusIcon = itemView.findViewById(R.id.message_status_icon)
        tvTime = itemView.findViewById(R.id.message_time)
        setIsRecyclable(true)
    }

    open fun bind(message: MessageDto, vhExtraData: MessageVhExtraData) {
        setIsRecyclable(true) // Reset recyclability at the start of binding
        messageContainer?.removeAllViews()
        balloon?.removeAllViews()

        val images = ArrayList<MessageReferenceDto>()
        val otherFiles = ArrayList<MessageReferenceDto>()

        if (message.references.isNotEmpty()) {
            for (reference in message.references) {
                val category = FileCategory.determineFileCategory(reference.mimeType)
                if (category == FileCategory.IMAGE || category == FileCategory.VIDEO)
                    images.add(reference)
                else otherFiles.add(reference)
            }
        }

        val needTail = if (message.references.isNotEmpty()) {
            if (message.references[0].isGeo) false
            else if (message.references[0].isVoiceMessage) vhExtraData.isNeedTail
            else if (otherFiles.isEmpty() && message.messageBody.isEmpty()) false else vhExtraData.isNeedTail
        } else vhExtraData.isNeedTail

        if (message.displayType != MessageDisplayType.System) {
            if (message.references.size > 0) {
                if (message.references[0].isGeo) {
                    setIsRecyclable(false) // Prevent recycling for geo messages
                    addGeoLocationBox(message, message.references[0].latitude, message.references[0].longitude)
                } else if (message.references[0].isVoiceMessage) {
                    setIsRecyclable(false) // Prevent recycling for voice messages
                    addVoiceMessageBox(message.references[0].uri!!, message)
                } else {
                    setIsRecyclable(true) // Ensure recyclability for other types
                    if (images.isNotEmpty()) addImageAndVideoBox(message, images)
                    if (otherFiles.isNotEmpty()) addFilesBox(message, otherFiles)
                }
            } else {
                setIsRecyclable(true) // Ensure recyclability for text messages
            }
            if (message.messageBody.isNotEmpty()) addTextBox(message)

            setupOnClick(message)
            setupOnLongClick(message.primary, message.isChecked)
            setBalloonBackground(message.isOutgoing, needTail)
            setItemCheckedBackground(message.isChecked)
        }
        needDate = vhExtraData.isNeedDate
        isUnread = vhExtraData.isUnread
        messageId = message.primary
        date = getDateStringForMessage(message.sentTimestamp)


        itemView.post {
            Log.d(
                TAG,
                "Binding: primary=${message.primary}, isChecked=${message.isChecked}, " +
                        "textLines=${tvMessageText?.lineCount ?: 0}, itemHeight=${itemView.height}, " +
                        "containerHeight=${messageContainer?.height ?: 0}, sentTimestamp=${message.sentTimestamp}, " +
                        "displayedDate=$date, isUnread=$isUnread"
            )
        }
    }

    private fun setTime(sentTime: Long, editTime: Long) {
        val date = Date(if (editTime > 0) editTime else sentTime)
        val time = StringUtils.getTimeText(context, date)
        tvTime?.text = if (editTime > 0) context.getString(R.string.edit) + " $time" else time
        tvTime?.setTextColor(ContextCompat.getColor(context, R.color.black))

    }

    private fun setBalloonBackground(isOutgoing: Boolean, needTail: Boolean) {
        val balloonBackground = ContextCompat.getDrawable(
            context,
            if (needTail) ChatSettingsManager.tail else ChatSettingsManager.simple
        )
        val tailBackground = ContextCompat.getDrawable(context, ChatSettingsManager.tailDrawable)
        val colorBackground = ContextCompat.getColor(context, if (isOutgoing) R.color.white else R.color.blue_100)
        val colorFilter = PorterDuffColorFilter(colorBackground, PorterDuff.Mode.SRC_IN)
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

    private fun setItemCheckedBackground(isChecked: Boolean) {
        itemView.setBackgroundColor(ContextCompat.getColor(context, if (isChecked) R.color.selected else R.color.transparent))
    }

    private fun addGeoLocationBox(message: MessageDto, latitude: Double, longitude: Double) {
        val geoLocationBox = GeoLocationBuilder()
        val geoLocationView: View = geoLocationBox.inflateView(messageContainer!!)
        geoLocationBox.addGeoLocationBox(geoLocationView, message, latitude, longitude, onViewClickListener)
        messageContainer?.addView(geoLocationView)
    }

    private fun addVoiceMessageBox(path: String, message: MessageDto) {
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
        val mediaPlayer = MediaPlayer()
        var isPlaying = false

        try {
            mediaPlayer.setDataSource(path)
            mediaPlayer.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare media player for voice message: ${e.message}")
            Toast.makeText(context, R.string.unable_to_play_audio, Toast.LENGTH_SHORT).show()
        }

        button?.setOnClickListener {
            try {
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

    private fun addImageAndVideoBox(message: MessageDto, images: ArrayList<MessageReferenceDto>) {
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
        infoStamp.isVisible = message.messageBody.isEmpty() && message.references.size == images.size
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

    private fun setImageTime(imageTime: TextView, message: MessageDto) {
        val date = Date(if (message.editTimestamp > 0) message.editTimestamp else message.sentTimestamp)
        val time = StringUtils.getTimeText(context, date)
        imageTime.text = if (message.editTimestamp > 0) "edit $time" else time
        // Reuse validation for image timestamp
        val currentTime = System.currentTimeMillis()
        val sentTime = message.sentTimestamp
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

    private fun addFilesBox(message: MessageDto, files: ArrayList<MessageReferenceDto>) {
        val filesBox = inflater.inflate(R.layout.files_box, messageContainer, false)
        filesBox.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        messageContainer?.addView(filesBox)
        val adapter = FilesAdapter(files, message.sentTimestamp, this)
        val recyclerView = filesBox.findViewById<RecyclerView>(R.id.file_list_rv)
        recyclerView.adapter = adapter
        adapter.submitList(files)
        setMessageInfo(message)
    }

    private fun addTextBox(message: MessageDto) {
        val textBox = inflater.inflate(R.layout.text_box, messageContainer, false)
        textBox.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        messageContainer?.addView(textBox)
        setMessageText(message.messageBody)
        setMessageInfo(message)
    }

    private fun setMessageText(text: String) {
        tvMessageText = itemView.findViewById(R.id.message_text)
        tvMessageText?.text = if (text.isEmpty()) context.getString(R.string.empty_message) else text
        tvMessageText?.movementMethod = CorrectlyTouchEventTextView.LocalLinkMovementMethod
        tvMessageText?.post {

        }
    }

    private fun setMessageInfo(message: MessageDto) {
        tvTime = itemView.findViewById(R.id.message_time)
        statusIcon = itemView.findViewById(R.id.message_status_icon)
        setTime(message.sentTimestamp, message.editTimestamp)
        if (statusIcon != null) setStatusIcon(statusIcon!!, message)
    }

    private fun setStatusIcon(statusIcon: ImageView, messageDto: MessageDto) {
        statusIcon.isVisible = messageDto.isOutgoing && messageDto.messageSendingState != MessageSendingState.Uploading
        if (statusIcon.isVisible) {
            MessageDeliveryStatusHelper.setupStatusImageView(messageDto, statusIcon)
            Log.d(
                TAG,
                "Set status icon for messageId=$messageId: state=${messageDto.messageSendingState}"
            )
        }
    }

    private fun setupOnClick(message: MessageDto) {
        itemView.setOnClickListener {
            if (Check.getSelectedMode()) {
                onViewClickListener?.checkItem(!message.isChecked, message.primary)
            } else {
                if (menuItemListener != null) {
                    val popup = PopupMenu(it.context, it, Gravity.START)
                    popup.setForceShowIcon(true)
                    popup.inflate(if (message.isOutgoing) R.menu.popup_menu_message_outgoing else R.menu.popup_menu_message_incoming)
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.copy -> menuItemListener.copyText(message.messageBody)
                            R.id.pin -> menuItemListener.pinMessage(message)
                            R.id.forward -> menuItemListener.forwardMessage(message)
                            R.id.reply -> menuItemListener.replyMessage(message)
                            R.id.delete_message -> menuItemListener.deleteMessage(message.primary)
                            R.id.edit -> menuItemListener.editMessage(message.primary, message.messageBody)
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