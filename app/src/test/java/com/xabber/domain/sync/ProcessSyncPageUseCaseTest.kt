package com.xabber.domain.sync

import com.xabber.domain.sync.model.*
import com.xabber.domain.sync.repository.SyncRepository
import com.xabber.domain.sync.usecase.DetermineMessageStateUseCase
import com.xabber.domain.sync.usecase.MergeSyncMarkersUseCase
import com.xabber.domain.sync.usecase.ProcessSyncPageUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessSyncPageUseCaseTest {

    // Fake repo — records what was passed to applyBatch
    open class FakeRepo : SyncRepository {
        val batches = mutableListOf<List<ConversationWrite>>()
        override suspend fun getVersion(owner: String) = "0"
        override suspend fun saveVersion(owner: String, version: String) {}
        override suspend fun resetGapFlags(owner: String) {}
        override suspend fun getConversation(owner: String, jid: String, type: String): StoredConversation? = null
        override suspend fun getRosterJids(owner: String): Set<String> = emptySet()
        override suspend fun applyBatch(owner: String, writes: List<ConversationWrite>) { batches += writes }
        override suspend fun markGapFixed(owner: String, jid: String, type: String) {}
        override fun close() {}
    }

    private fun makeUseCase(repo: SyncRepository = FakeRepo()) = ProcessSyncPageUseCase(
        determineState = DetermineMessageStateUseCase(),
        mergeMarkers = MergeSyncMarkersUseCase(),
        repo = repo,
    )

    private fun conv(jid: String, status: SyncStatus = SyncStatus.ACTIVE) = SyncConversation(
        jid = jid, type = "urn:xabber:chat", stamp = "1000",
        status = status, pinned = 0, muteUntilMs = -1L,
        markers = SyncMarkers(0, null, null, null),
        lastMessage = null,
    )

    @Test
    fun `deleted conversation produces Delete write`() = runBlocking {
        val repo = FakeRepo()
        val page = SyncPage("1000", listOf(conv("romeo@m.com", SyncStatus.DELETED)), false)
        makeUseCase(repo).execute(page, "owner@m.com")
        val write = repo.batches.single().single()
        assertTrue(write is ConversationWrite.Delete)
        assertEquals("romeo@m.com", (write as ConversationWrite.Delete).jid)
    }

    @Test
    fun `active conversation produces Upsert write`() = runBlocking {
        val repo = FakeRepo()
        val page = SyncPage("1000", listOf(conv("romeo@m.com", SyncStatus.ACTIVE)), false)
        makeUseCase(repo).execute(page, "owner@m.com")
        val write = repo.batches.single().single()
        assertTrue(write is ConversationWrite.Upsert)
    }

    @Test
    fun `createRosterIfMissing true when jid not in existing roster`() = runBlocking {
        val repo = FakeRepo() // getRosterJids returns emptySet
        val page = SyncPage("1000", listOf(conv("new@m.com")), false)
        makeUseCase(repo).execute(page, "owner@m.com")
        val upsert = repo.batches.single().single() as ConversationWrite.Upsert
        assertTrue(upsert.createRosterIfMissing)
    }

    @Test
    fun `createRosterIfMissing false when jid already in roster`() = runBlocking {
        val repo = object : FakeRepo() {
            override suspend fun getRosterJids(owner: String) = setOf("known@m.com")
        }
        val page = SyncPage("1000", listOf(conv("known@m.com")), false)
        makeUseCase(repo).execute(page, "owner@m.com")
        val upsert = repo.batches.single().single() as ConversationWrite.Upsert
        assertTrue(!upsert.createRosterIfMissing)
    }

    @Test
    fun `markersChanged true when no existing conversation`() = runBlocking {
        val repo = FakeRepo()
        val page = SyncPage("1000", listOf(conv("new@m.com")), false)
        makeUseCase(repo).execute(page, "owner@m.com")
        val upsert = repo.batches.single().single() as ConversationWrite.Upsert
        assertTrue(upsert.markersChanged)
    }

    @Test
    fun `gap detected when server message is newer than local`() = runBlocking {
        val repo = object : FakeRepo() {
            override suspend fun getConversation(owner: String, jid: String, type: String) =
                StoredConversation(jid, type,
                    SyncMarkers(0, null, null, null),
                    lastMessageDateMs = 1_000L,
                    isGapFixedForSession = false,
                    lastMessageState = null,
                )
        }
        val convWithMsg = conv("romeo@m.com").copy(
            stamp = "10000",
            lastMessage = SyncMessage("id1", "romeo@m.com", "hi", 10_000_000L, false, null)
        )
        val page = SyncPage("10000", listOf(convWithMsg), false)
        val gaps = makeUseCase(repo).execute(page, "owner@m.com")
        assertEquals(1, gaps.size)
        assertEquals("romeo@m.com", gaps[0].jid)
    }

    @Test
    fun `applyBatch called once for entire page`() = runBlocking {
        val repo = FakeRepo()
        val page = SyncPage("1000", listOf(conv("a@m.com"), conv("b@m.com"), conv("c@m.com")), false)
        makeUseCase(repo).execute(page, "owner@m.com")
        assertEquals(1, repo.batches.size)         // single batch call
        assertEquals(3, repo.batches[0].size)       // 3 writes in one batch
    }
}
