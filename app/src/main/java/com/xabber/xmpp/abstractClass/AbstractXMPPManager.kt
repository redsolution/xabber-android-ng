package com.xabber.xmpp.abstractClass

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class AbstractXMPPManager(val owner: String) {
    val queryIds = mutableSetOf<String>()
    private val mutex = Mutex()
    protected val tag = "AbstractXMPPManager"

    /**
     * Called when the stream is prepared (e.g., after authentication).
     */
    open suspend fun onStreamPrepared() {
        // Optional override
    }

    /**
     * Process an incoming IQ stanza. Returns true if the IQ was handled by this manager.
     * The default implementation extracts the IQ ID and checks if it matches a pending query.
     * Subclasses should override and call [checkAndRemoveQueryId] after their own parsing.
     */
    open suspend fun read(iq: String): Boolean {
        // Basic ID extraction – subclasses may override for full parsing
        val id = extractIqId(iq) ?: return false
        return checkAndRemoveQueryId(id)
    }

    /**
     * Extracts the 'id' attribute from an IQ stanza. Returns null if not found.
     */
    private fun extractIqId(iq: String): String? {
        val idPattern = "<iq[^>]*\\s+id=['\"]([^'\"]+)['\"]".toRegex()
        return idPattern.find(iq)?.groupValues?.get(1)
    }

    /**
     * Checks if the given element ID is tracked as a pending query and removes it.
     * Returns true if the ID was found and removed.
     */
    protected suspend fun checkAndRemoveQueryId(elementId: String): Boolean {
        mutex.withLock {
            return if (queryIds.contains(elementId)) {
                queryIds.remove(elementId)
                Log.d(tag, "Removed query ID: $elementId for owner $owner")
                true
            } else {
                false
            }
        }
    }

    /**
     * Adds a query ID to track.
     */
    protected suspend fun addQueryId(elementId: String) {
        mutex.withLock {
            queryIds.add(elementId)
        }
    }

    /**
     * Clears all session data (e.g., on stream disconnect).
     */
    open suspend fun clearSession() {
        mutex.withLock {
            queryIds.clear()
        }
        Log.d(tag, "Session cleared for owner $owner")
    }

    abstract fun namespaces(): List<String>
    abstract fun getPrimaryNamespace(): String
}