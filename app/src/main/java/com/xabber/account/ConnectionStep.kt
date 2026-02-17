package com.xabber.account

import androidx.annotation.StringRes
import com.xabber.R

enum class ConnectionStep(@StringRes val descriptionRes: Int) {
    RESOLVING(R.string.step_resolving_server),
    CONNECTING(R.string.step_establishing_connection),
    AUTHENTICATING(R.string.step_authenticating),
    LOADING_ROSTER(R.string.step_loading_contacts),
    SYNCING_MESSAGES(R.string.step_synchronizing_messages)
}

enum class StepState { PENDING, IN_PROGRESS, SUCCESS, ERROR }

interface ConnectionProgressListener {
    fun onStepChanged(step: ConnectionStep, state: StepState)
    fun onComplete()
    fun onError(message: String)
}