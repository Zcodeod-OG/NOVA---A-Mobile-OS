# NOVA Cognitive Runtime (NCR)

# Document 04 — Data Platform Specification (DPS) v1.0

**Status:** Engineering Ready

**Owner:** Runtime Team

**Dependencies:**

* PRD v2.0 ([§14 Implementation Status](./PRD.md#14-implementation-status))
* TDD v1.0 ([§22 Model & Tokenizer Pipeline](./TDD.md#22-model--tokenizer-pipeline))
* Interface & API Specification v1.0
* AIS v1.0 ([§4.9 Model Asset Delivery](./AIS.md#49-model-asset-delivery))

---

# 1. Purpose

The Data Platform is responsible for storing, indexing, retrieving, securing, and maintaining all runtime data used by NOVA.

It provides a unified abstraction over multiple storage technologies while exposing a single interface to the runtime.

The Data Platform guarantees:

* Offline-first operation
* Local-only storage
* Fast semantic retrieval
* Deterministic persistence
* Secure encryption
* Incremental indexing
* Extensible storage backends

The runtime must never directly access storage engines.

---

# 2. Design Principles

1. Every piece of data has **one source of truth**.
2. Storage engines are hidden behind a Storage Coordinator.
3. Runtime modules never know where data is stored.
4. Reads are optimized for latency.
5. Writes prioritize consistency.
6. Background work never blocks foreground interaction.
7. All user data remains local.

---

# 3. High-Level Architecture

```text
                    Runtime

                       │

               Storage Coordinator

      ┌────────┬────────┬────────┬─────────┐

      │        │        │        │

 SQLite    Vector    Graph    Cache

 Database   Index    Store    Layer

      │

 File Indexing Pipeline

      │

 Android Storage
```

---

# 4. Core Components

## 4.1 Storage Coordinator

The Storage Coordinator is the only component allowed to communicate with storage engines.

Responsibilities:

* Route read requests
* Route write requests
* Coordinate transactions
* Manage migrations
* Handle cache
* Maintain consistency
* Abstract storage implementation

Public API

```kotlin
store()

retrieve()

query()

delete()

update()

transaction()
```

---

## 4.2 SQLite Database

Purpose

Store structured runtime metadata.

Contains

* Documents
* Photos
* Contacts
* Calendar metadata
* Memory metadata
* Runtime settings
* Sessions
* Capability registry
* Execution history

SQLite is the primary relational database.

Room ORM will be used.

---

## 4.3 Vector Index

Purpose

Semantic similarity search.

Stores

* Document embeddings
* Image embeddings
* OCR embeddings
* Audio embeddings
* Conversation embeddings

Never stores raw files.

Recommended implementation

HNSW index.

---

## 4.4 Knowledge Graph

Purpose

Represent relationships.

Node Types

* Person
* Project
* Document
* Event
* Location
* Device
* Conversation
* File

Relationship Types

* BELONGS_TO
* RELATED_TO
* CREATED_BY
* SHARED_WITH
* OPENED_AFTER
* DEPENDS_ON
* REFERENCES

---

## 4.5 Cache Layer

Purpose

Reduce repeated computation.

Caches

* Recent search results
* Recent embeddings
* Active Reasoning Contexts
* Current sessions
* Capability lookups

Eviction Policy

LRU + importance score.

---

# 5. Storage Ownership

| Data             | Owner           |
| ---------------- | --------------- |
| File metadata    | SQLite          |
| Embeddings       | Vector Index    |
| Relationships    | Knowledge Graph |
| Active session   | Cache           |
| User preferences | SQLite          |
| Working memory   | Memory Platform |
| Raw files        | Android Storage |

No duplication of ownership.

---

# 6. SQLite Schema

## Documents

```text
Document
---------
id
path
name
mime_type
size
hash
created_at
updated_at
indexed_at
embedding_id
project_id
importance
```

---

## Photos

```text
Photo
---------
id
uri
timestamp
location
ocr_text
embedding_id
favorite
```

---

## Contacts

```text
Contact
---------
id
display_name
phone
email
last_contacted
importance
```

---

## Sessions

```text
Session
---------
id
trace_id
started_at
ended_at
status
```

---

## Preferences

```text
Preference
---------
key
value
confidence
updated_at
```

---

## Execution History

```text
Execution
---------
id
graph_id
status
started_at
finished_at
duration
```

---

# 7. Vector Index

Embedding Dimensions

Configurable.

Default:

384 dimensions (`all-MiniLM-L6-v2` ONNX export).

Supported Content

* PDF
* DOCX
* TXT
* Images (multimodal embeddings — **in progress**)
* OCR text (embedded via text pipeline)
* Audio transcripts
* Notes

Search Types

* k-NN (cosine similarity)
* Hybrid search (vector + metadata filter)
* Metadata filtering

## 7.1 Scale & Index Structure

The vector index must support full-device libraries (100k+ items per [PRD G2](./PRD.md#14-implementation-status)).

| Scale Tier | Item Count | Index Strategy | Status |
| ---------- | ---------- | -------------- | ------ |
| MVP | ≤ 10k | Brute-force cosine KNN (`CosineVectorIndex`) | **Shipped** |
| Production | 10k – 500k | HNSW approximate nearest neighbor | **Planned** |
| Large library | 500k+ | Sharded HNSW + importance-weighted eviction | **Deferred** |

Performance targets (§14) assume HNSW at production scale. Until HNSW lands, indexing continues in the background and search quality is bounded by brute-force latency on large libraries.

Embeddings are never recomputed on read; the index stores only vectors and metadata references (see §4.3).

---

# 8. Knowledge Graph

Example

```text
Bookly

├── Investor Deck

├── Rahul

├── Meeting Notes

├── YC Application

└── Landing Page
```

Graph updates occur through background workers.

---

# 9. File Indexing Pipeline

The indexing pipeline is the core of [PRD G2 — Universal Local Search](./PRD.md#14-implementation-status). It must cover the user's entire accessible library, not a developer-curated subset.

```text
Filesystem / MediaStore Change
        │
        ↓
Incremental Sync Coordinator
        │
        ├─ New / modified URI  → enqueue index job
        ├─ Deleted URI         → tombstone + vector purge
        └─ Re-index request  → invalidate embedding_id, re-enqueue
        │
        ↓
WorkManager: File Indexer Worker
        │
        ↓
Metadata Extractor (MediaStore, SAF, Downloads)
        │
        ↓
Content Extractor (text, PDF, DOCX)
        │
        ↓
OCR Worker (ML Kit — images without text layer)
        │
        ↓
Embedding Generator (MiniLM text / multimodal — see TDD §22)
        │
        ↓
SQLite (metadata + embedding_id)
        │
        ↓
Vector Index (upsert)
        │
        ↓
Knowledge Graph (relationship edges)
        │
        ↓
Cache Refresh
```

## 9.1 Full-Device Coverage

The File Indexer scans all content types listed in [PRD §3 G2](./PRD.md#3-product-goals):

| Source | Android API | Indexed Fields |
| ------ | ----------- | -------------- |
| Photos / Videos | MediaStore (`READ_MEDIA_*`) | URI, timestamp, location, OCR text, embedding |
| Audio | MediaStore | URI, transcript (Whisper — planned), embedding |
| Documents / PDFs | SAF + Downloads | path/URI, mime, text extract, embedding |
| Contacts | Contacts Provider | display name, phone, email |
| Calendar | Calendar Provider | title, time, location, attendees |

Indexing respects scoped storage: no raw filesystem assumptions (see [AIS §4.8](./AIS.md#48-storage-access-adapter)).

## 9.2 Incremental Sync

Indexing is **incremental and resumable**, not a one-shot bulk import.

Sync triggers:

* `MediaStore` content-observer callbacks
* SAF document tree change notifications
* App launch (delta since `last_indexed_at`)
* `BOOT_COMPLETED` worker re-registration ([AIS §9](./AIS.md#9-lifecycle-integration))
* Manual "Re-index" user action

Each indexed item stores `indexed_at`, content `hash`, and `embedding_id`. A change in hash invalidates the prior embedding and enqueues a re-index job. Deletes tombstone the SQLite row and remove the vector entry.

Workers checkpoint progress so initial indexing of large libraries survives app kills and device reboots.

## 9.3 Indexing Constraints

* Foreground conversation and execution always preempt indexing ([AIS §8](./AIS.md#8-background-execution-strategy)).
* Heavy embedding batches run only when charging or idle (§10).
* No file content is uploaded or copied off-device; only extracted text and embeddings are stored locally.

---

# 10. Background Workers

All indexing and embedding work is scheduled through **WorkManager** (see [AIS §5](./AIS.md#5-android-services)). The runtime never performs heavy indexing on the main thread.

## 10.1 Worker Inventory

| Worker | Responsibility | Constraints | Status |
| ------ | -------------- | ----------- | ------ |
| File Indexer | Full-device scan + incremental sync | `NetworkType.NOT_REQUIRED`; expedited only on user "Re-index" | **In progress** |
| OCR Worker | ML Kit text extraction for images | Chained after File Indexer for image MIME types | **Shipped** |
| Embedding Worker | MiniLM text + multimodal vector generation | Requires downloaded ONNX model ([AIS §4.9](./AIS.md#49-model-asset-delivery)) | **In progress** |
| Graph Builder | Knowledge graph edge creation | Runs after embedding upsert | **Planned** |
| Cleanup Worker | Purge tombstones, evict stale cache | Periodic; low priority | **Planned** |
| Migration Worker | Schema forward migrations | Runs at app upgrade | **Shipped** |

## 10.2 Scheduling Policy

Workers run only when:

* Charging (preferred for initial full-device index)
* Device idle (preferred for embedding batches)
* Thermal state acceptable
* Battery above low threshold

Foreground requests always take priority. WorkManager uses exponential backoff on failure and persists work across process death.

## 10.3 Initial Index vs. Steady State

| Phase | Behavior |
| ----- | -------- |
| First run (post-permissions) | Enqueue full-device File Indexer; show progress notification |
| Steady state | Incremental sync on content-observer events only |
| Model download complete | Backfill embeddings for items indexed with placeholder metadata |

---

# 11. Synchronization

Write Order

1. SQLite
2. Vector Index
3. Knowledge Graph
4. Cache

If any step fails,

rollback metadata and retry later.

---

# 12. Encryption

Sensitive runtime data is encrypted at rest.

Encryption Scope

* Preferences
* Execution history
* Session metadata
* Cached credentials (if ever added)

Raw user files remain managed by Android storage permissions and are referenced rather than copied whenever possible.

---

# 13. Versioning

Every storage engine has its own schema version.

Migration Strategy

* Forward migrations
* Automatic validation
* Backup before destructive changes

---

# 14. Performance Targets

| Operation       | Target     |
| --------------- | ---------- |
| Metadata lookup | <10 ms     |
| Vector search   | <150 ms    |
| Graph traversal | <100 ms    |
| Cache lookup    | <5 ms      |
| File indexing   | Background |
| Session restore | <50 ms     |

---

# 15. MVP Scope

Included

* SQLite
* Room
* Vector Index (brute-force KNN at MVP scale; HNSW planned — §7.1)
* Full-device WorkManager indexing pipeline (**in progress**)
* Incremental sync with content observers (**in progress**)
* File metadata
* Photo metadata
* OCR
* Local cache

Deferred

* HNSW at production scale (§7.1)
* Full graph inference
* Distributed storage
* Cloud sync
* Multi-device replication
* Advanced memory consolidation

---

# 16. Engineering Risks

* Large media libraries (>100k items)
* Battery impact during initial indexing
* Embedding generation latency
* Storage growth over time
* Schema migrations

Mitigations

* Incremental indexing
* Worker scheduling
* Cache limits
* Background processing
* Versioned migrations

---

# 17. Acceptance Criteria

The Data Platform is considered complete when:

* All storage passes through the Storage Coordinator.
* File search is functional.
* Semantic vector search is operational.
* Indexing survives app restarts.
* No user data leaves the device.
* All schema migrations are automated.
* Retrieval latency meets performance budgets.
* Storage ownership remains unambiguous.
