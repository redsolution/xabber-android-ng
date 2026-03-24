package com.xabber.xmpp.core.auth

import com.xabber.xmpp.core.model.XmppStreamFeatures

data class AccountIdentity(
    val jid: String,
    val username: String,
)

data class DeviceState(
    val deviceId: String,
    val secret: String,
    val validationKey: String,
    val authCounter: Long,
)

data class OutboundAuthRequest(
    val xml: String,
)

data class OutboundAuthResponse(
    val xml: String,
)

sealed interface AuthResult {
    data object Continue : AuthResult
    data object Success : AuthResult
    data class Failure(val reason: String) : AuthResult
}

data class AuthSessionContext(
    val account: AccountIdentity,
    val features: XmppStreamFeatures,
)

interface XmppAuthStrategy {
    fun supports(features: XmppStreamFeatures, account: AccountIdentity, deviceState: DeviceState?): Boolean

    suspend fun start(sessionContext: AuthSessionContext): OutboundAuthRequest

    suspend fun onChallenge(challenge: String, sessionContext: AuthSessionContext): OutboundAuthResponse

    suspend fun onSuccess(sessionContext: AuthSessionContext): AuthResult

    suspend fun onFailure(failure: String, sessionContext: AuthSessionContext): AuthResult
}
