package com.xabber.presentation.onboarding.fragments.signin.feature

class Feature(
    val nameResId: Int,
    var state: State = State.Loading
)

enum class State {
    Loading,
    Success,
    Error
}
