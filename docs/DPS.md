# NOVA Cognitive Runtime (NCR)

# Document 04 — Data Platform Specification (DPS) v1.0

**Status:** Engineering Ready

**Owner:** Runtime Team

**Dependencies:**

* PRD v2.0
* TDD v1.0
* Interface & API Specification v1.0

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

768 dimensions.

Supported Content

* PDF
* DOCX
* TXT
* Images
* OCR
* Audio transcripts
* Notes

Search Types

* k-NN
* Hybrid search
* Metadata filtering

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

```text
Filesystem Change

↓

File Scanner

↓

Metadata Extractor

↓

Content Extractor

↓

OCR (if image)

↓

Embedding Generator

↓

SQLite

↓

Vector Index

↓

Knowledge Graph

↓

Cache Refresh
```

Indexing is incremental.

---

# 10. Background Workers

Workers

* File Indexer
* OCR Worker
* Embedding Worker
* Graph Builder
* Cleanup Worker
* Migration Worker

Scheduling

Only when

* Charging (preferred for heavy tasks)
* Device idle (preferred)
* Thermal state acceptable

Foreground requests always take priority.

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
* Vector Index
* File metadata
* Photo metadata
* OCR
* Incremental indexing
* Local cache

Deferred

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
