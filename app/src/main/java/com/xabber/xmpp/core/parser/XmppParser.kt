package com.xabber.xmpp.core.parser

import com.xabber.xmpp.core.model.IqStanza
import com.xabber.xmpp.core.model.MessageStanza
import com.xabber.xmpp.core.model.PresenceStanza
import com.xabber.xmpp.core.model.ProceedTlsEvent
import com.xabber.xmpp.core.model.SaslChallengeEvent
import com.xabber.xmpp.core.model.SaslFailureEvent
import com.xabber.xmpp.core.model.SaslSuccessEvent
import com.xabber.xmpp.core.model.StreamClosedEvent
import com.xabber.xmpp.core.model.StreamErrorEvent
import com.xabber.xmpp.core.model.StreamFeaturesEvent
import com.xabber.xmpp.core.model.StreamOpenEvent
import com.xabber.xmpp.core.model.XmppParsedEvent

class XmppParser(
    private val stanzaParser: CoreStanzaParser = CoreStanzaParser(),
) {
    private val streamBuffer = StringBuilder()

    fun parseChunk(chunk: String): List<XmppParsedEvent> {
        return extractRawStanzas(chunk).mapNotNull { stanza ->
            when (stanza.tagName) {
                "stream:open" -> StreamOpenEvent(stanza.raw)
                "stream:features" -> StreamFeaturesEvent(stanza.raw, stanzaParser.parseStreamFeatures(stanza.raw))
                "stream:error" -> StreamErrorEvent(stanza.raw)
                "stream:close" -> StreamClosedEvent()
                "iq" -> stanzaParser.parseIQ(stanza.raw)?.let(::IqStanza)
                "message" -> (stanzaParser.parseMessage(stanza.raw) ?: stanzaParser.extractFallbackMessage(stanza.raw))?.let(::MessageStanza)
                "presence" -> PresenceStanza(stanza.raw)
                "challenge" -> SaslChallengeEvent(stanza.raw)
                "success" -> SaslSuccessEvent(stanza.raw)
                "failure" -> SaslFailureEvent(stanza.raw)
                "proceed" -> ProceedTlsEvent(stanza.raw)
                else -> null
            }
        }
    }

    private data class RawStanza(val tagName: String, val raw: String)

    private fun extractRawStanzas(chunk: String): List<RawStanza> {
        val maxBufferSize = 2 * 1024 * 1024
        if (streamBuffer.length + chunk.length > maxBufferSize) {
            streamBuffer.clear()
            return emptyList()
        }
        streamBuffer.append(chunk)
        var content = streamBuffer.toString()
        val result = mutableListOf<RawStanza>()

        while (content.isNotEmpty()) {
            val start = content.indexOf("<")
            if (start == -1) break

            if (content.startsWith("<?xml", start) || content.indexOf("<stream:stream", start) == start) {
                val end = content.indexOf(">", start)
                if (end == -1) break
                result.add(RawStanza("stream:open", content.substring(start, end + 1)))
                content = content.substring(end + 1).trimStart()
                continue
            }
            if (content.indexOf("<stream:features>", start) == start) {
                val closeTag = "</stream:features>"
                val end = content.indexOf(closeTag)
                if (end == -1) break
                val fullEnd = end + closeTag.length
                result.add(RawStanza("stream:features", content.substring(start, fullEnd)))
                content = content.substring(fullEnd).trimStart()
                continue
            }
            if (content.indexOf("<stream:error>", start) == start) {
                val closeTag = "</stream:error>"
                val end = content.indexOf(closeTag)
                if (end == -1) break
                val fullEnd = end + closeTag.length
                result.add(RawStanza("stream:error", content.substring(start, fullEnd)))
                content = content.substring(fullEnd).trimStart()
                continue
            }
            if (content.indexOf("</stream:stream>", start) == start) {
                val closeTag = "</stream:stream>"
                result.add(RawStanza("stream:close", closeTag))
                content = content.substring(start + closeTag.length).trimStart()
                continue
            }

            val candidates = listOfNotNull(
                content.indexOf("<iq", start).takeIf { it != -1 }?.let { it to "iq" },
                content.indexOf("<presence", start).takeIf { it != -1 }?.let { it to "presence" },
                content.indexOf("<message", start).takeIf { it != -1 }?.let { it to "message" },
                content.indexOf("<challenge", start).takeIf { it != -1 }?.let { it to "challenge" },
                content.indexOf("<success", start).takeIf { it != -1 }?.let { it to "success" },
                content.indexOf("<failure", start).takeIf { it != -1 }?.let { it to "failure" },
                content.indexOf("<proceed", start).takeIf { it != -1 }?.let { it to "proceed" },
            ).minByOrNull { it.first }

            val (stanzaStart, tagName) = if (candidates != null) {
                candidates
            } else {
                val gtPos = content.indexOf(">", start)
                if (gtPos == -1) break
                val name = content.substring(start + 1, gtPos).split(Regex("\\s+")).first().trimEnd('/')
                start to name
            }

            val tagEnd = content.indexOf(">", stanzaStart)
            if (tagEnd == -1) break

            val fullTag = content.substring(stanzaStart + 1, tagEnd)
            val isSelfClosing = fullTag.endsWith("/")
            val fullEnd = if (isSelfClosing) {
                tagEnd + 1
            } else {
                var openTags = 1
                var currentIndex = tagEnd + 1
                var complete = false
                while (openTags > 0 && currentIndex < content.length) {
                    val nextOpen = content.indexOf("<$tagName", currentIndex)
                    val nextClose = content.indexOf("</$tagName>", currentIndex)
                    if (nextClose == -1) break
                    if (nextOpen != -1 && nextOpen < nextClose) {
                        val gtPos = content.indexOf(">", nextOpen)
                        if (gtPos == -1) break
                        openTags++
                        currentIndex = gtPos + 1
                    } else {
                        openTags--
                        currentIndex = nextClose + "</$tagName>".length
                        if (openTags == 0) {
                            complete = true
                            break
                        }
                    }
                }
                if (!complete) break
                currentIndex
            }

            result.add(RawStanza(tagName, content.substring(stanzaStart, fullEnd)))
            content = content.substring(fullEnd).trimStart()
        }

        streamBuffer.clear()
        streamBuffer.append(content)
        return result
    }
}
