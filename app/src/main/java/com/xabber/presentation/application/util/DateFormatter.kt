package com.xabber.presentation.application.util

import android.text.format.DateUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object DateFormatter {
 const val DATE_FORMAT = "yyyy MM dd HH:mm:ss EE"

        fun dateFormat(date: String): String {
            var time = Date()
            try {
                time = SimpleDateFormat(DATE_FORMAT).parse(date)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            val calendar = Calendar.getInstance()
            calendar.time = time
            val now = Calendar.getInstance()
            val yesterday = Calendar.getInstance()
            yesterday.add(Calendar.DATE, -1)

            return if (DateUtils.isToday(time.time)) {
                SimpleDateFormat(" H:mm").format(time)
            } else if (calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && calendar.get(
                    Calendar.MONTH) == yesterday.get(Calendar.MONTH) && calendar.get(Calendar.DATE) == yesterday.get(
                    Calendar.DATE)) {
                "вчера ${SimpleDateFormat(" H:mm").format(time)}"
            } else if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(
                    Calendar.WEEK_OF_YEAR
                ) == now.get(Calendar.WEEK_OF_YEAR)
            ) {
                SimpleDateFormat("EE H:mm").format(time)
            } else if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                SimpleDateFormat("d MMMM").format(time)
            } else {
                SimpleDateFormat("d MMMM yyyy").format(time)
            }
        }




    }
