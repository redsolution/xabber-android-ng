package com.xabber.onboarding.fragments.signup.emoji


fun List<EmojiTypeDto>.toMap(): Map<String, List<List<String>>> {
    val map = this.map {
        it.name to it.list
    }.toMap()

    return map
}