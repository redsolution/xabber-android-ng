package com.xabber.presentation.onboarding.fragments.signup.emoji


fun List<EmojiTypeDto>.toMap(): Map<String, List<List<String>>> {
    val map = this.associate {
        it.name to it.list
    }

    return map
}