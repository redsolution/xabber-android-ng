package com.xabber.xmpp.messages

import com.xabber.xmpp.jid.XMPPJID

data class XMPPMessage(
    val raw: String,
    val type: String? = null,
    val id: String? = null,
    val from: XMPPJID? = null,
    val to: XMPPJID? = null,
    val lang: String? = null,
    val date: Long? = 0,
    val body: String? = null,
    val subject: String? = null,
    val thread: String? = null,
    val error: String? = null,
    val children: List<XMLElement> = emptyList(),
    var originId: String? = null
) {
    private val elements = mutableMapOf<String, MutableList<XMLElement>>().apply {
        children.forEach { child ->
            getOrPut(child.name) { mutableListOf() }.add(child)
        }
    }

    fun element(name: String, namespace: String? = null): XMLElement? {
        return elements[name]?.firstOrNull { it.namespace == namespace || namespace == null }
    }

    fun elements(name: String, namespace: String? = null): List<XMLElement> {
        return elements[name]?.filter { it.namespace == namespace || namespace == null } ?: emptyList()
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
    // Рекурсивно получаем текстовое содержимое
    val textContent: String?
        get() = if (children.isEmpty()) {
            // Попробуем извлечь текст из raw XML (между тегами)
            raw.let {
                val start = it.indexOf('>') + 1
                val end = it.lastIndexOf('<')
                if (start in 0 until end) it.substring(start, end).trim() else null
            }
        } else {
            null
        }

    // Удобные методы
    fun element(name: String, namespace: String? = null): XMLElement? =
        children.firstOrNull { it.name == name && (it.namespace == namespace || namespace == null) }

    fun elements(name: String, namespace: String? = null): List<XMLElement> =
        children.filter { it.name == name && (it.namespace == namespace || namespace == null) }

    fun getAttribute(name: String): String? = attributes[name]
}