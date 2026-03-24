package com.xabber.xmpp.core.session

import com.xabber.stream.StreamState
import com.xabber.xmpp.core.auth.AccountIdentity
import com.xabber.xmpp.core.auth.AuthResult
import com.xabber.xmpp.core.auth.AuthSessionContext
import com.xabber.xmpp.core.auth.DeviceState
import com.xabber.xmpp.core.auth.XmppAuthStrategy
import com.xabber.xmpp.core.model.XmppStreamFeatures
import com.xabber.xmpp.core.parser.CoreStanzaParser
import io.viascom.nanoid.NanoId

sealed interface XmppSessionAction {
    data class TransitionTo(val state: StreamState) : XmppSessionAction
    data class SendAndTransition(val xml: String, val state: StreamState) : XmppSessionAction
    data class Fail(val message: String, val state: StreamState) : XmppSessionAction
    data object RestartStream : XmppSessionAction
    data object NoOp : XmppSessionAction
}

class XmppSession(
    private val account: AccountIdentity,
    private val authStrategies: List<XmppAuthStrategy>,
    private val parser: CoreStanzaParser = CoreStanzaParser("XmppSession"),
) {
    private var attemptedPreTlsAuth = false
    private var activeStrategy: XmppAuthStrategy? = null
    private var currentFeatures: XmppStreamFeatures = XmppStreamFeatures()

    suspend fun handleStreamFeatures(
        raw: String,
        currentState: StreamState,
        isDeviceRegistered: Boolean,
        deviceState: DeviceState?,
    ): XmppSessionAction {
        currentFeatures = parser.parseStreamFeatures(raw)
        val isPostTls = currentState == StreamState.PROCEED
        val authContext = AuthSessionContext(account = account, features = currentFeatures)
        val strategy = authStrategies.firstOrNull { it.supports(currentFeatures, account, deviceState) }

        if (strategy != null && (isPostTls || !attemptedPreTlsAuth)) {
            if (!isPostTls) {
                attemptedPreTlsAuth = true
            }
            activeStrategy = strategy
            val request = strategy.start(authContext)
            return if (request.xml.isBlank()) {
                XmppSessionAction.TransitionTo(StreamState.PROCESS_AUTH)
            } else {
                XmppSessionAction.SendAndTransition(request.xml, StreamState.PROCESS_AUTH)
            }
        }

        if (currentFeatures.startTlsSupported) {
            if (currentFeatures.startTlsRequired && attemptedPreTlsAuth) {
                attemptedPreTlsAuth = false
            }
            return XmppSessionAction.TransitionTo(StreamState.START_TLS)
        }

        if (currentState == StreamState.STREAM_OPEN && currentFeatures.devicesSupported) {
            return XmppSessionAction.TransitionTo(
                if (isDeviceRegistered) StreamState.BINDING else StreamState.DEVICE_REGISTRATION
            )
        }

        return XmppSessionAction.Fail(
            message = "No supported authentication features found",
            state = StreamState.NOT_CONNECTING,
        )
    }

    suspend fun handleChallenge(raw: String): XmppSessionAction {
        val strategy = activeStrategy ?: return XmppSessionAction.NoOp
        val response = strategy.onChallenge(raw, AuthSessionContext(account, currentFeatures))
        return if (response.xml.isBlank()) {
            XmppSessionAction.NoOp
        } else {
            XmppSessionAction.SendAndTransition(response.xml, StreamState.PROCESS_AUTH)
        }
    }

    suspend fun handleSuccess(raw: String): XmppSessionAction {
        val strategy = activeStrategy ?: return XmppSessionAction.NoOp
        return when (val result = strategy.onSuccess(raw, AuthSessionContext(account, currentFeatures))) {
            AuthResult.Continue -> XmppSessionAction.NoOp
            AuthResult.Success -> XmppSessionAction.RestartStream
            is AuthResult.Failure -> XmppSessionAction.Fail(result.reason, StreamState.AUTH_FAILED)
        }
    }

    suspend fun handleFailure(raw: String): XmppSessionAction {
        val strategy = activeStrategy ?: return XmppSessionAction.NoOp
        return when (val result = strategy.onFailure(raw, AuthSessionContext(account, currentFeatures))) {
            AuthResult.Continue -> XmppSessionAction.NoOp
            AuthResult.Success -> XmppSessionAction.RestartStream
            is AuthResult.Failure -> XmppSessionAction.Fail(result.reason, StreamState.AUTH_FAILED)
        }
    }

    fun buildBindRequest(): String {
        val bindId = NanoId.generateOptimized(9, "-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
        val resourceId = NanoId.generateOptimized(8, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", 63, 16)
        return """
            <iq type='set' id='$bindId'>
                <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>
                    <resource>xabber-android-$resourceId</resource>
                </bind>
            </iq>
        """.trimIndent()
    }

    fun resetAuthState() {
        attemptedPreTlsAuth = false
        activeStrategy = null
        currentFeatures = XmppStreamFeatures()
    }

    fun shouldFallbackToTlsAfterAuthFailure(): Boolean {
        return attemptedPreTlsAuth && currentFeatures.startTlsSupported
    }
}
