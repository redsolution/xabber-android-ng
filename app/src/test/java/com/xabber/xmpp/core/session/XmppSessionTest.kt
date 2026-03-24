package com.xabber.xmpp.core.session

import com.xabber.stream.StreamState
import com.xabber.xmpp.core.auth.AccountIdentity
import com.xabber.xmpp.core.auth.AuthResult
import com.xabber.xmpp.core.auth.AuthSessionContext
import com.xabber.xmpp.core.auth.DeviceState
import com.xabber.xmpp.core.auth.OutboundAuthRequest
import com.xabber.xmpp.core.auth.OutboundAuthResponse
import com.xabber.xmpp.core.auth.XmppAuthStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmppSessionTest {

    @Test
    fun `pre-tls plain auth sends auth stanza and enters process auth`() {
        val session = XmppSession(
            account = AccountIdentity("user@example.com", "user"),
            authStrategies = listOf(FakeStrategy(supportedMechanism = "PLAIN", requestXml = "<auth/>")),
        )

        val action = suspendResult {
            session.handleStreamFeatures(
                raw = "<stream:features><mechanisms><mechanism>PLAIN</mechanism></mechanisms></stream:features>",
                currentState = StreamState.STREAM_OPEN,
                isDeviceRegistered = false,
                deviceState = null,
            )
        }

        val send = action as XmppSessionAction.SendAndTransition
        assertEquals("<auth/>", send.xml)
        assertEquals(StreamState.PROCESS_AUTH, send.state)
    }

    @Test
    fun `falls back to starttls when no auth strategy can run`() {
        val session = XmppSession(
            account = AccountIdentity("user@example.com", "user"),
            authStrategies = emptyList(),
        )

        val action = suspendResult {
            session.handleStreamFeatures(
                raw = "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>",
                currentState = StreamState.STREAM_OPEN,
                isDeviceRegistered = false,
                deviceState = null,
            )
        }

        assertEquals(StreamState.START_TLS, (action as XmppSessionAction.TransitionTo).state)
    }

    @Test
    fun `registered device transitions directly to binding when features expose devices`() {
        val session = XmppSession(
            account = AccountIdentity("user@example.com", "user"),
            authStrategies = emptyList(),
        )

        val action = suspendResult {
            session.handleStreamFeatures(
                raw = "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/><devices xmlns='https://xabber.com/protocol/devices'/></stream:features>",
                currentState = StreamState.STREAM_OPEN,
                isDeviceRegistered = true,
                deviceState = null,
            )
        }

        assertEquals(StreamState.BINDING, (action as XmppSessionAction.TransitionTo).state)
    }

    @Test
    fun `failed pre-tls auth keeps starttls fallback available`() {
        val session = XmppSession(
            account = AccountIdentity("user@example.com", "user"),
            authStrategies = listOf(FakeStrategy(supportedMechanism = "PLAIN", requestXml = "<auth/>")),
        )

        suspendResult {
            session.handleStreamFeatures(
                raw = "<stream:features><mechanisms><mechanism>PLAIN</mechanism></mechanisms><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>",
                currentState = StreamState.STREAM_OPEN,
                isDeviceRegistered = false,
                deviceState = null,
            )
        }

        assertTrue(session.shouldFallbackToTlsAfterAuthFailure())
    }

    private fun <T> suspendResult(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

    private class FakeStrategy(
        private val supportedMechanism: String,
        private val requestXml: String,
    ) : XmppAuthStrategy {
        override fun supports(
            features: com.xabber.xmpp.core.model.XmppStreamFeatures,
            account: AccountIdentity,
            deviceState: DeviceState?,
        ): Boolean = supportedMechanism in features.mechanisms

        override suspend fun start(sessionContext: AuthSessionContext): OutboundAuthRequest {
            return OutboundAuthRequest(requestXml)
        }

        override suspend fun onChallenge(challenge: String, sessionContext: AuthSessionContext): OutboundAuthResponse {
            return OutboundAuthResponse("")
        }

        override suspend fun onSuccess(success: String, sessionContext: AuthSessionContext): AuthResult {
            return AuthResult.Success
        }

        override suspend fun onFailure(failure: String, sessionContext: AuthSessionContext): AuthResult {
            return AuthResult.Failure("failed")
        }
    }
}
