package com.xabber.presentation.application.fragments.chat.geo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Location(val latitude: Double, val longitude: Double) : Parcelable
