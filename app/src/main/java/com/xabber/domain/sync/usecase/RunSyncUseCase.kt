package com.xabber.domain.sync.usecase

import com.xabber.domain.sync.model.GapFillRequest
import com.xabber.domain.sync.model.SyncPage
import com.xabber.domain.sync.repository.SyncRepository

class RunSyncUseCase(
    // Lambda instead of full SyncProtocolSender dependency — avoids circular import at this layer
    private val sendRequest: suspend (version: String, after: String?) -> Unit,
    private val processPage: ProcessSyncPageUseCase,
    private val fillGaps: FillGapsUseCase,
    private val repo: SyncRepository,
) {
    private val pendingGaps = mutableListOf<GapFillRequest>()

    suspend fun start(owner: String) {
        pendingGaps.clear()
        repo.resetGapFlags(owner)
        sendRequest(repo.getVersion(owner), null)
    }

    suspend fun onPageReceived(page: SyncPage, owner: String) {
        if (page.isFullPage) {
            sendRequest(repo.getVersion(owner), page.lastStamp)
        }
        val gaps = processPage.execute(page, owner)
        repo.saveVersion(owner, page.stamp)
        pendingGaps.addAll(gaps)

        if (!page.isFullPage && pendingGaps.isNotEmpty()) {
            fillGaps.execute(pendingGaps.toList(), owner)
            pendingGaps.clear()
        }
    }
}
