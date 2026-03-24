package com.xabber.xmpp.core.module

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class AbstractXmppModule(
    val owner: String,
) {
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queryIds = mutableSetOf<String>()
    private val mutex = Mutex()

    open val priority: Int = 0

    open suspend fun clearSession() {
        mutex.withLock {
            queryIds.clear()
        }
    }

    open suspend fun onSessionReady() = Unit

    abstract fun namespaces(): List<String>

    protected suspend fun trackQueryId(id: String) {
        mutex.withLock {
            queryIds.add(id)
        }
    }

    protected suspend fun consumeTrackedQueryId(id: String): Boolean {
        return mutex.withLock {
            queryIds.remove(id)
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
