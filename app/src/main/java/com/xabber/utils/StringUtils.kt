package com.xabber.utils

import android.content.Context
import com.xabber.presentation.application.util.isCurrentYear
import java.text.SimpleDateFormat
import java.util.*

object StringUtils {

    fun getTimeText(context: Context, timeStamp: Date): String {
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        return timeFormat.format(timeStamp)
    }

    fun getDateStringForMessage(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        val date = Date(timestamp)
        val strPattern = if (!date.isCurrentYear()) "d MMMM yyyy" else "d MMMM"
        return SimpleDateFormat(strPattern, locale).format(date)
    }

}
