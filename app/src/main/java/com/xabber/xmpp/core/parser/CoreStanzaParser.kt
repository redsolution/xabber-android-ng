package com.xabber.xmpp.core.parser

import android.util.Log
import com.xabber.stream.serializers.XMPPIQ
import com.xabber.utils.parseXMPPDateToMillis
import com.xabber.xmpp.core.model.XmppStreamFeatures
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.XMPPMessage
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class CoreStanzaParser(
    private val tag: String = "CoreStanzaParser",
) {

    fun parseStreamFeatures(raw: String): XmppStreamFeatures {
        val mechanisms = Regex("""<mechanism>([^<]+)</mechanism>""")
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
        return XmppStreamFeatures(
            mechanisms = mechanisms,
            startTlsSupported = raw.contains("<starttls"),
            startTlsRequired = raw.contains("<required/>"),
            proxySupported = raw.contains("<proxy"),
            devicesSupported = raw.contains("https://xabber.com/protocol/devices"),
            bindSupported = raw.contains("<bind"),
            synchronizationSupported = raw.contains("xabber.com/protocol/synchronization"),
        )
    }

    fun parseIQ(stanza: String): XMPPIQ? {
        return try {
            val typeMatch = Regex("""type=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1) ?: return null
            val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val error = if (typeMatch == "error") {
                val errorStart = stanza.indexOf("<error")
                if (errorStart != -1) {
                    val errorEnd = stanza.indexOf("</error>", errorStart) + 8
                    stanza.substring(errorStart, errorEnd)
                } else {
                    null
                }
            } else {
                null
            }
            val iqStart = stanza.indexOf("<iq")
            val headerEnd = stanza.indexOf(">", iqStart)
            val iqEnd = stanza.lastIndexOf("</iq>")
            val content = if (headerEnd != -1 && iqEnd > headerEnd + 1) stanza.substring(headerEnd + 1, iqEnd).trim() else ""
            val queryNamespace = if (content.isNotEmpty()) {
                val childStart = content.indexOf("<")
                if (childStart != -1) {
                    val childHeaderEnd = content.indexOf(">", childStart)
                    Regex("""xmlns=['"]([^'"]+)['"]""").find(content.substring(childStart, childHeaderEnd + 1))?.groupValues?.get(1)
                } else {
                    null
                }
            } else {
                null
            }
            XMPPIQ(
                raw = stanza,
                type = typeMatch,
                id = idMatch,
                from = fromMatch,
                to = toMatch,
                error = error,
                queryNamespace = queryNamespace,
                queryContent = content,
            )
        } catch (e: Exception) {
            Log.e(tag, "Error parsing IQ: ${e.message}, stanza=${stanza.take(500)}", e)
            null
        }
    }

    fun parseMessage(stanza: String): XMPPMessage? {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(stanza))

            var type: String? = null
            var id: String? = null
            var from: XMPPJID? = null
            var to: XMPPJID? = null
            var lang: String? = null
            var body: String? = null
            var originId: String? = null
            var archivedId: String? = null
            var queryId: String? = null
            var timestamp: Long? = null
            var realFrom: XMPPJID? = null
            var realTo: XMPPJID? = null
            var realId: String? = null
            var inForwarded = false
            var currentMessageDepth = 0
            var targetMessageDepth = -1
            val messageChildren = mutableListOf<XMLElement>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        val tagNs = parser.namespace ?: ""
                        when {
                            tagName == "message" -> {
                                currentMessageDepth++
                                if (currentMessageDepth == 1) {
                                    type = parser.getAttributeValue(null, "type") ?: "chat"
                                    id = parser.getAttributeValue(null, "id")
                                    from = parser.getAttributeValue(null, "from")?.let(::XMPPJID)
                                    to = parser.getAttributeValue(null, "to")?.let(::XMPPJID)
                                    lang = parser.getAttributeValue(null, "xml:lang")
                                }
                                if (inForwarded && targetMessageDepth == -1) {
                                    targetMessageDepth = currentMessageDepth
                                    realFrom = parser.getAttributeValue(null, "from")?.let(::XMPPJID) ?: from
                                    realTo = parser.getAttributeValue(null, "to")?.let(::XMPPJID) ?: to
                                    realId = parser.getAttributeValue(null, "id") ?: id
                                    type = parser.getAttributeValue(null, "type") ?: type ?: "chat"
                                }
                            }
                            tagName == "result" && tagNs == "urn:xmpp:mam:2" -> {
                                archivedId = parser.getAttributeValue(null, "id")
                                queryId = parser.getAttributeValue(null, "queryid")
                            }
                            tagName == "forwarded" && tagNs == "urn:xmpp:forward:0" -> {
                                inForwarded = true
                            }
                            tagName == "body" &&
                                (currentMessageDepth == targetMessageDepth || targetMessageDepth == -1) -> {
                                val text = parser.nextText().trim()
                                if (text.isNotBlank()) body = text
                            }
                            else -> {
                                val isAtMessageLevel =
                                    (currentMessageDepth == 1 && !inForwarded) ||
                                        (inForwarded && currentMessageDepth == targetMessageDepth)
                                when (tagName) {
                                    "delay" -> if (tagNs == "urn:xmpp:delay") {
                                        parser.getAttributeValue(null, "stamp")?.let { timestamp = it.parseXMPPDateToMillis() ?: timestamp }
                                    }
                                    "time" -> if (tagNs == "https://xabber.com/protocol/delivery") {
                                        parser.getAttributeValue(null, "stamp")?.let {
                                            val parsed = it.parseXMPPDateToMillis()
                                            if (parsed != null && (timestamp == null || parsed > timestamp!!)) {
                                                timestamp = parsed
                                            }
                                        }
                                    }
                                    "origin-id" -> if (tagNs == "urn:xmpp:sid:0") {
                                        originId = parser.getAttributeValue(null, "id")
                                    }
                                }
                                val element = parseElementFull(parser, tagName, tagNs)
                                if (isAtMessageLevel) {
                                    messageChildren.add(element)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "message") currentMessageDepth--
                        if (parser.name == "forwarded") inForwarded = false
                    }
                }
                event = parser.next()
            }

            val finalFrom = realFrom ?: from
            val finalTo = realTo ?: to
            val finalId = realId ?: originId ?: id

            if (body == null) {
                val hasKnownExtension = stanza.contains("urn:xmpp:chat-markers:0") ||
                    stanza.contains("http://jabber.org/protocol/chatstates") ||
                    stanza.contains("urn:xmpp:receipt") ||
                    stanza.contains("urn:xmpp:carbons") ||
                    stanza.contains("https://xabber.com/protocol/groups") ||
                    type == "headline" || type == "error"
                if (!hasKnownExtension) return null
            }

            if (timestamp == null && stanza.contains("<delay")) {
                val delayMatch = Regex("""<delay[^>]+stamp=['"]([^'"]+)['"]""").find(stanza)
                timestamp = delayMatch?.groupValues?.get(1)?.parseXMPPDateToMillis()
            }

            XMPPMessage(
                raw = stanza,
                type = type,
                id = finalId,
                from = finalFrom,
                to = finalTo,
                lang = lang,
                date = timestamp,
                body = body,
                originId = originId,
                archivedId = archivedId ?: queryId?.let { "query:$it" },
                children = messageChildren,
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse message stanza: ${e.message}\nStanza: ${stanza.take(1000)}", e)
            null
        }
    }

    fun extractFallbackMessage(stanza: String): XMPPMessage? {
        val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(stanza)
        val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(stanza)
        val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(stanza)
        val bodyMatch = Regex("""<body[^>]*>([^<]+)</body>""", RegexOption.DOT_MATCHES_ALL).find(stanza)
        val originMatch = Regex("""<origin-id[^>]+id=['"]([^'"]+)['"]""").find(stanza)
        val archivedMatch = Regex("""<archived[^>]+id=['"]([^'"]+)['"]""").find(stanza)
        val stampMatch = Regex("""stamp=['"]([^'"]+)['"]""").find(stanza)

        val id = idMatch?.groupValues?.get(1) ?: return null
        val fromStr = fromMatch?.groupValues?.get(1) ?: return null
        val toStr = toMatch?.groupValues?.get(1) ?: return null
        val body = bodyMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val originId = originMatch?.groupValues?.get(1)
        val archivedId = archivedMatch?.groupValues?.get(1) ?: ""
        val timestamp = stampMatch?.groupValues?.get(1)?.parseXMPPDateToMillis() ?: System.currentTimeMillis()

        return XMPPMessage(
            raw = stanza,
            type = "chat",
            id = id,
            from = try { XMPPJID(fromStr) } catch (_: Exception) { return null },
            to = try { XMPPJID(toStr) } catch (_: Exception) { return null },
            date = timestamp,
            body = body,
            originId = originId,
            archivedId = archivedId,
        )
    }

    private fun parseElementFull(parser: XmlPullParser, elementName: String, elementNs: String): XMLElement {
        val attributes = mutableMapOf<String, String>()
        for (i in 0 until parser.attributeCount) {
            attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
        }

        if (parser.isEmptyElementTag) {
            return XMLElement(
                name = elementName,
                namespace = elementNs,
                raw = "<$elementName/>",
                attributes = attributes,
                children = emptyList(),
            )
        }

        val children = mutableListOf<XMLElement>()
        val textBuilder = StringBuilder()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val child = parseElementFull(parser, parser.name, parser.namespace ?: "")
                    children.add(child)
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrBlank()) {
                        textBuilder.append(text.trim())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == elementName) {
                        val rawContent = if (textBuilder.isNotEmpty()) {
                            "<$elementName>${textBuilder}</$elementName>"
                        } else {
                            "<$elementName/>"
                        }
                        return XMLElement(
                            name = elementName,
                            namespace = elementNs,
                            raw = rawContent,
                            attributes = attributes,
                            children = children,
                        )
                    }
                }
            }
            event = parser.next()
        }

        return XMLElement(
            name = elementName,
            namespace = elementNs,
            raw = "<$elementName/>",
            attributes = attributes,
            children = children,
        )
    }
}
