package com.xabber.presentation.application.fragments.chatlist

enum class TimeMute(val time: Long) {
    MIN15(900), HOUR1(3600), HOUR2(7200), DAY1(86400), FOREVER(315360000)
}