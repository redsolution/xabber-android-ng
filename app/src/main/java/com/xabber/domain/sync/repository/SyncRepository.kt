package com.xabber.domain.sync.repository

import com.xabber.domain.sync.model.ConversationWrite
import com.xabber.domain.sync.model.StoredConversation

interface SyncRepository {
    // Stored in SharedPreferences (SettingManager), not Realm — survives Realm clears.
    suspend fun getVersion(owner: String): String
    suspend fun saveVersion(owner: String, version: String)

    suspend fun resetGapFlags(owner: String)
    suspend fun getConversation(owner: String, jid: String, type: String): StoredConversation?
    suspend fun getRosterJids(owner: String): Set<String>
    suspend fun applyBatch(owner: String, writes: List<ConversationWrite>)
    suspend fun markGapFixed(owner: String, jid: String, type: String)
    fun close()
}
