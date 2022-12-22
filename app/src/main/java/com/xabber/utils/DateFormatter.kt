package com.xabber.utils

import android.annotation.SuppressLint
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.*

object DateFormatter {


    @SuppressLint("SimpleDateFormat")
    fun dateFormat(date: Long): String {
        val time = Date(date)
        val calendar = Calendar.getInstance()
        calendar.time = time
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DATE, -1)

        return if (DateUtils.isToday(time.time)) {
            SimpleDateFormat(" H:mm:ss").format(time)
        } else if (calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && calendar.get(
                Calendar.MONTH
            ) == yesterday.get(Calendar.MONTH) && calendar.get(Calendar.DATE) == yesterday.get(
                Calendar.DATE
            ) && ((System.currentTimeMillis() - date) < 43200000)
        ) {
            SimpleDateFormat(" H:mm:ss").format(time)
        } else if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(
                Calendar.WEEK_OF_YEAR
            ) == now.get(Calendar.WEEK_OF_YEAR)
        ) {
            SimpleDateFormat("EE").format(time)
        } else if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            SimpleDateFormat("d MMM").format(time)
        } else {
            SimpleDateFormat("d MMM yyyy").format(time)
        }
    }

}
