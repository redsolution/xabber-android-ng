package com.xabber.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.xabber.data.dto.HostDto

data class HostListDto(
    @Expose
    @SerializedName("results")
    val list: List<HostDto>
)