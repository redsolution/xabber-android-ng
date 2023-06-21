package com.xabber.presentation.application.fragments.chat.message

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.utils.StringUtils
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.dp
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.*

class OutgoingMessageVH internal constructor(
    private val listener: MessageAdapter.Listener?,
    itemView: View?, messageListener: MessageClickListener?,
    longClickListener: MessageLongClickListener?,
    private val fileListener: FileListener?
) : MessageVH(itemView!!, messageListener!!, longClickListener!!, fileListener) {
    lateinit var context: Context
    lateinit var canvas: Canvas
    lateinit var bitmap: Bitmap
    lateinit var mapView: MapView
    lateinit var mapImage: ImageView


    val im = {

        mapImage.setImageBitmap(bitmap)
    }

    override fun bind(message: MessageDto, extraData: MessageVhExtraData) {
        super.bind(message, extraData)
        context = itemView.context
        val inflater = LayoutInflater.from(context)
        val needTail: Boolean =
            if (message.references.isNotEmpty() && message.messageBody.isEmpty()) false else extraData.isNeedTail

        setBalloonBackground(needTail)
        setItemCheckedBackground(message.isChecked)

        balloon?.removeAllViews()
        if (message.kind != null) {

        }
        if (message.references.size > 0) {
            if (message.references[0].isGeo) addGeoLocationBox(
                inflater,
                message.references[0].latitude,
                message.references[0].longitude
            )
            else {
                val builder = ImageGridBuilder()
                val imageGridView: View =
                    builder.inflateView(messageContainer!!, message.references.size)
                builder.bindView(imageGridView, message, message.references, this)
                messageContainer?.addView(imageGridView)
                val infoStamp = imageGridView.findViewById<LinearLayoutCompat>(R.id.message_info)
                val imageTime = imageGridView.findViewById<TextView>(R.id.tv_image_sending_time)
                val status = imageGridView.findViewById<ImageView>(R.id.im_image_message_status)
                infoStamp.isVisible = message.messageBody.isEmpty()
                if (infoStamp.isVisible) {
                    val date = Date(message.sentTimestamp)
                    val time = StringUtils.getTimeText(context, date)
                    imageTime.text = if (message.editTimestamp > 0) "edit $time" else time
                    setStatusIcon(status, message)
                }
            }
        }
        if (message.messageBody.isNotEmpty()) addTextBox(inflater, message)

        setupOnClick(message)
        setupOnLongClick(message.primary, message.isChecked)
    }

    private fun setBalloonBackground(needTail: Boolean) {
        val balloonBackground = ContextCompat.getDrawable(
            context,
            if (needTail) ChatSettingsManager.tail else
                ChatSettingsManager.simple
        )

        val tailBackground = ContextCompat.getDrawable(
            context, ChatSettingsManager.hvost
        )

        val colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
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
        Log.d("test", "$latitude, $longitude")
        Configuration.getInstance().load(context, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))
        val locationBox = inflater.inflate(
            R.layout.geo_location_box,
            messageContainer,
            false
        )
        messageContainer?.addView(locationBox)
        mapImage = locationBox.findViewById(R.id.map_image)
        val shape = locationBox.findViewById<ShapeOfView>(R.id.geo_shape)
        val radius =
            if (ChatSettingsManager.cornerValue > 4) (ChatSettingsManager.cornerValue - 4) else 1
        //  val radii = floatArrayOf(radius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat(), radius.dp.toFloat())

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

        mapView = MapView(context).apply {
        val location = GeoPoint(latitude, longitude)
      controller.setCenter(location)
        setTileSource(TileSourceFactory.MAPNIK)
       isTilesScaledToDpi = true
          //  controller.setZoom(5.0)
        }
        bitmap = Bitmap.createBitmap(200.dp, 200.dp, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        mapView.draw(canvas)
        mapImage.setImageBitmap(bitmap)
        handler.postDelayed(im, 2000)
        setLocationOnClick(locationBox, latitude, longitude)
    }

    val handler = Handler(Looper.getMainLooper())

    private fun setLocationOnClick(view: View, latitude: Double, longitude: Double) {
        view.setOnClickListener {
            itemView.context.startActivity(
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                }
            )
        }
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
                listener?.checkItem(!message.isChecked, message.primary)
            } else {
                val popup = PopupMenu(it.context, it, Gravity.START)
                popup.setForceShowIcon(true)
                popup.inflate(R.menu.popup_menu_message_outgoing)

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = message.messageBody
                            listener?.copyText(text)
                        }
                        R.id.pin -> {
                            listener?.pinMessage(message)
                        }
                        R.id.forward -> {
                            listener?.forwardMessage(message)
                        }
                        R.id.reply -> {
                            listener?.replyMessage(message)
                        }
                        R.id.delete_message -> {
                            listener?.deleteMessage(message.primary)
                        }
                        R.id.edit -> {
                            listener?.editMessage(message.primary, message.messageBody)
                        }
                    }
                    true
                }
                popup.show()
            }
        }
    }

    private fun setupOnLongClick(messagePrimary: String, isChecked: Boolean) {
        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener?.onLongClick(messagePrimary)
            else {
                listener?.checkItem(!isChecked, messagePrimary)
            }
            true
        }
    }

}
