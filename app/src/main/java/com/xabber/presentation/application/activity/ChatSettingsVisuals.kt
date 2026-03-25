package com.xabber.presentation.application.activity

import com.xabber.R

object ChatSettingsVisuals {
    fun gradientDrawable(gradient: Int): Int = when (gradient) {
        1 -> R.drawable.gradient_bordo
        2 -> R.drawable.gradient_red
        3 -> R.drawable.gradient_orange
        4 -> R.drawable.gradient_yellish_blue
        5 -> R.drawable.gradient_light_green
        6 -> R.drawable.gradient_light_yellish_blue
        8 -> R.drawable.gradient_purple
        else -> R.drawable.gradient_blue
    }

    fun patternDrawable(designType: Int): Int = when (designType) {
        2 -> R.drawable.cats_repeat
        3 -> R.drawable.hearts_repeat
        4 -> R.drawable.flowers_repeat
        5 -> R.drawable.meadow_repeat
        6 -> R.drawable.summer_repeat
        else -> R.drawable.aliens_repeat
    }
}
