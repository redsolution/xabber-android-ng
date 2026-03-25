package com.xabber.xmpp.XEP_0CCC

import android.util.Log
import com.xabber.account.AccountManager
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.domain.sync.model.GapFillRequest
import com.xabber.domain.sync.repository.GapFillPort
import com.xabber.domain.sync.repository.SyncRepository
import java.util.Date

class MamGapFillAdapter(
    private val owner: String,
    private val repo: SyncRepository,
) : GapFillPort {

    private val TAG = "MamGapFillAdapter"

    override suspend fun requestArchive(
        request: GapFillRequest,
        owner: String,
        onComplete: suspend () -> Unit,
    ) {
        val account = AccountManager.find(owner) ?: run {
            Log.w(TAG, "requestArchive: account not found for $owner")
            return
        }
        val stream = account.stream ?: run {
            Log.w(TAG, "requestArchive: stream not available for $owner")
            return
        }
        val mam = account.messageArchiveManager ?: run {
            Log.w(TAG, "requestArchive: messageArchiveManager not available for $owner")
            return
        }

        val conversationType = ConversationType.values()
            .firstOrNull { it.rawValue == request.conversationType }
            ?: ConversationType.Regular

        Log.d(TAG, "Filling gap for ${request.jid}, " +
                "from=${Date(request.localLastMessageDateMs)} to=${Date(request.serverLastMessageDateMs)}")

        try {
            mam.requestArchive(
                stream = stream,
                jid = request.jid,
                conversationType = conversationType,
                start = Date(request.localLastMessageDateMs),
                end = Date(request.serverLastMessageDateMs),
                rsmBefore = "",
                backward = true,
                callback = {
                    kotlinx.coroutines.runBlocking { onComplete() }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gap fill error for ${request.jid}: ${e.message}", e)
        }
    }
}
