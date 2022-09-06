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

    @JvmOverloads
    fun getDateStringForMessage(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        val date = Date(timestamp)
        val strPattern = if (!date.isCurrentYear()) "d MMMM yyyy" else "d MMMM"
        return SimpleDateFormat(strPattern, locale).format(date)
    }

    fun isSameDay(date1: Long?, date2: Long?): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = Date(date1!!)
        cal2.time = Date(date2!!)
        return cal1[Calendar.DAY_OF_YEAR] == cal2[Calendar.DAY_OF_YEAR] &&
                cal1[Calendar.YEAR] == cal2[Calendar.YEAR]
    }
}
