package com.xabber.domain.sync.usecase

import com.xabber.domain.sync.model.SyncPage
import com.xabber.domain.sync.repository.SyncRepository

class ProcessPushUpdateUseCase(
    private val processPage: ProcessSyncPageUseCase,
    private val fillGaps: FillGapsUseCase,
    private val repo: SyncRepository,
) {
    suspend fun execute(page: SyncPage, owner: String) {
        val gaps = processPage.execute(page, owner)
        repo.saveVersion(owner, page.stamp)
        if (gaps.isNotEmpty()) {
            fillGaps.execute(gaps, owner)
        }
    }
}
