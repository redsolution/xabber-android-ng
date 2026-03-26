package com.xabber.xmpp.XEP_0CCC

import android.util.Log
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.domain.sync.model.SyncConversation
import com.xabber.domain.sync.model.SyncMarkers
import com.xabber.domain.sync.model.SyncMessage
import com.xabber.domain.sync.model.SyncPage
import com.xabber.domain.sync.model.SyncStatus
import com.xabber.xmpp.jid.XMPPJID
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

class SyncProtocolParser {

    private val TAG = "SyncProtocolParser"
    private val SYNC_NS = "https://xabber.com/protocol/synchronization"

    fun parseSnapshot(rawIq: String): SyncPage? = parseIq(rawIq, isPush = false)

    fun parsePush(rawIq: String): SyncPage? = parseIq(rawIq, isPush = true)

    private fun parseIq(rawIq: String, isPush: Boolean): SyncPage? {
        return try {
            val cleanedIq = rawIq.trim()
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(cleanedIq.byteInputStream())
            val iqElement = doc.documentElement
            val queryElement = iqElement
                .getElementsByTagNameNS(SYNC_NS, "query").item(0) as? Element ?: return null
            val stamp = queryElement.getAttribute("stamp") ?: "0"
            val conversations = parseConversations(queryElement)
            SyncPage(stamp, conversations, isPush)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sync IQ: ${e.message}")
            null
        }
    }

    private fun parseConversations(query: Element): List<SyncConversation> {
        val result = mutableListOf<SyncConversation>()
        val nodes = query.getElementsByTagName("conversation")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            parseConversation(el)?.let { result += it }
        }
        return result
    }

    private fun parseConversation(el: Element): SyncConversation? {
        val jid = el.getAttribute("jid")?.let { XMPPJID(it).bare() }?.takeIf { it.isNotBlank() } ?: return null
        val type = el.getAttribute("type")?.takeIf { it.isNotBlank() } ?: return null
        val stamp = el.getAttribute("stamp") ?: return null
        val status = SyncStatus.from(el.getAttribute("status") ?: "active")
        val pinned = el.getAttribute("pinned")?.toLongOrNull() ?: 0L
        val muteUntilMs = parseMute(el.getAttribute("mute"))
        val markers = parseMarkers(el)
        val lastMessage = parseLastMessage(el, jid, type, stamp)
        return SyncConversation(jid, type, stamp, status, pinned, muteUntilMs, markers, lastMessage)
    }

    /**
     * Server sends mute as absolute timestamp in seconds per XEP-0CCC §8.3.
     * absent = not muted (-1), 0 = muted forever (Long.MAX_VALUE), >0 = absolute seconds → ms.
     */
    private fun parseMute(raw: String?): Long {
        val rawMute = raw?.toLongOrNull() ?: return -1L
        return when {
            rawMute == 0L -> Long.MAX_VALUE  // forever
            rawMute > 0L -> rawMute * 1000L  // seconds → milliseconds
            else -> -1L
        }
    }

    private fun parseMarkers(conv: Element): SyncMarkers {
        val metaList = conv.getElementsByTagName("metadata")
        for (i in 0 until metaList.length) {
            val meta = metaList.item(i) as? Element ?: continue
            if (meta.getAttribute("node") != SYNC_NS) continue
            val unreadEl = meta.getElementsByTagName("unread").item(0) as? Element
            val displayedEl = meta.getElementsByTagName("displayed").item(0) as? Element
            val deliveredEl = meta.getElementsByTagName("delivered").item(0) as? Element
            return SyncMarkers(
                unreadCount = unreadEl?.getAttribute("count")?.toLongOrNull() ?: 0L,
                unreadAfterUs = unreadEl?.getAttribute("after")?.toLongOrNull(),
                displayedId = displayedEl?.getAttribute("id")?.takeIf { it != "0" && it.isNotEmpty() },
                deliveredId = deliveredEl?.getAttribute("id")?.takeIf { it != "0" && it.isNotEmpty() },
            )
        }
        return SyncMarkers(0, null, null, null)
    }

    private fun parseLastMessage(conv: Element, convJid: String, convType: String, convStamp: String): SyncMessage? {
        val convStampUs = convStamp.toLongOrNull() ?: 0L
        val metaList = conv.getElementsByTagName("metadata")
        for (i in 0 until metaList.length) {
            val meta = metaList.item(i) as? Element ?: continue
            if (meta.getAttribute("node") != SYNC_NS) continue
            val lastMsgEl = meta.getElementsByTagName("last-message").item(0) as? Element ?: continue
            val msgEl = lastMsgEl.getElementsByTagName("message").item(0) as? Element ?: continue
            return parseMessageElement(msgEl, convJid, convType, convStampUs)
        }
        return null
    }

    private fun parseMessageElement(el: Element, convJid: String, convType: String, convStampUs: Long = 0L): SyncMessage? {
        val id = el.getAttribute("id")?.takeIf { it.isNotBlank() } ?: return null
        val body = el.getElementsByTagName("body").item(0)?.textContent?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val fromJid = el.getAttribute("from")?.let { XMPPJID(it).bare() }?.takeIf { it.isNotBlank() } ?: convJid
        val timestampUs = parseTimestampUs(el, convStampUs)
        val isGroupConversation = convType.contains("xabber.com/protocol/groups")

        val groupNickname: String?
        val effectiveBody: String
        val effectiveFromJid: String
        if (isGroupConversation) {
            val colonIdx = body.indexOf(":\n")
            groupNickname = if (colonIdx > 0) body.substring(0, colonIdx) else null
            effectiveBody = MessageStorageItem.stripGroupNicknamePrefix(body)
            // For group messages, `from` is the group JID. Extract the actual sender JID
            // from <x xmlns='https://xabber.com/protocol/groups'><user jid='...'/>
            val groupNs = "https://xabber.com/protocol/groups"
            val xElements = el.getElementsByTagNameNS(groupNs, "x")
            val userEl = (0 until xElements.length)
                .mapNotNull { xElements.item(it) as? Element }
                .firstNotNullOfOrNull { xEl ->
                    xEl.getElementsByTagNameNS(groupNs, "user").item(0) as? Element
                        ?: xEl.getElementsByTagName("user").item(0) as? Element
                }
            val senderJid = userEl?.getAttribute("jid")?.takeIf { it.isNotBlank() }
                ?: (userEl?.getElementsByTagName("jid")?.item(0) as? Element)?.textContent?.trim()?.takeIf { it.isNotBlank() }
            effectiveFromJid = senderJid ?: fromJid
        } else {
            groupNickname = null
            effectiveBody = body
            effectiveFromJid = fromJid
        }

        return SyncMessage(id, effectiveFromJid, effectiveBody, timestampUs, isOutgoing = false, groupNickname)
    }

    private fun parseTimestampUs(msgEl: Element, fallbackUs: Long = 0L): Long {
        val timeEl = msgEl.getElementsByTagName("time").item(0) as? Element
            ?: return if (fallbackUs > 0L) fallbackUs else System.currentTimeMillis() * 1000L
        val stamp = timeEl.getAttribute("stamp")
            ?: return if (fallbackUs > 0L) fallbackUs else System.currentTimeMillis() * 1000L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            (sdf.parse(stamp)?.time ?: (if (fallbackUs > 0L) fallbackUs / 1000L else System.currentTimeMillis())) * 1000L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse message timestamp: $stamp")
            if (fallbackUs > 0L) fallbackUs else System.currentTimeMillis() * 1000L
        }
    }
}
