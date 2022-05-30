package com.xabber.presentation.application.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.gson.internal.bind.util.ISO8601Utils.format
import com.xabber.presentation.application.activity.ApplicationActivity
import java.text.DateFormat
import java.util.*

object StringUtils {


    fun getTimeText(context: Context, timeStamp: Date): String {
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        return timeFormat.format(timeStamp)
    }
}