package com.xabber.xmpp.messages

import com.xabber.xmpp.jid.XMPPJID

data class XMPPMessage(
    val raw: String,
    val type: String? = null,
    val id: String? = null,
    val from: XMPPJID? = null,
    val to: XMPPJID? = null,
    val lang: String? = null,
    val body: String? = null,
    val subject: String? = null,
    val thread: String? = null,
    val error: String? = null,
    val children: List<XMLElement> = emptyList()
) {
    private val elements = mutableMapOf<String, MutableList<XMLElement>>()

    fun element(name: String, namespace: String? = null): XMLElement? {
        return elements[name]?.firstOrNull { it.namespace == namespace || namespace == null }
    }

    fun hasElement(name: String, namespace: String? = null): Boolean {
        return elements[name]?.any { it.namespace == namespace || namespace == null } ?: false
    }

    fun addElement(element: XMLElement) {
        elements.getOrPut(element.name) { mutableListOf() }.add(element)
    }
}

data class XMLElement(
    val name: String,
    val namespace: String? = null,
    val raw: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<XMLElement> = emptyList()
) {
    fun element(name: String, namespace: String? = null): XMLElement? {
        return children.firstOrNull { it.name == name && (it.namespace == namespace || namespace == null) }
    }

    fun elements(name: String): List<XMLElement> {
        return children.filter { it.name == name }
    }

    fun getAttribute(name: String): String? {
        return attributes[name]
    }
}