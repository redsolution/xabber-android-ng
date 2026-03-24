package com.xabber.xmpp.core.auth

import com.xabber.xmpp.core.model.XmppStreamFeatures
import java.nio.charset.StandardCharsets
import java.util.Base64

class PlainAuthStrategy(
    private val passwordProvider: suspend (String) -> String?,
) : XmppAuthStrategy {
    override fun supports(features: XmppStreamFeatures, account: AccountIdentity, deviceState: DeviceState?): Boolean {
        return "PLAIN" in features.mechanisms
    }

    override suspend fun start(sessionContext: AuthSessionContext): OutboundAuthRequest {
        val password = passwordProvider(sessionContext.account.jid)
            ?: error("Password not found for ${sessionContext.account.jid}")
        val payload = "\u0000${sessionContext.account.username}\u0000$password"
        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return OutboundAuthRequest(
            "<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$encoded</auth>",
        )
    }

    override suspend fun onChallenge(challenge: String, sessionContext: AuthSessionContext): OutboundAuthResponse {
        error("PLAIN authentication does not support challenge handling")
    }

    override suspend fun onSuccess(success: String, sessionContext: AuthSessionContext): AuthResult = AuthResult.Success

    override suspend fun onFailure(failure: String, sessionContext: AuthSessionContext): AuthResult {
        return AuthResult.Failure("PLAIN authentication failed")
    }
}
