package com.xabber.xmpp.XEP_0CCC

import android.util.Log
import com.xabber.common.SettingManager
import com.xabber.data.sync.SyncRepositoryImpl
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.domain.sync.usecase.DetermineMessageStateUseCase
import com.xabber.domain.sync.usecase.FillGapsUseCase
import com.xabber.domain.sync.usecase.MergeSyncMarkersUseCase
import com.xabber.domain.sync.usecase.ProcessPushUpdateUseCase
import com.xabber.domain.sync.usecase.ProcessSyncPageUseCase
import com.xabber.domain.sync.usecase.RunSyncUseCase
import com.xabber.stream.Stream
import io.realm.kotlin.Realm

class ClientSynchronizationManager(private val owner: String) {

    var isAvailable: Boolean = true
    var mentionId: String? = null
    var retractVersion: String? = null
    var boundJid: String? = null

    private val realm = Realm.open(defaultRealmConfig())
    private val repo = SyncRepositoryImpl(realm)
    private val sender = SyncProtocolSender()
    private val parser = SyncProtocolParser()

    private val determineState = DetermineMessageStateUseCase()
    private val mergeMarkers = MergeSyncMarkersUseCase()
    private val processPage = ProcessSyncPageUseCase(determineState, mergeMarkers, repo)

    private val gapFillPort = MamGapFillAdapter(owner, repo)
    private val fillGaps = FillGapsUseCase(gapFillPort, repo)

    private val runSync = RunSyncUseCase(
        sendRequest = { version, after ->
            val stream = com.xabber.account.AccountManager.find(owner)?.stream ?: run {
                Log.w(TAG, "sendRequest: stream unavailable for $owner")
                return@RunSyncUseCase
            }
            sender.sendSyncRequest(stream, owner, boundJid, version, after)
        },
        processPage = processPage,
        fillGaps = fillGaps,
        repo = repo,
    )

    private val processPush = ProcessPushUpdateUseCase(processPage, fillGaps, repo)

    // Called by Account after stream features are discovered
    suspend fun sync(stream: Stream, customVer: String? = null, after: String? = null, boundJid: String? = null): Boolean {
        if (boundJid != null) this.boundJid = boundJid
        runSync.start(owner)
        return true
    }

    // Called by Account when a complete sync IQ arrives (result or set)
    suspend fun read(iq: String) {
        val isPush = iq.contains("type='set'") || iq.contains("type=\"set\"")
        val page = if (isPush) parser.parsePush(iq) else parser.parseSnapshot(iq)
        if (page == null) {
            Log.w(TAG, "Failed to parse sync IQ, skipping")
            return
        }
        if (page.isPush) processPush.execute(page, owner)
        else runSync.onPageReceived(page, owner)
    }

    // Mutation methods — delegate directly to sender
    suspend fun muteConversation(stream: Stream, chatJid: String, type: ConversationType, muteSeconds: Long) =
        sender.sendMuteConversation(stream, owner, boundJid, chatJid, type, muteSeconds)

    suspend fun unmuteConversation(stream: Stream, chatJid: String, type: ConversationType) =
        sender.sendUnmuteConversation(stream, owner, boundJid, chatJid, type)

    suspend fun pinChat(stream: Stream, chatId: String, type: ConversationType) =
        sender.sendPinChat(stream, owner, boundJid, chatId, type)

    suspend fun update(stream: Stream, chatId: String, type: ConversationType, status: String? = null, mute: Double? = null) =
        sender.sendUpdate(stream, owner, boundJid, chatId, type, status, mute)

    // Called by Account.cleanup() to reset sync version
    fun clear() {
        SettingManager.saveClientSynchronizationVersion(owner, "0")
        Log.d(TAG, "Sync version reset to 0 for owner $owner")
    }

    fun reset() {
        repo.close()
        Log.d(TAG, "Reset and closed Realm for owner $owner")
    }

    companion object {
        private const val TAG = "ClientSyncManager"
    }
}
