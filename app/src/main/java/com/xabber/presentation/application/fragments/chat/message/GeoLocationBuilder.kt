package com.xabber.presentation.application.fragments.chat.message

import android.annotation.SuppressLint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.utils.StringUtils.getTimeText
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.dp
import org.osmdroid.views.MapView
import java.util.*
import kotlin.math.*

class GeoLocationBuilder {

    fun inflateView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.geo_location_box, parent, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun addGeoLocationBox(
        view: View, message: MessageStorageItem,
        latitude: Double,
        longitude: Double, onViewClickListener: MessageAdapter.OnViewClickListener?
    ) {
        val shape = view.findViewById<ShapeOfView>(R.id.geo_shape)
        val timeStamp = view.findViewById<LinearLayoutCompat>(R.id.message_info)
        val tvTime = view.findViewById<TextView>(R.id.tv_image_sending_time)
        val date = Date(if (message.editDate > 0) message.editDate else message.sentDate)
        val time = getTimeText(view.context, date)
        tvTime?.text =
            if (message.editDate > 0) view.context.resources.getString(R.string.edit) + " $time" else time
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

        // Hide the heavy MapView — use static tile image via Glide instead
        val map = view.findViewById<MapView>(R.id.map)
        map?.visibility = View.GONE

        val mapImage = view.findViewById<ImageView>(R.id.map_image)
        if (mapImage != null) {
            val zoom = 15
            val tileUrl = buildStaticMapTileUrl(latitude, longitude, zoom)
            Glide.with(mapImage.context)
                .load(tileUrl)
                .placeholder(R.drawable.ic_recent_image_placeholder)
                .error(R.drawable.ic_recent_image_placeholder)
                .centerCrop()
                .into(mapImage)
        }

        view.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_UP -> onViewClickListener?.onLocationClick(latitude, longitude)
            }; true
        }
    }

    /**
     * Build an OSM static tile URL for the given lat/lon at the specified zoom level.
     * Uses the standard OSM tile server to fetch a single 256x256 tile.
     */
    private fun buildStaticMapTileUrl(lat: Double, lon: Double, zoom: Int): String {
        val n = 1 shl zoom // 2^zoom
        val xTile = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val yTile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        return "https://tile.openstreetmap.org/$zoom/$xTile/$yTile.png"
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
