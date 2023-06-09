package com.xabber.presentation.application.fragments.test

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.View.OnAttachStateChangeListener
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.application.fragments.chat.message.MessageDeliveryStatusHelper
import com.xabber.presentation.application.fragments.chat.message.XMessageVH
import com.xabber.utils.StringUtils
import com.xabber.utils.custom.CorrectlyTouchEventTextView
import com.xabber.utils.custom.ShapeOfView
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.util.*
import kotlin.collections.ArrayList

class XOutgoingMessageVH internal constructor(
    private val listener: MessageAdapter.Listener?,
    itemView: View?, messageListener: MessageClickListener?,
    longClickListener: MessageLongClickListener?,
    val fileListener: FileListener?, @StyleRes appearance: Int
) : XMessageVH(itemView!!, messageListener!!, longClickListener!!, fileListener, appearance) {
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun bind(message: MessageDto, extraData: MessageVhExtraData) {
        super.bind(message, extraData)

        val context: Context = itemView.context
        var needTail: Boolean = extraData.isNeedTail

        val balloon = itemView.findViewById<FrameLayout>(R.id.balloon)
        val messageBalloon = itemView.findViewById<LinearLayout>(R.id.message_balloon)
        val tail = itemView.findViewById<FrameLayout>(R.id.tail)
        val backgroundGroup = itemView.findViewById<Group>(R.id.background_group)

        Log.d(
            "fff",
            "message.references.isNotEmpty() = ${message.references.isNotEmpty()}, message.messageBody.isEmpty() = '${message.messageBody.isEmpty()}'"
        )
        if (message.references.isNotEmpty() && message.messageBody.isEmpty()) needTail = false

        // checked background
        if (message.isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(
            R.color.transparent
        )


        // text
        messageTextTv.text = message.messageBody

        // time
        val date = Date(message.sentTimestamp)
        val time = StringUtils.getTimeText(context, date)
        messageTime.text = if (message.editTimestamp > 0) "edit $time" else time


        // background
        val balloonBackground = ContextCompat.getDrawable(
            context,
            if (needTail) MessageChanger.tail else
                MessageChanger.simple
        )

        val tailBackground = ContextCompat.getDrawable(
            context, MessageChanger.hvost
        )
        balloonBackground?.setColorFilter(
            ContextCompat.getColor(context, R.color.white),
            PorterDuff.Mode.MULTIPLY
        )
        if (message.isOutgoing) tailBackground?.setColorFilter(
            ContextCompat.getColor(
                context,
                R.color.white
            ), PorterDuff.Mode.MULTIPLY
        ) else tailBackground?.setColorFilter(
            ContextCompat.getColor(context, R.color.blue_100),
            PorterDuff.Mode.MULTIPLY
        )
        balloon.background = balloonBackground

        tail.background = tailBackground

        if (!MessageChanger.bottom) {
            balloon.scaleY = -1f
            tail.scaleY = -1f
        } else {
            balloon.scaleY = 1f
            tail.scaleY = 1f
        }

        // visible tail
        tail.isInvisible = !needTail || MessageChanger.typeValue == 2


        if (MessageChanger.bottom) {

        } else {
            val layoutParams = tail.layoutParams as RelativeLayout.LayoutParams
            layoutParams.removeRule(RelativeLayout.ALIGN_BOTTOM) // Удаляем правило ALIGN_BOTTOM
            layoutParams.addRule(
                RelativeLayout.ALIGN_TOP,
                R.id.message_balloon
            ) // Добавляем правило ALIGN_TOP с нужным id
            tail.layoutParams = layoutParams
        }

        // References

        if (message.references.isNotEmpty() && message.messageBody.isEmpty()) {
            if (message.references[0].isGeo) showGeolocation(message.references[0], message.references[0].latitude, message.references[0].longitude)
              else {  messageBalloon.removeAllViews()
                val builder = ImageGridBuilder()
                val imageGridView: View =
                    builder.inflateView(messageBalloon, message.references.size)
                builder.bindView(imageGridView, message, message.references, this)
                messageBalloon.addView(imageGridView) }
        } else if (message.references.isNotEmpty() && message.messageBody.isNotEmpty()) {

            messageBalloon.removeAllViews()
            val builder = ImageGridBuilder()
            val imageGridView: View =
                builder.inflateView(messageBalloon, message.references.size)
            builder.bindView(imageGridView, message, message.references, this)
            messageBalloon.addView(imageGridView)
            val v = inflateText(messageBalloon)
            messageBalloon.addView(v)
            val text = v.findViewById<CorrectlyTouchEventTextView>(R.id.message_text)
            text.text = message.messageBody
            val imageTime = imageGridView.findViewById<LinearLayout>(R.id.message_info)
            imageTime.isVisible = false
            val date = v.findViewById<TextView>(R.id.message_time)
            val dates = Date(message.sentTimestamp)
            val time = StringUtils.getTimeText(context, dates)
            date.text = if (message.editTimestamp > 0) "edit $time" else time

            val status = v.findViewById<ImageView>(R.id.message_status_icon)

            MessageDeliveryStatusHelper.setupStatusImageView(
                message, status
            )
            MessageDeliveryStatusHelper.setupStatusImageView(
                message, status
            )

        }

//if (message.references.isNotEmpty()) {
// //  if (message.references[0].isGeo)
//       showGeolocation()
//}
        setStatusIcon(message)

        // subscribe for FILE UPLOAD PROGRESS
        itemView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                // subscribeForUploadProgress()
            }

            override fun onViewDetachedFromWindow(v: View) {
                unsubscribeAll()
            }
        })
        if (messageTextTv.getText().toString().trim().isEmpty()) {
            messageTextTv.setVisibility(View.GONE)
        }
        messageTextTv.setOnClickListener {
            if (Check.getSelectedMode()) {
                Log.d(
                    "sel",
                    "Check.getSelectedMode ${Check.getSelectedMode()}, mess isChecked = ${message.isChecked}"
                )
                listener?.checkItem(!message.isChecked, message.primary)
            } else {
                val popup = PopupMenu(it.context, it, Gravity.CENTER)
                popup.setForceShowIcon(true)
                if (message.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
                else popup.inflate(R.menu.popup_menu_message_incoming)


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
        itemView.setOnClickListener {
            Log.d(
                "sel",
                "ITEM Check.getSelectedMode ${Check.getSelectedMode()}, mess isChecked = ${message.isChecked}"
            )
            if (Check.getSelectedMode()) {
                listener?.checkItem(!message.isChecked, message.primary)
            } else {
                val popup = PopupMenu(messageTextTv.context, messageTextTv, Gravity.CENTER)
                popup.setForceShowIcon(true)
                if (message.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
                else popup.inflate(R.menu.popup_menu_message_incoming)


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

        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener?.onLongClick(message.primary)
            else {
                listener?.checkItem(!message.isChecked, message.primary)
            }
            true
        }
    }

    private fun showGeolocation(referenceDto: MessageReferenceDto, latitude: Double, longitude: Double) {
        messageBalloon.removeAllViews()
        val inflater = LayoutInflater.from(itemView.context)
        val geo = inflater.inflate(
            R.layout.geo_location,
            messageBalloon,
            false
        )
        val point = GeoPoint(latitude, longitude)
        val mapView =
            geo.findViewById<FrameLayout>(R.id.map_container) // создание карты, 256 - размер тайла в пикселях
        val map = mapView.findViewById<ImageView>(R.id.map_image)
        map.setImageURI(referenceDto.uri?.toUri())
      //  val mapi = mapView.findViewById<MapView>(R.id.map)
        messageBalloon.addView(geo)
        geo.setOnClickListener {
            itemView.context.startActivity(
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                }
            )
        }
    }

    private fun setStatusIcon(messageRealmObject: MessageDto) {
        statusIcon.isVisible = true
        //  bottomStatusIcon.isVisible = true
        //   progressBar.isVisible = false
        if (messageRealmObject.messageSendingState === MessageSendingState.Uploading) {
            messageTextTv.text = ""
            statusIcon.isVisible = false
            //    bottomStatusIcon.isVisible = false
        } else {
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageRealmObject, statusIcon
            )
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageRealmObject, statusIcon
            )
        }
    }


    fun inflateText(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.flex, parent, false)
    }

}
