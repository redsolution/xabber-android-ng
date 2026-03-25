package com.xabber.domain.sync

import com.xabber.domain.sync.model.*
import com.xabber.domain.sync.repository.SyncRepository
import com.xabber.domain.sync.usecase.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSyncUseCaseTest {

    open class FakeRepo : SyncRepository {
        var version = "0"
        var gapResetCalled = false
        var savedVersion: String? = null
        val batches = mutableListOf<List<ConversationWrite>>()
        override suspend fun getVersion(owner: String) = version
        override suspend fun saveVersion(owner: String, v: String) { savedVersion = v }
        override suspend fun resetGapFlags(owner: String) { gapResetCalled = true }
        override suspend fun getConversation(owner: String, jid: String, type: String): StoredConversation? = null
        override suspend fun getRosterJids(owner: String) = emptySet<String>()
        override suspend fun applyBatch(owner: String, writes: List<ConversationWrite>) { batches += writes }
        override suspend fun markGapFixed(owner: String, jid: String, type: String) {}
        override fun close() {}
    }

    class FakeSender {
        val requests = mutableListOf<Pair<String?, String?>>()
        val send: suspend (version: String, after: String?) -> Unit = { v, a -> requests += v to a }
    }

    class FakeFillGaps : FillGapsUseCase(
        gapFillPort = object : com.xabber.domain.sync.repository.GapFillPort {
            override suspend fun requestArchive(request: GapFillRequest, owner: String, onComplete: suspend () -> Unit) {}
        },
        repo = object : SyncRepository {
            override suspend fun getVersion(owner: String) = "0"
            override suspend fun saveVersion(owner: String, version: String) {}
            override suspend fun resetGapFlags(owner: String) {}
            override suspend fun getConversation(owner: String, jid: String, type: String): StoredConversation? = null
            override suspend fun getRosterJids(owner: String) = emptySet<String>()
            override suspend fun applyBatch(owner: String, writes: List<ConversationWrite>) {}
            override suspend fun markGapFixed(owner: String, jid: String, type: String) {}
            override fun close() {}
        }
    ) {
        val executedWith = mutableListOf<List<GapFillRequest>>()
        override suspend fun execute(requests: List<GapFillRequest>, owner: String) { executedWith += requests }
    }

    private fun page(count: Int, stamp: String = "1000", isPush: Boolean = false): SyncPage {
        val convs = (1..count).map { i ->
            SyncConversation("jid$i@m.com", "urn:xabber:chat", "${1000 + i}",
                SyncStatus.ACTIVE, 0L, -1L, SyncMarkers(0, null, null, null), null)
        }
        return SyncPage(stamp, convs, isPush)
    }

    @Test
    fun `start resets gap flags and sends first request`() = runBlocking {
        val repo = FakeRepo()
        val sender = FakeSender()
        val fillGaps = FakeFillGaps()
        val processPage = ProcessSyncPageUseCase(DetermineMessageStateUseCase(), MergeSyncMarkersUseCase(), repo)
        val useCase = RunSyncUseCase(sender.send, processPage, fillGaps, repo)
        useCase.start("owner@m.com")
        assertTrue(repo.gapResetCalled)
        assertEquals(1, sender.requests.size)
        assertEquals(null, sender.requests[0].second) // after = null on first request
    }

    @Test
    fun `full page triggers pipelined next-page request`() = runBlocking {
        val repo = FakeRepo()
        val sender = FakeSender()
        val fillGaps = FakeFillGaps()
        val processPage = ProcessSyncPageUseCase(DetermineMessageStateUseCase(), MergeSyncMarkersUseCase(), repo)
        val useCase = RunSyncUseCase(sender.send, processPage, fillGaps, repo)
        val fullPage = page(SYNC_PAGE_SIZE, stamp = "2000")
        useCase.onPageReceived(fullPage, "owner@m.com")
        assertEquals(1, sender.requests.size)
        assertEquals(fullPage.lastStamp, sender.requests[0].second)
    }

    @Test
    fun `partial page does not trigger pipelining`() = runBlocking {
        val repo = FakeRepo()
        val sender = FakeSender()
        val fillGaps = FakeFillGaps()
        val processPage = ProcessSyncPageUseCase(DetermineMessageStateUseCase(), MergeSyncMarkersUseCase(), repo)
        val useCase = RunSyncUseCase(sender.send, processPage, fillGaps, repo)
        useCase.onPageReceived(page(3), "owner@m.com")
        assertEquals(0, sender.requests.size)
    }

    @Test
    fun `gaps dispatched only on final page`() = runBlocking {
        val repo = object : FakeRepo() {
            override suspend fun getConversation(owner: String, jid: String, type: String) =
                StoredConversation(jid, type, SyncMarkers(0, null, null, null), 1_000L, false, null)
        }
        val sender = FakeSender()
        val fillGaps = FakeFillGaps()
        val processPage = ProcessSyncPageUseCase(DetermineMessageStateUseCase(), MergeSyncMarkersUseCase(), repo)
        val useCase = RunSyncUseCase(sender.send, processPage, fillGaps, repo)

        // Full page with gap — should NOT dispatch yet
        val convWithMsg = SyncConversation("a@m.com", "urn:xabber:chat", "9000",
            SyncStatus.ACTIVE, 0L, -1L, SyncMarkers(0, null, null, null),
            SyncMessage("id1", "a@m.com", "hi", 9_000_000L, false, null))
        useCase.onPageReceived(SyncPage("9000", List(SYNC_PAGE_SIZE) { convWithMsg }, false), "owner@m.com")
        assertEquals(0, fillGaps.executedWith.size)

        // Final (partial) page — should dispatch accumulated gaps
        useCase.onPageReceived(page(1, stamp = "9001"), "owner@m.com")
        assertEquals(1, fillGaps.executedWith.size)
    }

    @Test
    fun `version saved after each page`() = runBlocking {
        val repo = FakeRepo()
        val sender = FakeSender()
        val fillGaps = FakeFillGaps()
        val processPage = ProcessSyncPageUseCase(DetermineMessageStateUseCase(), MergeSyncMarkersUseCase(), repo)
        val useCase = RunSyncUseCase(sender.send, processPage, fillGaps, repo)
        useCase.onPageReceived(page(3, stamp = "5000"), "owner@m.com")
        assertEquals("5000", repo.savedVersion)
    }
}
