package com.xabber.xmpp.abstract

import android.util.Log
import java.util.Collections


abstract class AbstractXMPPManager(val owner: String) {

    companion object {
        private const val TAG = "AbstractXMPPManager"
    }

    private val queryIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    open fun namespaces(): List<String> = listOf("")

    open fun getPrimaryNamespace(): String = ""


    open fun onStreamPrepared(stream: XMPPStream) {
    }


    open fun read(iq: XMPPIQ): Boolean {
        val elementId = iq.elementId ?: return false
        synchronized(queryIds) {
            if (!queryIds.contains(elementId)) return false
            queryIds.remove(elementId)
            return true
        }
    }


    open fun clearSession() {
        synchronized(queryIds) {
            queryIds.clear()
        }
    }


    fun close() {
        clearSession()
        Log.d(TAG, "Closed manager for owner: $owner")
    }

    protected fun addQueryId(id: String) {
        synchronized(queryIds) {
            queryIds.add(id)
        }
    }
}

// Placeholder types (replace with your XMPP library)
data class XMPPStream(val myJID: XMPPJID? = null)

data class XMPPIQ(val elementId: String?)

data class XMPPJID(val bare: String)