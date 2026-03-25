package com.xabber.xmpp.XEP_0CCC

import android.util.Log
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.domain.sync.model.SYNC_PAGE_SIZE
import com.xabber.stream.Stream
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.delay

class SyncProtocolSender {

    private val TAG = "SyncProtocolSender"

    suspend fun sendSyncRequest(
        stream: Stream,
        owner: String,
        boundJid: String?,
        version: String,
        after: String?,
    ) {
        val syncId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
        val query = buildString {
            append("<query xmlns='https://xabber.com/protocol/synchronization'")
            if (version.isNotEmpty() && version != "0") {
                append(" stamp='$version'")
            }
            append(">")
            append("<set xmlns='http://jabber.org/protocol/rsm'>")
            append("<max>$SYNC_PAGE_SIZE</max>")
            if (after != null) {
                append("<after>$after</after>")
            }
            append("</set>")
            append("</query>")
        }
        val fromAttr = boundJid?.let { " from='$it'" } ?: ""
        val toAttr = " to='$owner'"
        val iq = "<iq type='get' id='SYNC: $syncId'$fromAttr$toAttr>$query</iq>"
        writeWithRetry(stream, iq)
    }

    suspend fun sendMuteConversation(
        stream: Stream,
        owner: String,
        boundJid: String?,
        chatJid: String,
        type: ConversationType,
        muteSeconds: Long,
    ) {
        val fromAttr = boundJid?.let { " from='$it'" } ?: " from='$owner'"
        val stanza = "<iq type='set' id='mute_${chatJid}'$fromAttr to='$owner'>" +
                "<query xmlns='https://xabber.com/protocol/synchronization'>" +
                "<conversation jid='$chatJid' type='${type.rawValue}' mute='$muteSeconds'/>" +
                "</query></iq>"
        writeWithRetry(stream, stanza)
    }

    suspend fun sendUnmuteConversation(
        stream: Stream,
        owner: String,
        boundJid: String?,
        chatJid: String,
        type: ConversationType,
    ) {
        val fromAttr = boundJid?.let { " from='$it'" } ?: " from='$owner'"
        val stanza = "<iq type='set' id='unmute_${chatJid}'$fromAttr to='$owner'>" +
                "<query xmlns='https://xabber.com/protocol/synchronization'>" +
                "<conversation jid='$chatJid' type='${type.rawValue}' mute='-1'/>" +
                "</query></iq>"
        writeWithRetry(stream, stanza)
    }

    suspend fun sendPinChat(
        stream: Stream,
        owner: String,
        boundJid: String?,
        chatId: String,
        type: ConversationType,
    ) {
        val fromAttr = boundJid?.let { " from='$it'" } ?: " from='$owner'"
        val stanza = "<iq type='set' id='pin_${chatId}'$fromAttr to='$owner'>" +
                "<query xmlns='https://xabber.com/protocol/synchronization'>" +
                "<conversation jid='$chatId' type='${type.rawValue}' pinned='1'/>" +
                "</query></iq>"
        writeWithRetry(stream, stanza)
    }

    suspend fun sendUpdate(
        stream: Stream,
        owner: String,
        boundJid: String?,
        chatId: String,
        type: ConversationType,
        status: String? = null,
        mute: Double? = null,
    ) {
        val statusAttr = if (status != null) "status='$status'" else ""
        val muteAttr = if (mute != null) "mute='$mute'" else ""
        val fromAttr = boundJid?.let { " from='$it'" } ?: " from='$owner'"
        val stanza = "<iq type='set' id='update_${chatId}'$fromAttr to='$owner'>" +
                "<query xmlns='https://xabber.com/protocol/synchronization'>" +
                "<conversation jid='$chatId' type='${type.rawValue}' $statusAttr $muteAttr/>" +
                "</query></iq>"
        writeWithRetry(stream, stanza)
    }

    private suspend fun writeWithRetry(stream: Stream, stanza: String) {
        var attempt = 0
        while (attempt < 3) {
            if (stream.socket?.write(stanza) == true) return
            attempt++
            if (attempt < 3) {
                Log.w(TAG, "IQ write failed (attempt $attempt/3), retrying in 1s: ${stanza.take(80)}")
                delay(1_000L)
            }
        }
        Log.w(TAG, "IQ write failed after 3 attempts: ${stanza.take(80)}")
    }
}
