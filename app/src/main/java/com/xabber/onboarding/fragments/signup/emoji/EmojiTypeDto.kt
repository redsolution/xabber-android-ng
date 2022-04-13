package com.xabber.onboarding.fragments.signup.emoji

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class EmojiTypeDto(
    @Expose
    @SerializedName("emojis")
    val list: List<List<String>>,
    @Expose
    @SerializedName("type")
    val name: String
)