package com.xabber.xmpp.core.auth

import com.xabber.stream.Stream
import com.xabber.xmpp.auth.DevicesOCRA
import com.xabber.xmpp.core.model.XmppStreamFeatures
import io.realm.kotlin.Realm

class DevicesOcraAuthStrategy(
    private val streamProvider: () -> Stream,
    private val realmProvider: () -> Realm,
    private val deviceStateProvider: suspend (String) -> DeviceState?,
) : XmppAuthStrategy {
    private var ocra: DevicesOCRA? = null

    override fun supports(features: XmppStreamFeatures, account: AccountIdentity, deviceState: DeviceState?): Boolean {
        return "DEVICES-OCRA" in features.mechanisms && deviceState != null
    }

    override suspend fun start(sessionContext: AuthSessionContext): OutboundAuthRequest {
        val deviceState = deviceStateProvider(sessionContext.account.jid)
            ?: error("Device state not found for ${sessionContext.account.jid}")
        val stream = streamProvider()
        ocra = DevicesOCRA(
            stream = stream,
            deviceId = deviceState.deviceId,
            secret = deviceState.secret,
            validationKey = deviceState.validationKey,
            authCounter = deviceState.authCounter,
            realm = realmProvider(),
        )
        if (!requireNotNull(ocra).start()) {
            error("Failed to start DEVICES-OCRA authentication")
        }
        return OutboundAuthRequest(xml = "")
    }

    override suspend fun onChallenge(challenge: String, sessionContext: AuthSessionContext): OutboundAuthResponse {
        checkNotNull(ocra) { "DEVICES-OCRA was not started" }
        if (!ocra!!.handleAuthChallenge(challenge)) {
            error("Failed to process DEVICES-OCRA challenge")
        }
        return OutboundAuthResponse(xml = "")
    }

    override suspend fun onSuccess(sessionContext: AuthSessionContext): AuthResult {
        return if (ocra?.handleAuthResponse("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>") == false) {
            AuthResult.Failure("DEVICES-OCRA success handling failed")
        } else {
            AuthResult.Success
        }
    }

    override suspend fun onFailure(failure: String, sessionContext: AuthSessionContext): AuthResult {
        return AuthResult.Failure("DEVICES-OCRA authentication failed")
    }
}
