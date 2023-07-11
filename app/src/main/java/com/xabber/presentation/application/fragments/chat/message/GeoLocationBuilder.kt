package com.xabber.presentation.application.fragments.chat.message

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.utils.StringUtils
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.dp
import java.util.*

class GeoLocationBuilder {

    fun inflateView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.geo_location_box, parent, false)
    }

    fun addGeoLocationBox(
        view: View, message: MessageDto,
        latitude: Double,
        longitude: Double
    ) {
        val mapImage = view.findViewById<ImageView>(R.id.map_image)
        val shape = view.findViewById<ShapeOfView>(R.id.geo_shape)
        val timeStamp = view.findViewById<LinearLayoutCompat>(R.id.message_info)
        val tvTime = view.findViewById<TextView>(R.id.tv_image_sending_time)
        val date =
            Date(if (message.editTimestamp > 0) message.editTimestamp else message.sentTimestamp)
        val time = StringUtils.getTimeText(view.context, date)
        tvTime?.text =
            if (message.editTimestamp > 0) view.context.resources.getString(R.string.edit) + " $time" else time
        val radius =
            if (ChatSettingsManager.cornerValue > 4) (ChatSettingsManager.cornerValue - 4) else 1
        val timeStampRadius = if (radius > 3) radius - 3 else 1
        val timeStampBackground = getTimeStampBackground(timeStampRadius)
        timeStamp.setBackgroundResource(timeStampBackground)
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
        val shapeDrawable = ShapeDrawable(RoundRectShape(cornerRadii, null, null))
        shape.setDrawable(shapeDrawable)
        // получить картинку по координатам и загрузить в mapImage
    }


    private fun getTimeStampBackground(timeStampRadius: Int): Int {
        return when (timeStampRadius) {
            1 -> R.drawable.time_stamp_1px
            2 -> R.drawable.time_stamp_2px
            3 -> R.drawable.time_stamp_3px
            4 -> R.drawable.time_stamp_4px
            5 -> R.drawable.time_stamp_5px
            6 -> R.drawable.time_stamp_6px
            7 -> R.drawable.time_stamp_7px
            8 -> R.drawable.time_stamp_8px
            9 -> R.drawable.time_stamp_9px
            10 -> R.drawable.time_stamp_10px
            11 -> R.drawable.time_stamp_11px
            12 -> R.drawable.time_stamp_12px
            13 -> R.drawable.time_stamp_13px

            else -> R.drawable.time_stamp_1px
        }
    }
}


