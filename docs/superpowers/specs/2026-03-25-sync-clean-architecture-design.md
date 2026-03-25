# Sync Clean Architecture — Design Spec

**Date:** 2026-03-25
**Branch:** db
**Status:** Approved

---

## Overview

Refactor the XEP-0CCC client synchronization system from a single ~1000-line god class (`ClientSynchronizationManager`) into a clean three-layer architecture: protocol, domain, and data. Both the paginated sync flow and the server push flow are unified through shared domain use cases. Page size is set to 60.

---

## Goals

- Clean separation: protocol (XML/network), domain (business logic), data (Realm)
- Single Realm transaction per sync page (batch writes)
- Page size = 60
- Sync and push share the same processing path (`ProcessSyncPageUseCase`)
- Domain use cases are pure Kotlin — no Realm, no Android, fully unit-testable
- Pipelining preserved: next page request sent before current page is written

---

## Non-Goals

- Changes to the MAM gap-fill implementation internals
- Changes to any other XEP manager
- UI layer changes
- Message receiving path (only sync and push are in scope)

---

## Architecture

### Layer Boundaries

```
┌─────────────────────────────────────────────┐
│  PROTOCOL LAYER  (xmpp/XEP_0CCC/)           │
│  • Parses raw XML → domain models           │
│  • Sends IQ stanzas                         │
│  • Routes incoming IQs to domain            │
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
│   ├── ClientSynchronizationManager.kt     # ~30 lines, thin coordinator
│   ├── SyncIqModule.kt                     # thin router (existing, unchanged interface)
│   ├── SyncProtocolParser.kt               # NEW: raw XML → SyncPage domain model
│   └── SyncProtocolSender.kt               # NEW: builds and sends IQ stanzas
│
├── domain/sync/                            # NEW: Domain layer
│   ├── model/
│   │   ├── SyncPage.kt                     # + SYNC_PAGE_SIZE = 60
│   │   ├── SyncConversation.kt
│   │   ├── SyncMarkers.kt
│   │   ├── SyncMessage.kt
│   │   ├── StoredConversation.kt
│   │   ├── ConversationWrite.kt            # sealed write instructions
│   │   └── GapFillRequest.kt
│   ├── usecase/
│   │   ├── RunSyncUseCase.kt               # orchestration + pipelining
│   │   ├── ProcessSyncPageUseCase.kt       # shared by sync + push
│   │   ├── ProcessPushUpdateUseCase.kt     # thin wrapper over ProcessSyncPageUseCase
│   │   ├── FillGapsUseCase.kt              # MAM gap-fill dispatch
│   │   ├── DetermineMessageStateUseCase.kt # pure: message state from markers
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
    val isFullPage get() = !isPush && conversations.size >= SYNC_PAGE_SIZE
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
)

// StoredConversation.kt — returned by repository, no Realm types
data class StoredConversation(
    val jid: String,
    val type: String,
    val markers: SyncMarkers,
    val lastMessageDateMs: Long,
    val isGapFixedForSession: Boolean,
)

// ConversationWrite.kt — sealed instructions built in domain, executed in data layer
sealed class ConversationWrite {
    data class Upsert(
        val conv: SyncConversation,
        val mergedMarkers: SyncMarkers,
        val message: MessageUpdate?,
    ) : ConversationWrite()
    data class MessageUpdate(val msg: SyncMessage, val state: MessageStateResult)
    data class Delete(val jid: String, val type: String) : ConversationWrite()
}
```

---

## Domain Use Cases

### RunSyncUseCase
Orchestrates the full paginated sync lifecycle. Maintains pending gap requests across pages. Sends next page request (pipelining) before processing the current page.

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
Shared by both sync and push. Iterates conversations, computes merged markers and message states, accumulates ConversationWrite instructions, calls `repo.applyBatch()` in a single call.

```kotlin
class ProcessSyncPageUseCase(
    private val determineState: DetermineMessageStateUseCase,
    private val mergeMarkers: MergeSyncMarkersUseCase,
    private val repo: SyncRepository,
) {
    suspend fun execute(page: SyncPage, owner: String): List<GapFillRequest> {
        val gaps = mutableListOf<GapFillRequest>()
        val writes = mutableListOf<ConversationWrite>()

        for (conv in page.conversations) {
            if (conv.status == SyncStatus.DELETED) {
                writes += ConversationWrite.Delete(conv.jid, conv.type)
                continue
            }
            val existing = repo.getConversation(owner, conv.jid, conv.type)
            val message = conv.lastMessage?.let { msg ->
                val state = determineState.execute(msg, conv.markers)
                ConversationWrite.MessageUpdate(msg, state)
            }
            val merged = mergeMarkers.execute(existing?.markers, conv.markers, conv.lastMessage?.timestampUs)
            detectGap(existing, conv)?.let { gaps += it }
            writes += ConversationWrite.Upsert(conv, merged, message)
        }

        repo.applyBatch(owner, writes)
        return gaps
    }
}
```

### ProcessPushUpdateUseCase
Thin wrapper: reuses `ProcessSyncPageUseCase`, dispatches gaps immediately (no accumulation across pages since push is always a single conversation).

### DetermineMessageStateUseCase
Pure function. Given a `SyncMessage` and `SyncMarkers`, returns `MessageStateResult(state: MessageSendingState, isRead: Boolean)`. Extracted directly from current inline logic.

### MergeSyncMarkersUseCase
Pure function. Merges current stored markers with incoming server markers using max-wins logic for IDs and timestamp-based logic for `lastReadMessageDate`. Extracted from `ClientSynchronizationMergeRules.kt`.

### FillGapsUseCase
Receives a list of `GapFillRequest` and delegates to `MessageArchiveManager` (MAM). Marks `isHistoryGapFixedForSession = true` on completion. No changes to MAM internals.

---

## Repository Interface

```kotlin
interface SyncRepository {
    suspend fun getVersion(owner: String): String
    suspend fun saveVersion(owner: String, version: String)
    suspend fun resetGapFlags(owner: String)
    suspend fun getConversation(owner: String, jid: String, type: String): StoredConversation?
    suspend fun applyBatch(owner: String, writes: List<ConversationWrite>)
}
```

---

## Data Layer

### SyncRepositoryImpl

Implements `SyncRepository`. Only class that opens or writes to Realm.

`applyBatch()` executes a **single `realm.write { }` block** for all writes in a page — replacing the current pattern of one transaction per conversation. For 60 conversations per page this eliminates 59 extra transaction overheads.

Per-conversation message state updates (scanning all messages in a chat when markers change) remain inside the write block, preserving the existing optimization that skips the scan when markers are unchanged.

### Mappers

- `SyncConversationMapper` — maps `SyncConversation` + `SyncMarkers` → `LastChatsStorageItem` fields
- `SyncMessageMapper` — maps `SyncMessage` + `MessageStateResult` → `MessageStorageItem` fields; handles `MessageReferenceStorageItem` creation for group chat nicknames

---

## Protocol Layer

### SyncProtocolParser
Stateless. Parses raw IQ XML string into `SyncPage`. Handles: conversation attributes, mute timestamp conversion, marker extraction, last-message parsing (including group nickname prefix stripping), timestamp parsing (`yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`).

### SyncProtocolSender
Stateless. Builds sync IQ XML string and writes to `stream.socket`. Retries up to 3 times with 1s delay on write failure.

### SyncIqModule
Existing module, interface unchanged. Becomes a thin router: parses incoming IQ type, calls `parser.parseSnapshot()` or `parser.parsePush()`, emits the resulting `SyncPage` to `Account` via callback.

### ClientSynchronizationManager (~30 lines)
```kotlin
class ClientSynchronizationManager(owner: String) {
    private val runSync: RunSyncUseCase = ...
    private val processPush: ProcessPushUpdateUseCase = ...

    suspend fun sync(stream: Stream) = runSync.start(stream, owner)

    suspend fun read(page: SyncPage, stream: Stream) {
        if (page.isPush) processPush.execute(page, owner)
        else runSync.onPageReceived(page, stream, owner)
    }
}
```

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
          for each conversation:
            → DetermineMessageStateUseCase
            → MergeSyncMarkersUseCase
          → SyncRepository.applyBatch()  // single Realm write
      → SyncRepository.saveVersion()
      if last page → FillGapsUseCase.execute()
```

### Server Push
```
Server → SyncIqModule.handle() [type=set]
  → ack immediately
  → SyncProtocolParser.parsePush() → SyncPage
  → Account → ClientSynchronizationManager.read(page)
  → ProcessPushUpdateUseCase.execute()
      → ProcessSyncPageUseCase.execute()   // same path as sync
      → SyncRepository.saveVersion()
      → FillGapsUseCase.execute() if gaps
```

---

## Key Metrics: Before vs After

| Metric | Before | After |
|---|---|---|
| Page size | 50 | **60** |
| Realm transactions per page | N (one per conversation) | **1 (batch)** |
| `ClientSynchronizationManager` size | ~1000 lines | **~30 lines** |
| Largest single function | `readConversationMetadata()` ~400 lines | **~40 lines** (`ProcessSyncPageUseCase.execute()`) |
| Sync and push code sharing | None | **Shared via `ProcessSyncPageUseCase`** |
| Domain use case testability | Requires Realm + Android | **Pure Kotlin, no dependencies** |
| Pipelining | Preserved | **Preserved** |
| Marker optimization (skip scan) | Preserved | **Preserved** |

---

## Implementation Sequence

1. Create domain models (`domain/sync/model/`)
2. Create `SyncRepository` interface
3. Implement pure use cases: `DetermineMessageStateUseCase`, `MergeSyncMarkersUseCase`
4. Implement `ProcessSyncPageUseCase`
5. Implement `RunSyncUseCase`, `ProcessPushUpdateUseCase`, `FillGapsUseCase`
6. Create `SyncRepositoryImpl` + mappers
7. Create `SyncProtocolParser`, `SyncProtocolSender`
8. Slim down `ClientSynchronizationManager`
9. Update `SyncIqModule` to emit `SyncPage`
10. Wire via Koin DI
11. Delete `ClientSynchronizationMergeRules.kt` (logic moved to use cases)
