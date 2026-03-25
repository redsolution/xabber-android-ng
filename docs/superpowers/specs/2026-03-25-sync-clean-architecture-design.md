# Sync Clean Architecture — Design Spec

**Date:** 2026-03-25
**Branch:** db
**Status:** Approved

---

## Overview

Refactor the XEP-0CCC client synchronization system from a single ~1000-line god class (`ClientSynchronizationManager`) into a clean three-layer architecture: protocol, domain, and data. All three incoming paths — paginated sync, IQ-set server push, and message-stanza sync — are unified through shared domain use cases. Page size is set to 60.

---

## Goals

- Clean separation: protocol (XML/network), domain (business logic), data (Realm)
- Single Realm transaction per sync page (batch writes)
- Page size = 60
- All three incoming paths share `ProcessSyncPageUseCase`
- Domain use cases are pure Kotlin — no Realm, no Android, fully unit-testable
- Pipelining preserved: next page request sent before current page is written

---

## Non-Goals

- Changes to the MAM gap-fill implementation internals
- Changes to any other XEP manager
- UI layer changes

---

## Incoming Paths (Three, Not Two)

The existing `ClientSynchronizationManager` has three distinct entry points:

1. **Paginated sync** — IQ `type=result` response to our `type=get` request, routed via `SyncIqModule`
2. **IQ-set push** — IQ `type=set` server push, also routed via `SyncIqModule`, acknowledged immediately
3. **Message-stanza sync** — a `<message>` stanza containing `<last-message xmlns='https://xabber.com/protocol/synchronization'>`, routed via `MessageManager+CommonReceiver` → `receiveClientSyncRaw()`

All three paths produce a `SyncPage` (with `isPush=true` for paths 2 and 3) and then call `ProcessSyncPageUseCase`. `receiveClientSyncRaw()` is **in scope** and moves to the protocol layer as `SyncProtocolParser.parseMessageStanza()`.

---

## Architecture

### Layer Boundaries

```
┌─────────────────────────────────────────────┐
│  PROTOCOL LAYER  (xmpp/XEP_0CCC/)           │
│  • Parses raw XML → SyncPage domain models  │
│  • Sends IQ stanzas + mutation IQs          │
│  • Routes all three incoming paths          │
│  No Realm. No business logic.               │
└─────────────────┬───────────────────────────┘
                  │ domain models
┌─────────────────▼───────────────────────────┐
│  DOMAIN LAYER  (domain/sync/)               │
│  • Use cases: RunSync, ProcessPage,         │
│    ProcessPush, FillGaps                    │
│  • Merge rules, state determination         │
│  • SyncRepository interface                 │
│  No Realm. No XML. Pure Kotlin.             │
└─────────────────┬───────────────────────────┘
                  │ via interface
┌─────────────────▼───────────────────────────┐
│  DATA LAYER  (data/sync/)                   │
│  • SyncRepositoryImpl (Realm)               │
│  • Single batch write per page              │
│  • Mappers: domain ↔ Realm models           │
│  Only layer that touches Realm.             │
└─────────────────────────────────────────────┘
```

---

## File Structure

```
com/xabber/
│
├── xmpp/XEP_0CCC/                          # Protocol layer (slimmed)
│   ├── ClientSynchronizationManager.kt     # ~60 lines, thin coordinator + mutation methods
│   ├── SyncIqModule.kt                     # thin router (existing, unchanged interface)
│   ├── SyncProtocolParser.kt               # NEW: raw XML → SyncPage (all 3 input shapes)
│   └── SyncProtocolSender.kt               # NEW: sync request + mutation IQs
│
├── domain/sync/                            # NEW: Domain layer
│   ├── model/
│   │   ├── SyncPage.kt                     # + SYNC_PAGE_SIZE = 60
│   │   ├── SyncConversation.kt
│   │   ├── SyncMarkers.kt
│   │   ├── SyncMessage.kt                  # + currentState field
│   │   ├── StoredConversation.kt
│   │   ├── ConversationWrite.kt            # sealed write instructions + markersChanged flag
│   │   └── GapFillRequest.kt
│   ├── usecase/
│   │   ├── RunSyncUseCase.kt               # orchestration + pipelining
│   │   ├── ProcessSyncPageUseCase.kt       # shared by all 3 paths
│   │   ├── ProcessPushUpdateUseCase.kt     # thin wrapper over ProcessSyncPageUseCase
│   │   ├── FillGapsUseCase.kt              # MAM gap-fill dispatch
│   │   ├── DetermineMessageStateUseCase.kt # pure: message state from markers + currentState
│   │   └── MergeSyncMarkersUseCase.kt      # pure: merge current + incoming markers
│   └── repository/
│       └── SyncRepository.kt              # interface only
│
└── data/sync/                              # NEW: Data layer
    ├── SyncRepositoryImpl.kt               # all Realm operations
    └── mapper/
        ├── SyncConversationMapper.kt       # SyncConversation → LastChatsStorageItem
        └── SyncMessageMapper.kt            # SyncMessage ↔ MessageStorageItem
```

---

## Domain Models

```kotlin
// SyncPage.kt
const val SYNC_PAGE_SIZE = 60

data class SyncPage(
    val stamp: String,
    val conversations: List<SyncConversation>,
    val isPush: Boolean,
) {
    // NOTE: if server returns exactly 60 on the final page, one extra empty request
    // will be sent. This is acceptable and matches current behaviour.
    val isFullPage get() = !isPush && conversations.size >= SYNC_PAGE_SIZE
    // NOTE: empty non-push page should not occur; if it does, after=stamp is sent
    // which will return an empty result and terminate the loop gracefully.
    val lastStamp get() = conversations.lastOrNull()?.stamp ?: stamp
}

// SyncConversation.kt
data class SyncConversation(
    val jid: String,
    val type: String,
    val stamp: String,
    val status: SyncStatus,           // ACTIVE, ARCHIVED, DELETED
    val pinned: Long,
    val muteUntilMs: Long,            // -1 = not muted, Long.MAX_VALUE = forever
    val markers: SyncMarkers,
    val lastMessage: SyncMessage?,
)

// SyncMarkers.kt
data class SyncMarkers(
    val unreadCount: Long,
    val unreadAfterUs: Long?,
    val displayedId: String?,
    val deliveredId: String?,
)

// SyncMessage.kt
data class SyncMessage(
    val id: String,
    val fromJid: String,
    val body: String,
    val timestampUs: Long,
    val isOutgoing: Boolean,
    val groupNickname: String?,       // non-null for group chat messages
    // Pre-fetched from Realm by ProcessSyncPageUseCase via repo.getConversation()
    // so DetermineMessageStateUseCase stays pure (no Realm access needed).
    val currentState: MessageSendingState? = null,
)

// StoredConversation.kt — returned by repository, no Realm types
data class StoredConversation(
    val jid: String,
    val type: String,
    val markers: SyncMarkers,
    val lastMessageDateMs: Long,
    val isGapFixedForSession: Boolean,
    val lastMessageState: MessageSendingState?,  // for passing to DetermineMessageStateUseCase
)

// ConversationWrite.kt — sealed instructions built in domain, executed in data layer
sealed class ConversationWrite {
    data class Upsert(
        val conv: SyncConversation,
        val mergedMarkers: SyncMarkers,
        val message: MessageUpdate?,
        val markersChanged: Boolean,    // if false, applyBatch() skips per-message state scan
        val createRosterIfMissing: Boolean,  // true when jid not in existing roster set
    ) : ConversationWrite()
    data class MessageUpdate(val msg: SyncMessage, val state: MessageStateResult)
    data class Delete(val jid: String, val type: String) : ConversationWrite()
}

data class MessageStateResult(val state: MessageSendingState, val isRead: Boolean)
```

---

## Domain Use Cases

### RunSyncUseCase
Orchestrates the full paginated sync lifecycle. Maintains pending gap requests across pages. Sends next page request (pipelining) before processing the current page.

`RunSyncUseCase` is **stateful per account** — it holds `pendingGaps` across multiple `onPageReceived()` calls within one sync session. It is constructed once per `ClientSynchronizationManager` instance (i.e., once per account) and lives for the account's lifetime. It does not own a coroutine scope; callers provide coroutine context via `suspend` functions. Thread safety: all calls to `start()` and `onPageReceived()` are serialised by `Account.kt`'s existing coroutine scope (`Dispatchers.IO`), matching the current `ClientSynchronizationManager` behaviour.

```kotlin
class RunSyncUseCase(
    private val sender: SyncProtocolSender,
    private val processPage: ProcessSyncPageUseCase,
    private val fillGaps: FillGapsUseCase,
    private val repo: SyncRepository,
) {
    private val pendingGaps = mutableListOf<GapFillRequest>()

    suspend fun start(stream: Stream, owner: String) {
        pendingGaps.clear()
        repo.resetGapFlags(owner)
        sender.sendSyncRequest(stream, owner, version = repo.getVersion(owner), after = null)
    }

    suspend fun onPageReceived(page: SyncPage, stream: Stream, owner: String) {
        if (page.isFullPage) {
            sender.sendSyncRequest(stream, owner, version = repo.getVersion(owner), after = page.lastStamp)
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
```

### ProcessSyncPageUseCase
Shared by all three incoming paths. Iterates conversations, pre-fetches existing state from repo, computes merged markers and message states, accumulates `ConversationWrite` instructions, calls `repo.applyBatch()` in a single call.

Pre-fetches the full roster JID set for the owner once (outside the write loop) to allow O(1) membership checks — preserving the existing optimisation.

```kotlin
class ProcessSyncPageUseCase(
    private val determineState: DetermineMessageStateUseCase,
    private val mergeMarkers: MergeSyncMarkersUseCase,
    private val repo: SyncRepository,
) {
    suspend fun execute(page: SyncPage, owner: String): List<GapFillRequest> {
        val gaps = mutableListOf<GapFillRequest>()
        val writes = mutableListOf<ConversationWrite>()
        val existingRosterJids = repo.getRosterJids(owner)  // pre-fetch once

        for (conv in page.conversations) {
            if (conv.status == SyncStatus.DELETED) {
                writes += ConversationWrite.Delete(conv.jid, conv.type)
                continue
            }
            val existing = repo.getConversation(owner, conv.jid, conv.type)
            val markersChanged = existing == null
                || existing.markers.displayedId != conv.markers.displayedId
                || existing.markers.deliveredId != conv.markers.deliveredId
                || existing.markers.unreadCount != conv.markers.unreadCount

            val message = conv.lastMessage?.let { msg ->
                val msgWithState = msg.copy(currentState = existing?.lastMessageState)
                val state = determineState.execute(msgWithState, conv.markers)
                ConversationWrite.MessageUpdate(msgWithState, state)
            }
            val merged = mergeMarkers.execute(existing?.markers, conv.markers, conv.lastMessage?.timestampUs)
            detectGap(existing, conv)?.let { gaps += it }
            writes += ConversationWrite.Upsert(
                conv = conv,
                mergedMarkers = merged,
                message = message,
                markersChanged = markersChanged,
                createRosterIfMissing = conv.jid !in existingRosterJids,
            )
        }

        repo.applyBatch(owner, writes)
        return gaps
    }
}
```

### ProcessPushUpdateUseCase
Thin wrapper: reuses `ProcessSyncPageUseCase`, dispatches gaps immediately (no accumulation — push is always a single conversation).

### DetermineMessageStateUseCase
Pure function. Takes `SyncMessage` (which carries `currentState` pre-fetched by the caller) and `SyncMarkers`. Prevents downgrading a message that is already `Read` or `Deliver` to a lower state.

```kotlin
class DetermineMessageStateUseCase {
    fun execute(msg: SyncMessage, markers: SyncMarkers): MessageStateResult {
        // uses msg.currentState to avoid downgrade
        // same logic as current determineMessageState(), now isolated and testable
    }
}
```

### MergeSyncMarkersUseCase
Pure function. Extracted directly from `ClientSynchronizationMergeRules.kt` — max-wins for IDs, timestamp-based for `lastReadMessageDate`.

### FillGapsUseCase
Receives a list of `GapFillRequest`, delegates to `MessageArchiveManager` (MAM). Marks `isHistoryGapFixedForSession = true` on completion via `SyncRepository`. No changes to MAM internals.

---

## Repository Interface

```kotlin
interface SyncRepository {
    // Version is stored in SettingManager (SharedPreferences), not Realm,
    // so it survives Realm clears. SyncRepositoryImpl delegates these two
    // methods to SettingManager — this is intentional and documented.
    suspend fun getVersion(owner: String): String
    suspend fun saveVersion(owner: String, version: String)

    suspend fun resetGapFlags(owner: String)
    suspend fun getConversation(owner: String, jid: String, type: String): StoredConversation?
    suspend fun getRosterJids(owner: String): Set<String>
    suspend fun applyBatch(owner: String, writes: List<ConversationWrite>)
    suspend fun markGapFixed(owner: String, jid: String, type: String)
}
```

**Version storage note:** `getVersion`/`saveVersion` delegate to `SettingManager` (SharedPreferences) inside `SyncRepositoryImpl`. This preserves existing behaviour where sync version survives Realm clears. The data layer is still the only caller — `SettingManager` is not accessed from protocol or domain layers.

---

## Data Layer

### SyncRepositoryImpl

Implements `SyncRepository`. Only class that opens or queries Realm (except for version, which uses `SettingManager` as noted above).

**`applyBatch()` behaviour:**
1. Pre-reads nothing — all decisions were made in the domain layer
2. Opens a single `realm.write { }` block
3. For each `ConversationWrite.Upsert`:
   - Creates `RosterStorageItem` if `createRosterIfMissing = true` (preserving current roster auto-creation logic)
   - Upserts `LastChatsStorageItem` via `SyncConversationMapper`
   - Upserts `MessageStorageItem` + `MessageReferenceStorageItem` (group nickname) if `message != null`
   - If `markersChanged = true`: scans all `MessageStorageItem` for `(owner, jid, type)` and updates state/isRead
   - If `markersChanged = false`: skips the scan (existing optimisation preserved)
4. For each `ConversationWrite.Delete`: removes `LastChatsStorageItem`
5. Closes the write block

For 60 conversations per page: one transaction replaces up to 60.

### SyncRepositoryImpl Lifecycle

`SyncRepositoryImpl` holds the `Realm` instance injected at construction. It is constructed once per account inside `ClientSynchronizationManager`. When `Account.closeStream()` / `reset()` is called, `ClientSynchronizationManager.reset()` calls `repo.close()` which calls `realm.close()`. A `close()` method is added to `SyncRepository` interface.

### Mappers

- `SyncConversationMapper` — maps `SyncConversation` + `SyncMarkers` → `LastChatsStorageItem` fields
- `SyncMessageMapper` — maps `SyncMessage` + `MessageStateResult` → `MessageStorageItem` + `MessageReferenceStorageItem` for group nicknames

---

## Protocol Layer

### SyncProtocolParser
Stateless. Three parse methods for three input shapes:
- `parseSnapshot(rawIq: String): SyncPage` — IQ result response
- `parsePush(rawIq: String): SyncPage` — IQ set push
- `parseMessageStanza(rawMessage: String): SyncPage?` — message stanza with `<last-message>` child (replaces `receiveClientSyncRaw`)

All three output a `SyncPage`. Handles: conversation attributes, mute timestamp conversion, marker extraction, last-message parsing (group nickname prefix stripping), timestamp parsing (`yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`).

### SyncProtocolSender
Stateless. Two responsibilities:
1. **Sync requests:** `sendSyncRequest(stream, owner, boundJid, version, after)` — builds and sends paginated sync IQ with retry
2. **Mutation IQs:** `sendMuteConversation()`, `sendUnmuteConversation()`, `sendPinChat()`, `sendUpdate()` — moved from current `ClientSynchronizationManager` public methods

### SyncIqModule
Existing module, interface unchanged. Becomes a thin router: parses IQ type, calls `parser.parseSnapshot()` or `parser.parsePush()`, emits the resulting `SyncPage` to `Account` via callback.

### ClientSynchronizationManager (~60 lines)

Holds `RunSyncUseCase` and `ProcessPushUpdateUseCase`. Also exposes the four mutation methods (`muteConversation`, `unmuteConversation`, `pinChat`, `update`) which delegate to `SyncProtocolSender`. Manages `SyncRepositoryImpl` lifecycle (construct on init, close on reset).

```kotlin
class ClientSynchronizationManager(private val owner: String) {
    private val repo = SyncRepositoryImpl(Realm.open(defaultRealmConfig()))
    private val sender = SyncProtocolSender()
    private val runSync = RunSyncUseCase(sender, ProcessSyncPageUseCase(...), FillGapsUseCase(...), repo)
    private val processPush = ProcessPushUpdateUseCase(ProcessSyncPageUseCase(...), FillGapsUseCase(...), repo)

    suspend fun sync(stream: Stream) = runSync.start(stream, owner)

    suspend fun read(page: SyncPage, stream: Stream) {
        if (page.isPush) processPush.execute(page, owner)
        else runSync.onPageReceived(page, stream, owner)
    }

    // Mutation methods — delegate to sender
    suspend fun muteConversation(stream: Stream, jid: String, muteUntil: Long) =
        sender.sendMuteConversation(stream, owner, jid, muteUntil)
    suspend fun unmuteConversation(stream: Stream, jid: String) =
        sender.sendUnmuteConversation(stream, owner, jid)
    suspend fun pinChat(stream: Stream, jid: String, pinned: Long) =
        sender.sendPinChat(stream, owner, jid, pinned)
    suspend fun update(stream: Stream) =
        sender.sendUpdate(stream, owner)

    fun reset() { repo.close() }
}
```

**DI note:** These objects are per-account, not app-level singletons. Construction remains manual inside `ClientSynchronizationManager` (matching existing pattern in `Account.kt`), not via Koin. Koin is not used for per-account sync objects.

---

## Data Flow

### Paginated Sync
```
Account.streamSyncRequest()
  → ClientSynchronizationManager.sync(stream)
  → RunSyncUseCase.start()
  → SyncProtocolSender.sendSyncRequest(after=null)

Server → SyncIqModule.handle() [type=result]
  → SyncProtocolParser.parseSnapshot() → SyncPage
  → Account.processSyncStanza() → ClientSynchronizationManager.read(page)
  → RunSyncUseCase.onPageReceived()
      if isFullPage → SyncProtocolSender.sendSyncRequest(after=lastStamp)  // pipeline
      → ProcessSyncPageUseCase.execute()
          pre-fetch existingRosterJids
          for each conversation:
            → repo.getConversation() → StoredConversation (carries lastMessageState)
            → DetermineMessageStateUseCase (uses msg.currentState, pure)
            → MergeSyncMarkersUseCase (pure)
          → repo.applyBatch()  // single Realm write
      → repo.saveVersion()
      if last page → FillGapsUseCase.execute()
```

### IQ-Set Server Push
```
Server → SyncIqModule.handle() [type=set]
  → ack immediately
  → SyncProtocolParser.parsePush() → SyncPage (isPush=true)
  → Account → ClientSynchronizationManager.read(page)
  → ProcessPushUpdateUseCase.execute()
      → ProcessSyncPageUseCase.execute()   // same path as sync
      → repo.saveVersion()
      → FillGapsUseCase.execute() if gaps
```

### Message-Stanza Sync
```
Server → MessageManager+CommonReceiver → ClientSynchronizationManager.receiveClientSyncRaw(raw)
  → SyncProtocolParser.parseMessageStanza(raw) → SyncPage? (isPush=true)
  → if non-null: ClientSynchronizationManager.read(page, stream=null)
  → ProcessPushUpdateUseCase.execute()
      → ProcessSyncPageUseCase.execute()   // same path as above
      → repo.saveVersion()
      → FillGapsUseCase.execute() if gaps
```

---

## Key Metrics: Before vs After

| Metric | Before | After |
|---|---|---|
| Page size | 50 | **60** |
| Realm transactions per page | N (one per conversation) | **1 (batch)** |
| `ClientSynchronizationManager` size | ~1000 lines | **~60 lines** |
| Largest single function | `readConversationMetadata()` ~400 lines | **~50 lines** (`ProcessSyncPageUseCase.execute()`) |
| Incoming paths code sharing | None | **All 3 share `ProcessSyncPageUseCase`** |
| Domain use case testability | Requires Realm + Android | **Pure Kotlin, no dependencies** |
| Pipelining | Preserved | **Preserved** |
| Marker scan optimisation | Preserved | **Preserved via `markersChanged` flag** |
| Roster pre-fetch optimisation | Preserved | **Preserved via `getRosterJids()` + `createRosterIfMissing` flag** |

---

## Implementation Sequence

1. Create domain models (`domain/sync/model/`) — `SyncPage`, `SyncConversation`, `SyncMarkers`, `SyncMessage` (+ `currentState`), `StoredConversation`, `ConversationWrite` (+ `markersChanged`, `createRosterIfMissing`), `GapFillRequest`, `MessageStateResult`
2. Create `SyncRepository` interface (+ `close()`, `getRosterJids()`, `markGapFixed()`)
3. Implement pure use cases: `DetermineMessageStateUseCase`, `MergeSyncMarkersUseCase`
4. Implement `ProcessSyncPageUseCase`
5. Implement `RunSyncUseCase`, `ProcessPushUpdateUseCase`, `FillGapsUseCase`
6. Create `SyncRepositoryImpl` + mappers (`SyncConversationMapper`, `SyncMessageMapper`)
7. Create `SyncProtocolParser` (3 parse methods) and `SyncProtocolSender` (sync request + 4 mutation methods)
8. Rewrite `ClientSynchronizationManager` (~60 lines)
9. Update `SyncIqModule` to call `parser` and emit `SyncPage` via callback
10. Update `MessageManager+CommonReceiver` call site to use `parseMessageStanza()` path
11. Delete `ClientSynchronizationMergeRules.kt` (logic moved to `MergeSyncMarkersUseCase`)
