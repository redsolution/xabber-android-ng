package com.xabber.domain.sync.usecase

import com.xabber.domain.sync.model.GapFillRequest
import com.xabber.domain.sync.repository.GapFillPort
import com.xabber.domain.sync.repository.SyncRepository

open class FillGapsUseCase(
    private val gapFillPort: GapFillPort,
    private val repo: SyncRepository,
) {
    open suspend fun execute(requests: List<GapFillRequest>, owner: String) {
        for (req in requests) {
            try {
                gapFillPort.requestArchive(req, owner) {
                    repo.markGapFixed(owner, req.jid, req.conversationType)
                }
            } catch (e: Exception) {
                android.util.Log.e("FillGapsUseCase", "Gap fill failed for ${req.jid}: ${e.message}")
            }
        }
    }
}
