package com.xabber.common

interface XMPPStreamDelegate {
    suspend fun didReceiveIQ(iq: XMPPIQ, stream: Stream): Boolean

    fun didReceivePresence(presence: String, stream: Stream): Boolean

    fun didReceiveStreamHeader(header: String, stream: Stream): Boolean

    fun didReceiveStreamFeatures(features: String, stream: Stream): Boolean

    suspend fun didReceiveChallenge(challenge: String, stream: Stream): Boolean

    suspend fun didReceiveSuccess(success: String, stream: Stream): Boolean


    fun didReceiveFailure(failure: String, stream: Stream): Boolean

    fun didReceiveProceed(proceed: String, stream: Stream): Boolean

    suspend fun didReceiveMessage(message: String, stream: Stream): Boolean

    suspend fun streamDidConnect(stream: Stream): Boolean

    suspend fun streamBinding(stream: Stream): Boolean

    suspend fun streamDeviceRegistration(stream: Stream): Boolean

    suspend fun streamAuthFailed(stream: Stream): Boolean

    suspend fun streamAuthSuccess(stream: Stream): Boolean

    suspend fun streamPlainAuth(stream: Stream): Boolean

    suspend fun streamOCRAAuth(stream: Stream): Boolean

    suspend fun streamStartTLS(stream: Stream): Boolean

    suspend fun streamSyncRequest(stream: Stream): Boolean

    suspend fun streamCarbonsSend(stream: Stream): Boolean

}