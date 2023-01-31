package com.xabber.models.dto

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class HostDto(
    @Expose
    @SerializedName("host")
    val name: String
)