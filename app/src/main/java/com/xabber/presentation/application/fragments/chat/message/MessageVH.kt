package com.xabber.presentation.application.fragments.chat.message

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.audio.VoiceMessagePresenterManager
import com.xabber.utils.StringUtils
import com.xabber.utils.StringUtils.getDateStringForMessage
import com.xabber.utils.custom.CorrectlyTouchEventTextView
import com.xabber.utils.custom.PlayerVisualizerView
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.dp
import org.osmdroid.views.overlay.Polygon.OnClickListener
import java.util.*
import kotlin.collections.ArrayList

abstract class MessageVH(
    itemView: View,
    private val menuItemListener: MessageAdapter.MenuItemListener?,
    private val onViewClickListener: MessageAdapter.OnViewClickListener?
) : RecyclerView.ViewHolder(itemView) {
    var needDate = false
    var date: String? = null
    var isUnread = false
    var messageId: String? = null

    private val context: Context = itemView.context
    protected var messageContainer: LinearLayout? = null
    protected var balloon: FrameLayout? = null
    protected var tail: FrameLayout? = null
    protected var tvMessageText: CorrectlyTouchEventTextView? = null
    protected var statusIcon: ImageView? = null
    protected var tvTime: TextView? = null

    interface MessageClickListener {
        fun onMessageClick(caller: View, position: Int)
    }

    interface MessageLongClickListener {
        fun onLongMessageClick(position: Int)
    }

    open fun bind(message: MessageDto, vhExtraData: MessageVhExtraData) {
        val inflater = LayoutInflater.from(context)
        var needTail: Boolean =
            if (message.references.isNotEmpty() && message.messageBody.isEmpty()) false else vhExtraData.isNeedTail

        initViews()

        if (message.displayType != MessageDisplayType.System) {
            balloon?.removeAllViews()

            if (message.references.size > 0) {
                if (message.references[0].isGeo) addGeoLocationBox(
                    inflater,
                    message.references[0].latitude,
                    message.references[0].longitude
                )
                else if (message.references[0].isAudioMessage) addVoiceMessageBox(inflater, message.references[0].uri!!)
                else {
                    val images = ArrayList<MessageReferenceDto>()
                    val otherFiles = ArrayList<MessageReferenceDto>()
                    for (reference in message.references) {
                        val category = FileCategory.determineFileCategory(reference.mimeType)
                        if (category == FileCategory.IMAGE || category == FileCategory.VIDEO)
                            images.add(reference)
                        else otherFiles.add(reference)
                    }
                   if (images.isNotEmpty()) addImageAndVideoBox(message, images)
                    if (otherFiles.isNotEmpty()) { addFilesBox(inflater, message, otherFiles)
                    needTail = true}
                }
            }
            if (message.messageBody.isNotEmpty()) addTextBox(inflater, message)

            setupOnClick(message)
            setupOnLongClick(message.primary, message.isChecked)
            setBalloonBackground(message.isOutgoing, needTail)
            setItemCheckedBackground(message.isChecked)
        }
        if (tvTime != null) {
            val dates = Date(message.sentTimestamp)
            val time = StringUtils.getTimeText(itemView.context, dates)
            tvTime?.text = if (message.editTimestamp > 0) "edit $time" else time
        }
        needDate = vhExtraData.isNeedDate
        date = getDateStringForMessage(message.sentTimestamp)
    }

    private fun setBalloonBackground(isOutgoing: Boolean, needTail: Boolean) {
        val balloonBackground = ContextCompat.getDrawable(
            context,
            if (needTail) ChatSettingsManager.tail else
                ChatSettingsManager.simple
        )

        val tailBackground = ContextCompat.getDrawable(
            context, ChatSettingsManager.hvost
        )

        val colorBackground =
            ContextCompat.getColor(context, if (isOutgoing) R.color.white else R.color.blue_100)
        val colorFilter = PorterDuffColorFilter(
            colorBackground,
            PorterDuff.Mode.SRC_IN
        )
        balloonBackground?.colorFilter = colorFilter
        tailBackground?.colorFilter = colorFilter

        balloon?.background = balloonBackground
        tail?.background = tailBackground

        tail?.isInvisible = !needTail || ChatSettingsManager.messageTypeValue?.rawValue == 2

        if (tail != null)
            if (tail!!.isVisible && !ChatSettingsManager.bottom) turnOverTail()
    }

    private fun turnOverTail() {
        balloon?.scaleY = -1f
        tail?.scaleY = -1f
        setAlign()
    }

    private fun setAlign() {
        val layoutParams = tail?.layoutParams as RelativeLayout.LayoutParams
        layoutParams.removeRule(RelativeLayout.ALIGN_BOTTOM)
        layoutParams.addRule(
            RelativeLayout.ALIGN_TOP,
            R.id.message_container
        )
        tail?.layoutParams = layoutParams
    }

    private fun setItemCheckedBackground(isChecked: Boolean) {
        if (isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(
            R.color.transparent
        )
    }

    private fun addGeoLocationBox(
        inflater: LayoutInflater,
        latitude: Double,
        longitude: Double
    ) {
        val locationBox = inflater.inflate(
            R.layout.geo_location_box,
            messageContainer,
            false
        )
        messageContainer?.addView(locationBox)
        val mapImage = locationBox.findViewById<ImageView>(R.id.map_image)
        val shape = locationBox.findViewById<ShapeOfView>(R.id.geo_shape)
        val radius =
            if (ChatSettingsManager.cornerValue > 4) (ChatSettingsManager.cornerValue - 4) else 1
        val cornerRadii = floatArrayOf(
            radius.dp.toFloat(),
            radius.dp.toFloat(),
            radius.dp.toFloat(),
            radius.dp.toFloat(),
            radius.dp.toFloat(),
            radius.dp.toFloat(),
            radius.dp.toFloat(),
            radius.dp.toFloat()
        )
        val sh = ShapeDrawable(RoundRectShape(cornerRadii, null, null))
        shape.setDrawable(sh)

//        mapView = MapView(context).apply {
//            val location = GeoPoint(latitude, longitude)
//            controller.setCenter(location)
//            setTileSource(TileSourceFactory.MAPNIK)
//            isTilesScaledToDpi = true
//            //  controller.setZoom(5.0)
//        }
//        bitmap = Bitmap.createBitmap(200.dp, 200.dp, Bitmap.Config.ARGB_8888)
//        canvas = Canvas(bitmap)
//        mapView.draw(canvas)
//        mapImage.setImageBitmap(bitmap)
//        handler.postDelayed(im, 2000)
        locationBox.setOnClickListener { onViewClickListener?.onLocationClick(latitude, longitude) }
    }

    private fun addVoiceMessageBox(inflater: LayoutInflater, path: String) {
        val voiceMessageBox = inflater.inflate(
            R.layout.voice_message_box,
            messageContainer,
            false
        )
        messageContainer?.addView(voiceMessageBox)
val presenter = messageContainer?.findViewById<PlayerVisualizerView>(R.id.player_visualizer)
        VoiceMessagePresenterManager.getInstance().sendWaveDataIfSaved(path, presenter)
    }

    private fun addImageAndVideoBox(
        message: MessageDto,
        images: ArrayList<MessageReferenceDto>
    ) {
        val builder = ImageGridBuilder()
        val imageGridView: View =
            builder.inflateView(messageContainer!!, images.size)
        builder.bindView(imageGridView, message, images)
        messageContainer?.addView(imageGridView)
        val infoStamp =
            imageGridView.findViewById<LinearLayoutCompat>(R.id.message_info)
        val imageTime = imageGridView.findViewById<TextView>(R.id.tv_image_sending_time)
        val status = imageGridView.findViewById<ImageView>(R.id.im_image_message_status)
        infoStamp.isVisible = message.messageBody.isEmpty()
        if (infoStamp.isVisible) {
            val date = Date(message.sentTimestamp)
            val time = StringUtils.getTimeText(context, date)
            imageTime.text = if (message.editTimestamp > 0) "edit $time" else time
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
                else -> null
            }
        }
        image0?.setOnClickListener(onClickListener)
        image1?.setOnClickListener(onClickListener)
        image2?.setOnClickListener(onClickListener)
        image3?.setOnClickListener(onClickListener)
        image4?.setOnClickListener(onClickListener)
        image5?.setOnClickListener(onClickListener)
        }

    private fun addFilesBox(inflater: LayoutInflater, message: MessageDto, files: ArrayList<MessageReferenceDto>) {
        val filesBox = inflater.inflate(
            R.layout.files_box,
            messageContainer,
            false
        )
        messageContainer?.addView(filesBox)
        val adapter = FilesAdapter(files, message.sentTimestamp)
        val recyclerView = filesBox.findViewById<RecyclerView>(R.id.file_list_rv)
        recyclerView.adapter = adapter
        adapter.submitList(files)
    }

    private fun addTextBox(inflater: LayoutInflater, message: MessageDto) {
        val textBox = inflater.inflate(
            R.layout.text_box,
            messageContainer,
            false
        )
        messageContainer?.addView(textBox)
        setMessageText(message.messageBody)
        setMessageInfo(message)
    }

    private fun setMessageText(text: String) {
        tvMessageText = itemView.findViewById(R.id.message_text)
        tvMessageText?.text = text
        tvMessageText?.movementMethod = CorrectlyTouchEventTextView.LocalLinkMovementMethod
    }

    private fun setMessageInfo(message: MessageDto) {
        tvTime = itemView.findViewById(R.id.message_time)
        statusIcon = itemView.findViewById(R.id.message_status_icon)
        setTime(message.sentTimestamp, message.editTimestamp)
        if (statusIcon != null) setStatusIcon(statusIcon!!, message)
    }

    private fun setTime(sentTime: Long, editTime: Long) {
        val date = Date(sentTime)
        val time = StringUtils.getTimeText(itemView.context, date)
        tvTime?.text = if (editTime > 0) "edit $time" else time
    }

    private fun setStatusIcon(statusIcon: ImageView, messageDto: MessageDto) {
        statusIcon.isVisible = true
        if (messageDto.messageSendingState === MessageSendingState.Uploading) {
            statusIcon.isVisible = false
        } else {
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageDto, statusIcon
            )
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageDto, statusIcon
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
                            R.id.copy -> {
                                val text = message.messageBody
                                menuItemListener.copyText(text)
                            }
                            R.id.pin -> {
                                menuItemListener.pinMessage(message)
                            }
                            R.id.forward -> {
                                menuItemListener.forwardMessage(message)
                            }
                            R.id.reply -> {
                                menuItemListener.replyMessage(message)
                            }
                            R.id.delete_message -> {
                                menuItemListener.deleteMessage(message.primary)
                            }
                            R.id.edit -> {
                                menuItemListener.editMessage(message.primary, message.messageBody)
                            }
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
            else {
                onViewClickListener?.checkItem(!isChecked, messagePrimary)
            }
            true
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

}
