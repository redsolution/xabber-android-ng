package com.xabber.utils.mask

import androidx.annotation.DrawableRes
import com.xabber.R

enum class Mask(
    @DrawableRes val size32: Int,
    @DrawableRes val size48: Int,
    @DrawableRes val size56: Int,
    @DrawableRes val size128: Int,
    @DrawableRes val size176: Int
) {
    Circle(
        R.drawable.circle32,
        R.drawable.circle48,
        R.drawable.circle56,
        R.drawable.circle128,
        R.drawable.circle176
    ),
    Hexagon(
        R.drawable.hexagon32,
        R.drawable.hexagon48,
        R.drawable.hexagon56,
        R.drawable.hexagon128,
        R.drawable.hexagon176
    ),
    Octagon(
        R.drawable.octagon32,
        R.drawable.octagon48,
        R.drawable.octagon56,
        R.drawable.octagon128,
        R.drawable.octagon176
    ),
    Pentagon(
        R.drawable.pentagon32,
        R.drawable.pentagon48,
        R.drawable.pentagon56,
        R.drawable.hexagon128,
        R.drawable.pentagon176
    ),
    Rounded(
        R.drawable.rounded32,
        R.drawable.rounded48,
        R.drawable.rounded56,
        R.drawable.rounded128,
        R.drawable.rounded176
    ),
    Squircle(
        R.drawable.squircle32,
        R.drawable.squircle48,
        R.drawable.squircle56,
        R.drawable.squircle128,
        R.drawable.squircle176
    ),
    Star(
        R.drawable.star32,
        R.drawable.star48,
        R.drawable.star56,
        R.drawable.star128,
        R.drawable.star176
    )
}